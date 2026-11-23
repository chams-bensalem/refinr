// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.eval;

import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.adapters.out.llm.LlmBacklogGenerator;
import fr.heliocraft.refinr.adapters.out.llm.LlmReadinessAssessor;
import fr.heliocraft.refinr.adapters.out.llm.LlmRequest;
import fr.heliocraft.refinr.adapters.out.llm.LlmSettings;
import fr.heliocraft.refinr.adapters.out.llm.MeteredLlmClients;
import fr.heliocraft.refinr.adapters.out.llm.StructuredOutput;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.service.PreviewBacklogService;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.config.EvalLlmSettings;
import fr.heliocraft.refinr.config.LlmProperties;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.eval.VerdictMetrics.Confidence;
import fr.heliocraft.refinr.eval.VerdictMetrics.Label;
import fr.heliocraft.refinr.eval.VerdictMetrics.Observation;
import fr.heliocraft.refinr.eval.VerdictMetrics.Reference;
import org.springframework.boot.Banner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Mesure de la justesse du verdict {@code ai-ready} contre {@code eval/verdict} (E0). Lance par
 * {@code scripts/eval-verdict.sh}, jamais par {@code mvn test} ni par la CI : {@code run} appelle le modele (quota,
 * argent). Appelle seulement le vrai {@link LlmReadinessAssessor} (prompt, schema, lots et reparations de production),
 * aucun appel d'analyse, aucun tracker.
 * <p>
 * Fournisseur et modele : lus dans {@code .env}, jamais ecrits ici. Cles : resolues par Spring ({@code ${...}}), jamais
 * lues ni affichees (seule leur presence est indiquee).
 */
public final class VerdictEval {

    @Configuration
    @EnableConfigurationProperties(LlmProperties.class)
    static class LlmOnly {}

    static final long BASE_SEED = 20261116L;
    static final ZoneId ZONE = ZoneId.of("Europe/Paris");
    static final Pattern STATUS = Pattern.compile("HTTP (\\d{3})");
    static final Set<Integer> STOP_STATUSES = Set.of(400, 401, 403, 404, 429);
    static final Set<Integer> OVERLOAD_STATUSES = Set.of(503, 529);
    static final int MAX_CONSECUTIVE_OVERLOAD = 2;

    /**
     * Plafond d'appels par jour calendaire (Europe/Paris) et par fournisseur, sauf {@code --cap} : 100 pour Anthropic
     * (cout borne par le budget de la phase et le refus avant chaque requete), 40 pour les autres (quota Gemini observe).
     */
    static int defaultCap(String providerId) {
        return providerId.equals("anthropic") ? 100 : 40;
    }
    /** Tokens d'entree : 1 token pour 2,2 caracteres (observe en E0 sur Haiku 5.5 et Opus 5.5, francais + JSON). */
    static final double CHARS_PER_TOKEN = 2.2;
    /** Sortie typique par story (identifiant, booleen, raison d'une phrase) : ~166 tokens observes en E0. */
    static final int OUTPUT_TOKENS_PER_STORY = 166;
    /** Valeur factice de cle pour {@code estimate --assume-model} : jamais envoyee, aucun appel dans estimate. */
    static final String ESTIMATE_ONLY_KEY = "estimate-only-no-call";

    /** Cibles : verdict de production lu dans .env, ou role verdict remplace par la cle et le modele d'evaluation Anthropic. */
    enum Target {
        ENV("env", null),
        ANTHROPIC("anthropic", "EVAL_ANTHROPIC_MODEL"),
        ANTHROPIC_CHECK("anthropic-check", "EVAL_ANTHROPIC_CHECK_MODEL");

        final String id;
        final String modelVariable;

        Target(String id, String modelVariable) {
            this.id = id;
            this.modelVariable = modelVariable;
        }

        static Target of(String id) {
            for (Target t : values()) if (t.id.equals(id)) return t;
            throw new IllegalArgumentException("unknown target '" + id + "' (env, anthropic, anthropic-check)");
        }

        /**
         * Generation (commande generate) : role analyse remplace par Anthropic, et aucun verdict distinct (le verdict vient
         * de la generation, comme une instance a un seul fournisseur).
         */
        List<String> analysisSpringArgs() {
            List<String> args = new ArrayList<>(List.of("--refinr.llm.analysis.max-retries=0", "--refinr.llm.verdict.max-retries=0"));
            if (modelVariable != null) {
                args.addAll(List.of("--refinr.llm.analysis.provider=anthropic",
                        "--refinr.llm.analysis.model=${" + modelVariable + ":}",
                        "--refinr.llm.analysis.api-key=${EVAL_ANTHROPIC_API_KEY:}",
                        "--refinr.llm.analysis.base-url=", "--refinr.llm.analysis.temperature=",
                        "--refinr.llm.analysis.extra-body.reasoning_effort=",
                        "--refinr.llm.verdict.provider=", "--refinr.llm.verdict.model="));
            }
            return args;
        }

        List<String> springArgs(String assumedModel) {
            List<String> args = new ArrayList<>(List.of("--refinr.llm.analysis.max-retries=0", "--refinr.llm.verdict.max-retries=0"));
            if (modelVariable != null) {
                // Role verdict entierement remplace : rien du verdict de .env (URL, temperature, effort) ne s'y melange
                args.addAll(List.of("--refinr.llm.verdict.provider=anthropic",
                        "--refinr.llm.verdict.model=" + (assumedModel != null ? assumedModel : "${" + modelVariable + ":}"),
                        // Estimation seule : valeur factice, jamais envoyee (aucun appel dans estimate)
                        "--refinr.llm.verdict.api-key=" + (assumedModel != null ? ESTIMATE_ONLY_KEY : "${EVAL_ANTHROPIC_API_KEY:}"),
                        "--refinr.llm.verdict.base-url=", "--refinr.llm.verdict.temperature=",
                        "--refinr.llm.verdict.extra-body.reasoning_effort="));
            }
            return args;
        }
    }

    /** {@code languages} : politique de langue de production ({@code refinr.llm.output-language}, auto par defaut). */
    record Setup(Target target, LlmSettings settings, int repairs, int batchSize, OutputLanguagePolicy languages) {
        List<String> optionalParams() {
            List<String> p = new ArrayList<>();
            if (settings.temperature() != null) p.add("temperature");
            p.addAll(settings.extraBody().paths());
            return p;
        }
    }

    /** Tranche de prix : s'applique aux requetes d'au plus {@code maxInputTokens} tokens d'entree (null = sans limite). */
    record Tier(Long maxInputTokens, double inputPerMTok, double outputPerMTok) {}

    /**
     * Prix d'un modele, par requete : la tranche depend des tokens d'entree de la requete (ex. Haiku 5.5 : 0,10 / 0,50 USD
     * jusqu'a 100 000 tokens de prompt, 0,50 / 2,50 au-dela). Toujours appliquee a une requete, jamais a un total.
     */
    record Price(List<Tier> tiers, String checkedOn, String source) {
        Tier tier(long input) {
            for (Tier t : tiers) {
                if (t.maxInputTokens() == null || input <= t.maxInputTokens()) return t;
            }
            throw new IllegalStateException("no price tier for a request of " + input + " input tokens in eval/verdict/prices.csv");
        }

        double cost(long input, long output) {
            Tier t = tier(input);
            return (input * t.inputPerMTok() + output * t.outputPerMTok()) / 1_000_000d;
        }

        String describe() {
            List<String> parts = new ArrayList<>();
            for (Tier t : tiers) {
                parts.add(t.inputPerMTok() + "/" + t.outputPerMTok()
                        + (t.maxInputTokens() == null ? "" : " up to " + t.maxInputTokens() + " input tokens"));
            }
            return String.join(", then ", parts) + " USD per MTok (checked " + checkedOn + ")";
        }
    }

    private final Path root;
    private final Path dataDir;
    private final Path stateDir;

    VerdictEval(Path root) {
        this.root = root;
        this.dataDir = root.resolve("eval/verdict");
        this.stateDir = dataDir.resolve(".state");
    }

    public static void main(String[] args) throws Exception {
        try {
            dispatch(args);
        } catch (IllegalStateException | IllegalArgumentException | java.io.IOException e) {
            // Messages sans secret (noms de variables seulement) ; pas de pile
            System.err.println("error: " + (e instanceof java.nio.file.NoSuchFileException ? "file not found: " : "") + e.getMessage());
            System.exit(1);
        }
    }

    static void dispatch(String[] args) throws Exception {
        Map<String, String> opts = new LinkedHashMap<>();
        List<String> positional = new ArrayList<>();
        for (int i = 0; i < args.length; i++) {
            if (args[i].startsWith("--")) {
                String key = args[i].substring(2);
                if (key.contains("=")) opts.put(key.substring(0, key.indexOf('=')), key.substring(key.indexOf('=') + 1));
                else opts.put(key, args[++i]);
            } else {
                positional.add(args[i]);
            }
        }
        if (positional.isEmpty()) {
            usage();
            return;
        }
        VerdictEval eval = new VerdictEval(Path.of(opts.getOrDefault("root", "..")).toAbsolutePath().normalize());
        String config = opts.getOrDefault("config", "src/main/resources/application-example.properties");
        if (opts.containsKey("set")) {
            if (!"en".equals(opts.get("set"))) throw new IllegalArgumentException("--set only accepts en (English control set)");
            eval.set = "en";
        }
        switch (positional.get(0)) {
            case "check" -> eval.check();
            case "describe" -> eval.describe(eval.setup(Target.of(arg(positional, 1)), config));
            // --assume-model : estimation seulement, avant que le modele d'evaluation soit dans .env (aucun appel)
            case "estimate" -> eval.estimate(eval.setup(Target.of(arg(positional, 1)), config, opts.get("assume-model")),
                    Integer.parseInt(arg(positional, 2)));
            case "run" -> {
                refuseAutomatedRun(System.getenv(), System.getProperties());
                eval.run(eval.setup(Target.of(arg(positional, 1)), config), Integer.parseInt(arg(positional, 2)),
                    opts.get("out"), Integer.parseInt(opts.getOrDefault("pause", "15")), opts.containsKey("cap") ? Integer.valueOf(opts.get("cap")) : null,
                    Double.parseDouble(opts.getOrDefault("budget", "20")), opts.getOrDefault("phase", "E0"));
            }
            case "summarize" -> eval.summarize(Path.of(arg(positional, 1)), opts.containsKey("baseline")
                    ? java.util.Arrays.stream(opts.get("baseline").split(",")).map(b -> Path.of(b.startsWith("eval/") ? "../" + b : b)).toList() : List.of());
            case "generate" -> {
                refuseAutomatedRun(System.getenv(), System.getProperties());
                eval.generate(Target.of(arg(positional, 1)), config, positional.subList(2, positional.size()), opts.get("out"),
                        opts.containsKey("cap") ? Integer.valueOf(opts.get("cap")) : null, Double.parseDouble(opts.getOrDefault("budget", "20")),
                        opts.getOrDefault("phase", "E0"), Boolean.parseBoolean(opts.getOrDefault("pipeline", "false")));
            }
            case "reassess" -> {
                refuseAutomatedRun(System.getenv(), System.getProperties());
                eval.reassess(eval.setup(Target.of(arg(positional, 1)), config), Path.of(arg(positional, 2)), opts.get("out"),
                        Double.parseDouble(opts.getOrDefault("budget", "20")), opts.getOrDefault("phase", "E0"));
            }
            case "test-cases" -> {
                refuseAutomatedRun(System.getenv(), System.getProperties());
                eval.testCases(Target.of(arg(positional, 1)), config, positional.subList(2, positional.size()), opts.get("out"),
                        Integer.parseInt(opts.getOrDefault("passes", "1")), Double.parseDouble(opts.getOrDefault("budget", "20")),
                        opts.getOrDefault("phase", "E0"));
            }
            case "probe-schema" -> {
                refuseAutomatedRun(System.getenv(), System.getProperties());
                eval.probeSchema(Target.of(arg(positional, 1)), config, opts.get("out"),
                        Double.parseDouble(opts.getOrDefault("budget", "20")), opts.getOrDefault("phase", "E0"),
                        opts.getOrDefault("schema", "backlog"));
            }
            default -> usage();
        }
    }

    /** Variables posees par les services de CI courants (GitHub Actions, GitLab, etc.). */
    static final List<String> CI_VARIABLES = List.of("CI", "GITHUB_ACTIONS", "GITLAB_CI", "JENKINS_URL", "BUILDKITE", "TF_BUILD");

    /**
     * Garde-fou : la mesure appelle le modele (quota, argent) ; elle ne tourne jamais en CI ni sous Surefire
     * ({@code mvn test}), meme si un test l'appelait par erreur.
     */
    static void refuseAutomatedRun(Map<String, String> env, java.util.Properties properties) {
        for (String v : CI_VARIABLES) {
            String value = env.get(v);
            if (value != null && !value.isBlank() && !value.equalsIgnoreCase("false")) {
                throw new IllegalStateException("refusing to call the model: CI environment detected (" + v + ")");
            }
        }
        if (properties.getProperty("surefire.test.class.path") != null || properties.getProperty("surefire.real.class.path") != null
                || properties.getProperty("basedir") != null && properties.getProperty("surefire.version") != null) {
            throw new IllegalStateException("refusing to call the model: running under Maven Surefire (mvn test)");
        }
    }

    static String arg(List<String> positional, int i) {
        if (positional.size() <= i) throw new IllegalArgumentException("missing argument, see usage");
        return positional.get(i);
    }

    static void usage() {
        System.out.println("""
                usage (from backend/, via scripts/eval-verdict.sh):
                  check                                  validate stories.jsonl and labels.csv (no model call)
                  describe  <env|anthropic|anthropic-check>          effective verdict settings (no model call)
                  estimate  <target> <runs> [--assume-model ID]      calls and cost estimate (no model call)
                  run       <target> <runs> [--out DIR] [--pause 15] [--cap N] [--budget 20] [--phase E0]   CALLS THE MODEL
                  summarize <resultsDir> [--baseline DIR[,DIR]]      metrics against the labels (no model call)
                  generate  <target> <spec.md>... [--out DIR] [--cap N] [--budget 20] [--phase E0] [--pipeline true]   CALLS THE MODEL
                  probe-schema <target> [--out DIR] [--budget 20] [--phase E0] [--schema backlog|backlog-stories|story-tasks|story-test-cases]   CALLS THE MODEL (Anthropic, 1 call)
                  reassess <target> <precisions.jsonl> [--out DIR] [--budget 20] [--phase E0]   CALLS THE MODEL (prompt 3, before = verdict-v3.2 / after = verdict-v3.3)
                  test-cases <target> <backlog.json>... [--out DIR] [--passes 1] [--budget 20] [--phase E0]   CALLS THE MODEL (prompt 5 only, ready stories of saved backlogs)
                            backlog generation (analysis role) of each spec, one call each, saved as <spec>.json""");
    }

    // ---------- jeu de reference ----------

    /** Jeu de controle : stories et etiquettes, jamais arbitre. */
    record ControlSet(String stories, String labels) {}

    /** Jeux de controle, dans l'ordre de creation ; seuls ceux dont le fichier de stories existe sont pris. */
    static final List<ControlSet> CONTROL_SETS = List.of(new ControlSet("holdout.jsonl", "holdout-labels.csv"),
            new ControlSet("holdout2.jsonl", "holdout2-labels.csv"));

    List<ControlSet> controlSets() {
        return CONTROL_SETS.stream().filter(c -> Files.exists(dataDir.resolve(c.stories()))).toList();
    }

    /** Stories mesurees : jeu d'origine (stories.jsonl) puis chaque jeu de controle existant. */
    /** Jeu anglais : traduction du controle 1, memes ids, memes etiquettes ; mesure a part ({@code --set en}). */
    static final String ENGLISH_SET = "holdout.en.jsonl";
    static final ControlSet ENGLISH_CONTROL = new ControlSet(ENGLISH_SET, "holdout-labels.csv");

    /** null : jeu francais (origine + controles) ; "en" : jeu anglais seul. */
    String set;

    List<Story> stories(List<String> idsOut) throws IOException {
        if ("en".equals(set)) {
            return readStories(dataDir.resolve(ENGLISH_SET), idsOut);
        }
        List<Story> stories = readStories(dataDir.resolve("stories.jsonl"), idsOut);
        for (ControlSet c : controlSets()) stories.addAll(readStories(dataDir.resolve(c.stories()), idsOut));
        return stories;
    }

    List<String> controlIds(ControlSet c) throws IOException {
        List<String> ids = new ArrayList<>();
        readStories(dataDir.resolve(c.stories()), ids);
        return ids;
    }

    /** Identifiants de tous les jeux de controle (vide s'il n'y en a pas). */
    List<String> holdoutIds() throws IOException {
        List<String> ids = new ArrayList<>();
        for (ControlSet c : controlSets()) ids.addAll(controlIds(c));
        return ids;
    }

    /** Vrai si les etiquettes du jeu sont toutes vides (gabarit pas encore rempli). */
    boolean unlabeled(ControlSet c) throws IOException {
        if (!Files.exists(dataDir.resolve(c.labels()))) return true;
        return Csv.read(dataDir.resolve(c.labels())).stream().skip(1).allMatch(r -> r.get(1).isBlank());
    }

    static List<Story> readStories(Path file, List<String> idsOut) throws IOException {
        JsonMapper json = JsonMapper.builder().build();
        List<Story> stories = new ArrayList<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = json.readTree(line);
            Story story = new Story();
            story.setTitle(node.path("title").asString(""));
            story.setDescription(node.path("description").asString(""));
            List<String> criteria = new ArrayList<>();
            node.path("acceptanceCriteria").forEach(c -> criteria.add(c.asString("")));
            story.setAcceptanceCriteria(criteria);
            stories.add(story);
            idsOut.add(node.path("id").asString(""));
        }
        return stories;
    }

    /** Etiquettes de reference : labels.csv, corrige par labels-adjudication.csv s'il existe. */
    List<Reference> references() throws IOException {
        return references(true);
    }

    /**
     * {@code adjudicated} false = labels.csv seul (etiquettes v1). L'arbitrage ne remplace que les stories qu'il cite,
     * et seulement si son {@code label_v1} / {@code confidence_v1} correspond encore a labels.csv.
     */
    List<Reference> references(boolean adjudicated) throws IOException {
        List<Reference> refs = readLabels("labels.csv");
        if (!adjudicated) return refs;
        List<String> holdout = holdoutIds();
        for (List<String> a : adjudication()) {
            String id = a.get(0);
            if (holdout.contains(id)) throw new IllegalStateException(ADJUDICATION + ": " + id + ": the holdout set is never adjudicated");
            int i = indexOf(refs, id);
            Reference v1 = refs.get(i);
            if (v1.label() != label(ADJUDICATION, id, a.get(1)) || v1.confidence() != confidence(ADJUDICATION, id, a.get(2))) {
                throw new IllegalStateException(ADJUDICATION + ": " + id + ": label_v1/confidence_v1 differ from labels.csv");
            }
            refs.set(i, new Reference(id, label(ADJUDICATION, id, a.get(3)), confidence(ADJUDICATION, id, a.get(4)), a.get(5)));
        }
        return refs;
    }

    static final String ADJUDICATION = "labels-adjudication.csv";

    /** Etiquettes d'un jeu de controle, telles quelles, jamais arbitrees. */
    List<Reference> controlReferences(ControlSet c) throws IOException {
        return readLabels(c.labels());
    }

    List<Reference> readLabels(String file) throws IOException {
        List<List<String>> rows = Csv.read(dataDir.resolve(file));
        if (!rows.get(0).equals(List.of("id", "label", "reason", "confidence", "labeled_by", "labeled_on"))) {
            throw new IllegalStateException(file + ": unexpected header " + rows.get(0));
        }
        List<Reference> refs = new ArrayList<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            refs.add(new Reference(r.get(0), label(file, r.get(0), r.get(1)), confidence(file, r.get(0), r.get(3)), r.get(2)));
        }
        return refs;
    }

    /** Lignes d'arbitrage (sans l'en-tete) ; vide si le fichier n'existe pas. */
    List<List<String>> adjudication() throws IOException {
        Path file = dataDir.resolve(ADJUDICATION);
        if (!Files.exists(file)) return List.of();
        List<List<String>> rows = Csv.read(file);
        if (!rows.get(0).equals(List.of("id", "label_v1", "confidence_v1", "label", "confidence", "reason", "decided_by", "decided_on"))) {
            throw new IllegalStateException(ADJUDICATION + ": unexpected header " + rows.get(0));
        }
        List<List<String>> body = rows.subList(1, rows.size());
        if (body.stream().map(r -> r.get(0)).distinct().count() != body.size()) {
            throw new IllegalStateException(ADJUDICATION + ": duplicate id");
        }
        return body;
    }

    private static int indexOf(List<Reference> refs, String id) {
        for (int i = 0; i < refs.size(); i++) if (refs.get(i).id().equals(id)) return i;
        throw new IllegalStateException(ADJUDICATION + ": unknown id " + id);
    }

    private static Label label(String file, String id, String value) {
        return switch (value) {
            case "ready" -> Label.READY;
            case "not-ready" -> Label.NOT_READY;
            default -> throw new IllegalStateException(file + ": " + id + ": label must be ready or not-ready");
        };
    }

    private static Confidence confidence(String file, String id, String value) {
        return switch (value) {
            case "sure" -> Confidence.SURE;
            case "hesitant" -> Confidence.HESITANT;
            default -> throw new IllegalStateException(file + ": " + id + ": confidence must be sure or hesitant");
        };
    }

    Set<String> labelers() throws IOException {
        Set<String> by = new java.util.TreeSet<>();
        List<String> files = new ArrayList<>(List.of("labels.csv"));
        for (ControlSet c : controlSets()) files.add(c.labels());
        for (String file : files) {
            if (!Files.exists(dataDir.resolve(file))) continue;
            List<List<String>> rows = Csv.read(dataDir.resolve(file));
            rows.subList(1, rows.size()).forEach(r -> { if (!r.get(4).isBlank()) by.add(r.get(4)); });
        }
        return by;
    }

    void check() throws IOException {
        List<String> all = new ArrayList<>();
        stories(all);
        if (new HashSet<>(all).size() != all.size()) throw new IllegalStateException("stories.jsonl / holdout.jsonl: duplicate id");
        for (ControlSet c : controlSets()) {
            List<String> holdout = controlIds(c);
            List<String> labelIds = Csv.read(dataDir.resolve(c.labels())).stream().skip(1).map(r -> r.get(0)).toList();
            if (!labelIds.equals(holdout)) {
                throw new IllegalStateException(c.labels() + " ids differ from " + c.stories() + " (same ids, same order)");
            }
            if (unlabeled(c)) {
                System.out.printf("%s: stories=%d NOT LABELED YET (run refused until it is)%n", c.stories(), holdout.size());
                continue;
            }
            List<Reference> h = controlReferences(c);
            System.out.printf("%s: stories=%d ready=%d (sure %d) not-ready=%d (sure %d) labeled_by=%s adjudicated=none%n", c.stories(), h.size(),
                    h.stream().filter(r -> r.label() == Label.READY).count(),
                    h.stream().filter(r -> r.label() == Label.READY && r.confidence() == Confidence.SURE).count(),
                    h.stream().filter(r -> r.label() == Label.NOT_READY).count(),
                    h.stream().filter(r -> r.label() == Label.NOT_READY && r.confidence() == Confidence.SURE).count(),
                    labelers());
        }
        List<String> ids = new ArrayList<>();
        List<Story> stories = readStories(dataDir.resolve("stories.jsonl"), ids);
        List<Reference> refs = references();
        List<String> labelIds = refs.stream().map(Reference::id).toList();
        if (new HashSet<>(ids).size() != ids.size()) throw new IllegalStateException("stories.jsonl: duplicate id");
        if (!labelIds.equals(ids)) throw new IllegalStateException("labels.csv ids differ from stories.jsonl (same ids, same order)");
        long sure = refs.stream().filter(r -> r.confidence() == Confidence.SURE).count();
        long ready = refs.stream().filter(r -> r.label() == Label.READY).count();
        System.out.printf("stories=%d labels=%d ready=%d not-ready=%d sure=%d hesitant=%d labeled_by=%s adjudicated=%s%n", stories.size(),
                refs.size(), ready, refs.size() - ready, sure, refs.size() - sure, labelers(),
                adjudication().stream().map(r -> r.get(0)).toList());
    }

    // ---------- reglages ----------

    Setup setup(Target target, String config) {
        return setup(target, config, null);
    }

    Setup setup(Target target, String config, String assumedModel) {
        List<String> args = new ArrayList<>(List.of("--spring.config.location=file:" + config,
                "--logging.level.root=WARN", "--logging.level.fr.heliocraft.refinr=WARN"));
        if (assumedModel != null && target == Target.ENV) {
            throw new IllegalArgumentException("--assume-model only applies to the anthropic targets");
        }
        args.addAll(target.springArgs(assumedModel));
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LlmOnly.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).logStartupInfo(false).run(args.toArray(String[]::new))) {
            LlmProperties props = ctx.getBean(LlmProperties.class);
            LlmSettings settings;
            try {
                settings = target == Target.ENV ? EvalLlmSettings.productionVerdict(props) : EvalLlmSettings.verdictRole(props);
            } catch (IllegalStateException e) {
                throw new IllegalStateException(target == Target.ENV ? e.getMessage()
                        : "set " + target.modelVariable + " and EVAL_ANTHROPIC_API_KEY in .env (names only, see eval/verdict/README.md)", e);
            }
            int batchSize = Integer.parseInt(ctx.getEnvironment().getProperty("refinr.llm.verdict.batch-size", "10"));
            return new Setup(target, settings, EvalLlmSettings.maxRepairAttempts(props), batchSize, languagePolicy(ctx));
        }
    }

    static OutputLanguagePolicy languagePolicy(ConfigurableApplicationContext ctx) {
        return OutputLanguagePolicy.fromProperty(ctx.getEnvironment().getProperty(OutputLanguagePolicy.PROPERTY,
                OutputLanguagePolicy.AUTO));
    }

    /** Langue de sortie d'une story, comme en production sans langue demandee (create-jira) : propriete, puis detection. */
    static Language language(Setup s, Story story) {
        StringBuilder text = new StringBuilder();
        if (story.getTitle() != null) text.append(story.getTitle()).append('\n');
        if (story.getDescription() != null) text.append(story.getDescription()).append('\n');
        if (story.getAcceptanceCriteria() != null) story.getAcceptanceCriteria().forEach(c -> text.append(c).append('\n'));
        return s.languages().resolve(null, text.toString());
    }

    static final List<String> PRICES_HEADER = List.of("provider", "model", "max_input_tokens", "input_usd_per_mtok",
            "output_usd_per_mtok", "checked_on", "source");

    Map<String, Price> prices() throws IOException {
        return parsePrices(Csv.read(dataDir.resolve("prices.csv")));
    }

    /** Lignes d'un meme modele = tranches, triees par limite croissante (sans limite en dernier, une seule). */
    static Map<String, Price> parsePrices(List<List<String>> rows) {
        if (!rows.get(0).equals(PRICES_HEADER)) throw new IllegalStateException("prices.csv: unexpected header " + rows.get(0));
        Map<String, List<Tier>> tiers = new LinkedHashMap<>();
        Map<String, String[]> sources = new LinkedHashMap<>();
        for (List<String> r : rows.subList(1, rows.size())) {
            String key = r.get(0) + "|" + r.get(1);
            tiers.computeIfAbsent(key, k -> new ArrayList<>()).add(new Tier(r.get(2).isBlank() ? null : Long.valueOf(r.get(2)),
                    Double.parseDouble(r.get(3)), Double.parseDouble(r.get(4))));
            sources.putIfAbsent(key, new String[]{r.get(5), r.get(6)});
        }
        Map<String, Price> prices = new LinkedHashMap<>();
        tiers.forEach((key, list) -> {
            list.sort(java.util.Comparator.comparing(Tier::maxInputTokens, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())));
            if (list.stream().filter(t -> t.maxInputTokens() == null).count() > 1) {
                throw new IllegalStateException("prices.csv: " + key + " has several tiers without max_input_tokens");
            }
            prices.put(key, new Price(List.copyOf(list), sources.get(key)[0], sources.get(key)[1]));
        });
        return prices;
    }

    Price price(Setup s) throws IOException {
        return prices().get(s.settings().provider().id() + "|" + s.settings().model());
    }

    void describe(Setup s) throws IOException {
        Price p = price(s);
        System.out.println("target=" + s.target().id + " describe=" + s.settings().describe()
                + " optional_params=" + s.optionalParams() + " timeout=" + s.settings().timeout()
                + " max_output_tokens=" + s.settings().maxOutputTokens() + " json_mode=" + s.settings().jsonMode()
                + " max_retries=" + s.settings().retry().maxRetries() + " max_repair_attempts=" + s.repairs()
                + " batch_size=" + s.batchSize() + " prompt=" + MeteredLlmClients.verdictPromptVersion()
                + " api_key_set=" + (ESTIMATE_ONLY_KEY.equals(s.settings().apiKey()) ? "n/a (estimate)" : !s.settings().apiKey().isBlank())
                + " price=" + (p == null ? "none" : p.describe()));
    }

    // ---------- estimation ----------

    List<List<Integer>> batches(int size, int batchSize, int run) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < size; i++) order.add(i);
        Collections.shuffle(order, new Random(BASE_SEED + run));
        List<List<Integer>> batches = new ArrayList<>();
        for (int from = 0; from < size; from += batchSize) {
            batches.add(order.subList(from, Math.min(from + batchSize, size)));
        }
        return batches;
    }

    void estimate(Setup s, int runs) throws IOException {
        describe(s);
        List<String> ids = new ArrayList<>();
        List<Story> stories = stories(ids);
        Price p = price(s);
        long calls = 0, input = 0, output = 0, worstCalls = 0, worstInput = 0, worstOutput = 0;
        double cost = 0, worstCost = 0;
        // pire cas d'un lot de n stories : decoupage complet en moitiés (2n - 1 appels) + nouvelle demande des verdicts
        // manquants, chaque appel avec ses reparations
        for (int run = 1; run <= runs; run++) {
            for (List<Integer> batch : batches(stories.size(), s.batchSize(), run)) {
                int callsPerBatchWorst = 2 * batch.size() * (s.repairs() + 1);
                List<Story> batchStories = batch.stream().map(stories::get).toList();
                long in = Math.round(MeteredLlmClients.verdictRequestChars(batchStories, language(s, batchStories.get(0)))
                        / CHARS_PER_TOKEN);
                calls++;
                input += in;
                output += (long) OUTPUT_TOKENS_PER_STORY * batch.size();
                worstCalls += callsPerBatchWorst;
                // une reparation renvoie la requete + la reponse precedente : entree majoree par la sortie maximale
                worstInput += callsPerBatchWorst * (in + s.settings().maxOutputTokens());
                worstOutput += (long) callsPerBatchWorst * s.settings().maxOutputTokens();
                if (p != null) {
                    // tarif applique requete par requete (tranche selon l'entree de chaque requete)
                    cost += p.cost(in, (long) OUTPUT_TOKENS_PER_STORY * batch.size());
                    worstCost += callsPerBatchWorst * p.cost(in + s.settings().maxOutputTokens(), s.settings().maxOutputTokens());
                }
            }
        }
        System.out.printf(Locale.ROOT, "estimate runs=%d stories=%d batch_size=%d%n", runs, stories.size(), s.batchSize());
        System.out.printf(Locale.ROOT, "  typical: %d calls, ~%d input tokens, ~%d output tokens%n", calls, input, output);
        System.out.printf(Locale.ROOT, "  worst  : %d calls (every batch truncated down to single stories, each call repaired %d times), <= %d input, <= %d output tokens%n",
                worstCalls, s.repairs(), worstInput, worstOutput);
        if (p == null) {
            System.out.println("  cost   : no price for " + s.settings().provider().id() + " / " + s.settings().model()
                    + " in eval/verdict/prices.csv (quota-limited provider: calls are capped, not priced)");
        } else {
            System.out.printf(Locale.ROOT, "  cost   : typical ~%.3f USD, worst <= %.2f USD (prices checked %s, %s)%n",
                    cost, worstCost, p.checkedOn(), p.source());
        }
    }

    // ---------- mesure ----------

    static final class Stop extends RuntimeException {
        final String reason;

        Stop(String reason) {
            super(reason);
            this.reason = reason;
        }
    }

    /** Compteur journalier, registre de depense, relevé de chaque appel. */
    final class Meter implements MeteredLlmClients.Meter {
        final Setup setup;
        final Price price;
        final Path counter;
        final Path ledger;
        final Path callsCsv;
        final int cap;
        final double budget;
        int run, batch;
        Instant started;
        long estimatedInput;
        JsonNode lastUsage;
        Integer lastStatus;
        boolean lastTimeout;
        boolean lastSuspended;

        Meter(Setup setup, Price price, Path callsCsv, Integer cap, double budget, String phase) {
            this.setup = setup;
            this.price = price;
            this.callsCsv = callsCsv;
            this.cap = cap != null ? cap : defaultCap(setup.settings().provider().id());
            this.budget = budget;
            this.counter = stateDir.resolve(setup.settings().provider().id() + "-calls-" + LocalDate.now(ZONE) + ".count");
            this.ledger = stateDir.resolve("spend-" + phase + ".csv");
        }

        int calls() {
            return readInt(counter);
        }

        double spent() {
            if (!Files.exists(ledger)) return 0;
            try {
                return Csv.read(ledger).stream().skip(1).mapToDouble(r -> Double.parseDouble(r.get(4))).sum();
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public void before(LlmSettings settings, LlmRequest request) {
            int total = calls();
            if (total >= cap) throw new Stop("cap-" + cap + "-calls-per-day");
            estimatedInput = Math.round(MeteredLlmClients.requestChars(request) / CHARS_PER_TOKEN);
            if (price != null) {
                double worst = price.cost(estimatedInput, settings.maxOutputTokens());
                if (spent() + worst > budget) throw new Stop("budget-" + budget + "-usd");
            }
            writeString(counter, String.valueOf(total + 1));
            started = Instant.now();
            lastUsage = null;
            lastStatus = null;
            lastTimeout = false;
            lastSuspended = false;
        }

        @Override
        public void usage(JsonNode response) {
            lastUsage = response;
        }

        @Override
        public void success() {
            record("ok");
        }

        @Override
        public void failure(LlmException e) {
            String msg = String.valueOf(e.getMessage());
            Matcher m = STATUS.matcher(msg);
            lastStatus = m.find() ? Integer.parseInt(m.group(1)) : null;
            boolean timeout = msg.contains("did not answer within");
            // Duree murale bien au-dela du delai : machine en veille (le minuteur monotone s'arrete), pas un delai du
            // fournisseur ; jamais compte dans les echecs consecutifs
            lastSuspended = suspended(Duration.between(started, Instant.now()), setup.settings().timeout());
            lastTimeout = timeout && !lastSuspended;
            record(timeout ? (lastSuspended ? "suspended" : "timeout") : e.reason().name().toLowerCase(Locale.ROOT));
        }

        private void record(String outcome) {
            long[] tokens = tokens(lastUsage);
            double cost;
            if (price == null) cost = 0;
            else if (tokens != null) cost = price.cost(tokens[0], tokens[1]);
            else if (lastStatus != null) cost = 0; // erreur HTTP : rien de facture
            else cost = price.cost(estimatedInput, setup.settings().maxOutputTokens()); // inconnu (delai) : pire cas
            if (price != null) {
                appendLine(ledger, "at,target,model,calls,cost_usd", String.join(",", Instant.now().toString(), setup.target().id,
                        setup.settings().model(), "1", String.format(Locale.ROOT, "%.6f", cost)));
            }
            Duration wall = Duration.between(started, Instant.now());
            appendLine(callsCsv, "run,batch,started_at,duration_ms,outcome,http_status,input_tokens,output_tokens,cost_usd,spent_phase_usd,calls_today,suspended",
                    String.join(",", String.valueOf(run), String.valueOf(batch), started.toString(),
                            String.valueOf(Duration.between(started, Instant.now()).toMillis()), outcome,
                            lastStatus == null ? "" : String.valueOf(lastStatus),
                            tokens == null ? "" : String.valueOf(tokens[0]), tokens == null ? "" : String.valueOf(tokens[1]),
                            price == null ? "" : String.format(Locale.ROOT, "%.6f", cost),
                            price == null ? "" : String.format(Locale.ROOT, "%.4f", spent()), String.valueOf(calls()),
                            suspended(wall, setup.settings().timeout()) ? "true" : ""));
        }
    }

    /** Marge au-dela du delai configure : un appel plus long a ete suspendu (veille de la machine). */
    static final Duration SUSPEND_MARGIN = Duration.ofSeconds(30);

    static boolean suspended(Duration wall, Duration timeout) {
        return wall.compareTo(timeout.plus(SUSPEND_MARGIN)) > 0;
    }

    /** Usage facture {entree, sortie} : Anthropic {@code usage}, Google {@code usageMetadata}, OpenAI {@code usage}. */
    static long[] tokens(JsonNode response) {
        if (response == null) return null;
        JsonNode u = response.path("usage");
        if (u.has("input_tokens")) {
            long in = u.path("input_tokens").asLong(0) + u.path("cache_creation_input_tokens").asLong(0) + u.path("cache_read_input_tokens").asLong(0);
            return new long[]{in, u.path("output_tokens").asLong(0)};
        }
        if (u.has("prompt_tokens")) return new long[]{u.path("prompt_tokens").asLong(0), u.path("completion_tokens").asLong(0)};
        JsonNode g = response.path("usageMetadata");
        if (g.has("promptTokenCount")) {
            return new long[]{g.path("promptTokenCount").asLong(0),
                    g.path("candidatesTokenCount").asLong(0) + g.path("thoughtsTokenCount").asLong(0)};
        }
        return null;
    }

    void run(Setup s, int runs, String outOption, int pauseSec, Integer cap, double budget, String phase) throws Exception {
        describe(s);
        if (!s.optionalParams().isEmpty() && s.target() != Target.ENV) {
            throw new IllegalStateException("optional parameters would be sent to the evaluation model: " + s.optionalParams());
        }
        if (s.settings().apiKey().isBlank() && s.target() != Target.ENV) throw new IllegalStateException("EVAL_ANTHROPIC_API_KEY is empty");
        Price price = price(s);
        if (price == null && s.target() != Target.ENV) {
            throw new IllegalStateException("no price for " + s.settings().model() + " in eval/verdict/prices.csv: add it from the official pricing page first");
        }
        for (ControlSet c : controlSets()) {
            if (unlabeled(c)) throw new IllegalStateException(c.labels() + " is not labeled yet: label the control set before measuring");
        }
        List<String> ids = new ArrayList<>();
        List<Story> stories = stories(ids);
        Path out = outOption != null ? Path.of(outOption)
                : dataDir.resolve("results").resolve(LocalDate.now(ZONE) + "-" + s.settings().provider().id() + "-" + s.settings().model()
                        + "-" + MeteredLlmClients.verdictPromptVersion() + ("en".equals(set) ? "-en" : ""));
        Files.createDirectories(out);
        Files.createDirectories(stateDir);
        try (Stream<Path> files = Files.list(out)) {
            List<Path> stops = files.filter(p -> p.getFileName().toString().startsWith("STOP-")).toList();
            if (!stops.isEmpty()) throw new IllegalStateException("stop file present in " + out + ": " + stops + " (remove it to resume)");
        }
        Path meta = out.resolve("meta.md");
        if (!Files.exists(meta)) {
            writeString(meta, """
                    # Mesure du verdict — %s

                    - Début : %s ; cible `%s` ; `describe()` : `%s` ; paramètres optionnels envoyés : %s
                    - Prompt `%s`, lots de %d stories (production), %d réparation(s) au plus, `max-retries=0`, ordre mélangé
                      par exécution (graine %d + numéro d'exécution) ; délai %s, %d tokens de sortie au plus
                    - **Référence étiquetée par une IA** (Claude, `labeled_by` = %s), pas par un humain.%s
                    """.formatted(s.settings().describe(), Instant.now(), s.target().id, s.settings().describe(),
                    s.optionalParams().isEmpty() ? "aucun" : s.optionalParams(), MeteredLlmClients.verdictPromptVersion(),
                    s.batchSize(), s.repairs(), BASE_SEED, s.settings().timeout(), s.settings().maxOutputTokens(), labelers(),
                    s.settings().provider().id().equals("anthropic")
                            ? "\n- **Circularité** : modèle Claude évalué contre des étiquettes posées par Claude ; résultats indicatifs." : "")
                    + ("en".equals(set) ? "- Jeu mesuré : `" + ENGLISH_SET + "` (contrôle 1 traduit en anglais, étiquettes `"
                            + ENGLISH_CONTROL.labels() + "` reprises telles quelles)\n" : "")
                    + "- Langue de sortie : propriété `" + OutputLanguagePolicy.PROPERTY + "` puis détection par story\n");
        }
        Path runsCsv = out.resolve("runs.csv");
        Set<String> done = new HashSet<>();
        if (Files.exists(runsCsv)) {
            Csv.read(runsCsv).stream().skip(1).forEach(r -> done.add(r.get(0) + "/" + r.get(2)));
        }
        Meter meter = new Meter(s, price, out.resolve("calls.csv"), cap, budget, phase);
        LlmReadinessAssessor assessor = new LlmReadinessAssessor(MeteredLlmClients.create(s.settings(), meter),
                new StructuredOutput(s.repairs()), s.batchSize());
        boolean first = true;
        int overload = 0;
        for (int run = 1; run <= runs; run++) {
            List<List<Integer>> batches = batches(stories.size(), s.batchSize(), run);
            for (int b = 0; b < batches.size(); b++) {
                if (done.contains(run + "/" + (b + 1))) continue;
                if (!first && pauseSec > 0) Thread.sleep(pauseSec * 1000L);
                first = false;
                meter.run = run;
                meter.batch = b + 1;
                List<Integer> batch = batches.get(b);
                List<Readiness> verdicts;
                try {
                    List<Story> batchStories = batch.stream().map(stories::get).toList();
                    verdicts = assessor.assess(batchStories, batchStories.stream().map(st -> language(s, st)).toList());
                } catch (Stop stop) {
                    stopFile(out, stop.reason, run, b + 1, meter);
                    return;
                }
                // Lot arrete par le protocole (HTTP 4xx d'arret, surcharge repetee) : non enregistre, --out le refera
                if (meter.lastStatus != null && STOP_STATUSES.contains(meter.lastStatus)) {
                    stopFile(out, "http-" + meter.lastStatus, run, b + 1, meter);
                    return;
                }
                boolean overloaded = meter.lastTimeout || (meter.lastStatus != null && OVERLOAD_STATUSES.contains(meter.lastStatus));
                overload = overloaded ? overload + 1 : 0;
                if (overload >= MAX_CONSECUTIVE_OVERLOAD) {
                    stopFile(out, "consecutive-overload", run, b + 1, meter);
                    return;
                }
                for (int i = 0; i < batch.size(); i++) {
                    Readiness r = verdicts.get(i);
                    appendLine(runsCsv, "run,seed,batch,position,id,ai_ready,source,reason", Csv.line(List.of(String.valueOf(run),
                            String.valueOf(BASE_SEED + run), String.valueOf(b + 1), String.valueOf(i + 1), ids.get(batch.get(i)),
                            r == null ? "" : String.valueOf(r.aiReady()), r == null ? "fallback" : r.source().id(),
                            r == null || r.aiReadyReason() == null ? "" : r.aiReadyReason())));
                }
                long fallbacks = verdicts.stream().filter(v -> v == null).count();
                System.out.printf("run %d batch %d/%d: %d verdict(s), %d fallback(s), calls today %d%s%n", run, b + 1, batches.size(),
                        batch.size() - fallbacks, fallbacks, meter.calls(),
                        price == null ? "" : String.format(Locale.ROOT, ", spent %.4f USD", meter.spent()));
            }
        }
        System.out.println("done: " + out);
    }

    void stopFile(Path out, String reason, int run, int batch, Meter meter) {
        writeString(out.resolve("STOP-" + reason), "run=%d batch=%d at=%s calls_today=%d%n".formatted(run, batch, Instant.now(), meter.calls()));
        System.out.println("STOP " + reason + " (run " + run + ", batch " + batch + ")");
    }

    // ---------- generation (essais de prompts) ----------

    /**
     * Genere le backlog de chaque specification avec le vrai {@link LlmBacklogGenerator} (prompt et schema de production,
     * texte fourni comme par {@code analyze-preview}), sans tracker. Sortie : le backlog tel que Refinr le renvoie (pas la
     * reponse brute), tokens et cout par appel ; memes garde-fous que {@code run} (plafond, budget, arrets).
     */
    /**
     * Sonde du schema de generation chez Anthropic : un appel (corps de production, prompt d'une phrase, sortie limitee),
     * meme configuration que {@code generate}. N'affiche que le statut HTTP, {@code error.message} et la duree.
     */
    /**
     * Mesure C2 : prompt 5 seul (cas de test) sur les stories pretes de backlogs deja enregistres (sortie de
     * {@code generate}), sans generation ; une sortie par fichier et par passe : {@code <source>-pass<N>.json}.
     */
    void testCases(Target target, String config, List<String> backlogs, String outOption, int passes, double budget,
                   String phase) throws Exception {
        if (backlogs.isEmpty()) throw new IllegalArgumentException("missing backlog file(s), see usage");
        List<String> args = new ArrayList<>(List.of("--spring.config.location=file:" + config,
                "--logging.level.root=WARN", "--logging.level.fr.heliocraft.refinr=WARN"));
        args.addAll(target.analysisSpringArgs());
        Setup s;
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LlmOnly.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).logStartupInfo(false).run(args.toArray(String[]::new))) {
            LlmProperties props = ctx.getBean(LlmProperties.class);
            s = new Setup(target, EvalLlmSettings.analysisRole(props), EvalLlmSettings.maxRepairAttempts(props), 0,
                    languagePolicy(ctx));
        }
        Price price = price(s);
        System.out.println("target=" + target.id + " describe=" + s.settings().describe() + " prompt=test-cases-v1"
                + " api_key_set=" + !s.settings().apiKey().isBlank() + " price=" + (price == null ? "none" : price.describe()));
        if (target != Target.ENV) {
            if (s.settings().apiKey().isBlank()) throw new IllegalStateException("EVAL_ANTHROPIC_API_KEY is empty");
            if (price == null) throw new IllegalStateException("no price for " + s.settings().model() + " in eval/verdict/prices.csv");
        }
        Path out = outOption != null ? Path.of(outOption) : root.resolve("eval/generation/results")
                .resolve(LocalDate.now(ZONE) + "-test-cases-" + s.settings().model());
        Files.createDirectories(out);
        Files.createDirectories(stateDir);
        Meter meter = new Meter(s, price, out.resolve("calls.csv"), null, budget, phase);
        fr.heliocraft.refinr.adapters.out.llm.LlmTestCaseGenerator generator = new fr.heliocraft.refinr.adapters.out.llm
                .LlmTestCaseGenerator(MeteredLlmClients.create(s.settings(), meter), new StructuredOutput(s.repairs()), 10);
        JsonMapper json = JsonMapper.builder().build();
        for (int pass = 1; pass <= passes; pass++) {
            for (int f = 0; f < backlogs.size(); f++) {
                Path source = Path.of(backlogs.get(f));
                JsonNode backlog = json.readTree(Files.readString(source, StandardCharsets.UTF_8));
                List<fr.heliocraft.refinr.domain.model.Story> ready = new ArrayList<>();
                StringBuilder text = new StringBuilder();
                for (JsonNode epic : backlog.path("epics")) {
                    for (JsonNode feature : epic.path("features")) {
                        for (JsonNode node : feature.path("stories")) {
                            if (!node.path("aiReady").asBoolean(false)) continue;
                            fr.heliocraft.refinr.domain.model.Story story = new fr.heliocraft.refinr.domain.model.Story();
                            story.setTitle(node.path("title").asString(""));
                            story.setDescription(node.path("description").asString(""));
                            List<String> criteria = new ArrayList<>();
                            node.path("acceptanceCriteria").forEach(c -> criteria.add(c.asString("")));
                            story.setAcceptanceCriteria(criteria);
                            ready.add(story);
                            text.append(story.getTitle()).append('\n').append(story.getDescription()).append('\n');
                        }
                    }
                }
                String name = source.getParent().getFileName() + "--" + source.getFileName().toString().replaceFirst("\\.json$", "");
                if (ready.isEmpty()) {
                    System.out.println(name + ": no ready story, skipped");
                    continue;
                }
                // Meme regle que la chaine : sans langue demandee ni propriete, detection sur les stories
                fr.heliocraft.refinr.domain.model.Language language = s.languages().resolve(null, text.toString());
                meter.run = pass;
                meter.batch = f + 1;
                List<List<fr.heliocraft.refinr.domain.model.TestCase>> result;
                int repairsBefore = generator.repairsTriggered();
                try {
                    result = generator.generateTestCases(ready, language);
                } catch (Stop stop) {
                    stopFile(out, stop.reason, pass, f + 1, meter);
                    return;
                } catch (RuntimeException e) {
                    stopFile(out, "error-" + e.getClass().getSimpleName(), pass, f + 1, meter);
                    System.out.println("error: " + e.getMessage());
                    return;
                }
                List<java.util.Map<String, Object>> stories = new ArrayList<>();
                for (int i = 0; i < ready.size(); i++) {
                    java.util.Map<String, Object> item = new java.util.LinkedHashMap<>();
                    item.put("title", ready.get(i).getTitle());
                    item.put("acceptanceCriteria", ready.get(i).getAcceptanceCriteria());
                    item.put("testCases", result.get(i));
                    stories.add(item);
                }
                writeString(out.resolve(name + "-pass" + pass + ".json"), json.writerWithDefaultPrettyPrinter()
                        .writeValueAsString(java.util.Map.of("source", source.toString().replace("../", ""),
                                "language", language.code(), "repairs", generator.repairsTriggered() - repairsBefore,
                                "stories", stories)));
                System.out.printf(Locale.ROOT, "%s pass %d: %d ready stor(ies), %d coverage repair(s), calls today %d%s%n", name,
                        pass, ready.size(), generator.repairsTriggered() - repairsBefore, meter.calls(),
                        price == null ? "" : String.format(Locale.ROOT, ", spent %.4f USD", meter.spent()));
            }
        }
        System.out.println("coverage repairs triggered: " + generator.repairsTriggered());
        System.out.println("done: " + out);
    }

    /**
     * Mesure B3 du prompt 3 : pour chaque story du jeu E0 de {@code precisions.jsonl}, la precision qui repond et la
     * precision hors sujet, en version « avant » (description complete, ligne de verdict comprise, sans champ de
     * question : texte verdict-v3.2) et « apres » (verdict-v3.3 : lignes de verdict retirees, derniere question ouverte
     * dans son champ). Sortie : {@code runs.csv} (id, precision, version, verdict, raison) et {@code calls.csv}.
     */
    void reassess(Setup s, Path precisions, String outOption, double budget, String phase) throws Exception {
        describe(s);
        if (!s.optionalParams().isEmpty() && s.target() != Target.ENV) {
            throw new IllegalStateException("optional parameters would be sent to the evaluation model: " + s.optionalParams());
        }
        if (s.settings().apiKey().isBlank() && s.target() != Target.ENV) throw new IllegalStateException("EVAL_ANTHROPIC_API_KEY is empty");
        Price price = price(s);
        if (price == null && s.target() != Target.ENV) throw new IllegalStateException("no price for " + s.settings().model());
        JsonMapper json = JsonMapper.builder().build();
        java.util.Map<String, JsonNode> byId = new java.util.LinkedHashMap<>();
        for (String line : Files.readAllLines(dataDir.resolve("stories.jsonl"), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) {
                JsonNode node = json.readTree(line);
                byId.put(node.path("id").asString(""), node);
            }
        }
        Path out = outOption != null ? Path.of(outOption) : root.resolve("eval/reassessment/results")
                .resolve(LocalDate.now(ZONE) + "-" + s.settings().provider().id() + "-" + s.settings().model());
        Files.createDirectories(out);
        Files.createDirectories(stateDir);
        Meter meter = new Meter(s, price, out.resolve("calls.csv"), null, budget, phase);
        fr.heliocraft.refinr.adapters.out.llm.LlmReadinessAssessor assessor = new fr.heliocraft.refinr.adapters.out.llm
                .LlmReadinessAssessor(MeteredLlmClients.create(s.settings(), meter), new StructuredOutput(s.repairs()), 10);
        Path runsCsv = out.resolve("runs.csv");
        int position = 0;
        for (String line : Files.readAllLines(precisions, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode p = json.readTree(line);
            String id = p.path("id").asString("");
            JsonNode story = byId.get(id);
            if (story == null) throw new IllegalStateException("unknown story " + id);
            Story domain = new Story();
            domain.setTitle(story.path("title").asString(""));
            domain.setDescription(story.path("description").asString(""));
            List<String> criteria = new ArrayList<>();
            story.path("acceptanceCriteria").forEach(c -> criteria.add(c.asString("")));
            domain.setAcceptanceCriteria(criteria);
            domain.setAiReady(false);
            domain.setAiReadyReason(p.path("question").asString(""));
            // Description telle que Refinr l'ecrit dans le ticket : recit, criteres, ligne de verdict (question)
            String written = fr.heliocraft.refinr.domain.service.StoryMarkdown.description(domain);
            for (String kind : List.of("answering", "offTopic")) {
                String precision = p.path(kind).asString("");
                for (String version : List.of("before", "after")) {
                    meter.run = 1;
                    meter.batch = ++position;
                    Readiness verdict;
                    try {
                        verdict = "before".equals(version)
                                ? assessor.reassess(domain.getTitle(), written, precision, null, fr.heliocraft.refinr.domain.model.Language.FR)
                                : assessor.reassess(domain.getTitle(),
                                        fr.heliocraft.refinr.domain.service.StoryMarkdown.forModel(written), precision,
                                        fr.heliocraft.refinr.domain.service.StoryMarkdown.lastOpenQuestion(written),
                                        fr.heliocraft.refinr.domain.model.Language.FR);
                    } catch (Stop stop) {
                        stopFile(out, stop.reason, 1, position, meter);
                        return;
                    } catch (RuntimeException e) {
                        stopFile(out, "error-" + e.getClass().getSimpleName(), 1, position, meter);
                        System.out.println("error: " + e.getMessage());
                        return;
                    }
                    appendLine(runsCsv, "id,precision,version,ai_ready,reason", Csv.line(List.of(id, kind, version,
                            String.valueOf(verdict.aiReady()), verdict.aiReadyReason() != null ? verdict.aiReadyReason() : "")));
                    System.out.printf(Locale.ROOT, "%s %s %s: %s, calls today %d%s%n", id, kind, version,
                            verdict.aiReady() ? "ready" : "not ready", meter.calls(),
                            price == null ? "" : String.format(Locale.ROOT, ", spent %.4f USD", meter.spent()));
                }
            }
        }
        System.out.println("done: " + out);
    }

    void probeSchema(Target target, String config, String outOption, double budget, String phase, String schema) throws Exception {
        List<String> args = new ArrayList<>(List.of("--spring.config.location=file:" + config,
                "--logging.level.root=WARN", "--logging.level.fr.heliocraft.refinr=WARN"));
        args.addAll(target.analysisSpringArgs());
        Setup s;
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LlmOnly.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).logStartupInfo(false).run(args.toArray(String[]::new))) {
            LlmProperties props = ctx.getBean(LlmProperties.class);
            s = new Setup(target, EvalLlmSettings.analysisRole(props), EvalLlmSettings.maxRepairAttempts(props), 0,
                    languagePolicy(ctx));
        }
        if (s.settings().apiKey().isBlank()) throw new IllegalStateException("EVAL_ANTHROPIC_API_KEY is empty");
        Price price = price(s);
        if (price == null) throw new IllegalStateException("no price for " + s.settings().model() + " in eval/verdict/prices.csv");
        System.out.println("target=" + target.id + " describe=" + s.settings().describe() + " api_key_set=true");
        Path out = outOption != null ? Path.of(outOption) : root.resolve("eval/generation/results")
                .resolve(LocalDate.now(ZONE) + "-schema-probe-" + s.settings().model());
        Files.createDirectories(out);
        Files.createDirectories(stateDir);
        Meter meter = new Meter(s, price, out.resolve("calls.csv"), null, budget, phase);
        fr.heliocraft.refinr.adapters.out.llm.LlmRequest request = fr.heliocraft.refinr.adapters.out.llm.SchemaProbe.request(schema);
        System.out.println("schema=" + schema);
        meter.before(s.settings(), request);
        fr.heliocraft.refinr.adapters.out.llm.SchemaProbe.Result result = fr.heliocraft.refinr.adapters.out.llm.SchemaProbe
                .probe(s.settings(), request, Duration.ofMinutes(4));
        if (result.status() == 200) {
            meter.usage(result.response());
            meter.success();
            System.out.printf(Locale.ROOT, "status=200 duration_ms=%d%n", result.millis());
        } else {
            meter.failure(new LlmException(LlmException.Reason.UNAVAILABLE, "HTTP " + result.status()));
            System.out.printf(Locale.ROOT, "status=%d duration_ms=%d error.message=%s%n", result.status(), result.millis(),
                    result.errorMessage());
        }
    }

    void generate(Target target, String config, List<String> specs, String outOption, Integer cap, double budget, String phase,
                  boolean pipeline) throws Exception {
        if (specs.isEmpty()) throw new IllegalArgumentException("missing spec file(s), see usage");
        List<String> args = new ArrayList<>(List.of("--spring.config.location=file:" + config,
                "--logging.level.root=WARN", "--logging.level.fr.heliocraft.refinr=WARN"));
        args.addAll(target.analysisSpringArgs());
        Setup s;
        boolean separate;
        try (ConfigurableApplicationContext ctx = new SpringApplicationBuilder(LlmOnly.class).web(WebApplicationType.NONE)
                .bannerMode(Banner.Mode.OFF).logStartupInfo(false).run(args.toArray(String[]::new))) {
            LlmProperties props = ctx.getBean(LlmProperties.class);
            s = new Setup(target, EvalLlmSettings.analysisRole(props), EvalLlmSettings.maxRepairAttempts(props), 0,
                    languagePolicy(ctx));
            separate = EvalLlmSettings.separateVerdict(props);
        }
        if (separate) throw new IllegalStateException("a separate verdict provider is configured: generate measures the generation verdict only");
        Price price = price(s);
        System.out.println("target=" + target.id + " describe=" + s.settings().describe() + " optional_params=" + s.optionalParams()
                + " timeout=" + s.settings().timeout() + " max_output_tokens=" + s.settings().maxOutputTokens()
                + " max_retries=" + s.settings().retry().maxRetries() + " max_repair_attempts=" + s.repairs()
                + " api_key_set=" + !s.settings().apiKey().isBlank() + " price=" + (price == null ? "none" : price.describe()));
        if (target != Target.ENV) {
            if (!s.optionalParams().isEmpty()) throw new IllegalStateException("optional parameters would be sent: " + s.optionalParams());
            if (s.settings().apiKey().isBlank()) throw new IllegalStateException("EVAL_ANTHROPIC_API_KEY is empty");
            if (price == null) throw new IllegalStateException("no price for " + s.settings().model() + " in eval/verdict/prices.csv");
        }
        Path out = outOption != null ? Path.of(outOption) : root.resolve("eval/generation/results")
                .resolve(LocalDate.now(ZONE) + "-" + s.settings().provider().id() + "-" + s.settings().model());
        Files.createDirectories(out);
        Files.createDirectories(stateDir);
        Meter meter = new Meter(s, price, out.resolve("calls.csv"), cap, budget, phase);
        LlmBacklogGenerator generator = new LlmBacklogGenerator(MeteredLlmClients.create(s.settings(), meter), new StructuredOutput(s.repairs()));
        // Chaine C1 (--pipeline true) : meme fournisseur pour la generation, le verdict (prompt 2, lots de 10) et les
        // sous-taches (prompt 4), comme une instance a un seul fournisseur ; tous les appels passent par le compteur
        fr.heliocraft.refinr.application.service.BacklogPipeline chain = !pipeline ? null
                : new fr.heliocraft.refinr.application.service.BacklogPipeline(generator,
                        new fr.heliocraft.refinr.adapters.out.llm.LlmReadinessAssessor(
                                MeteredLlmClients.create(s.settings(), meter), new StructuredOutput(s.repairs()), 10),
                        new fr.heliocraft.refinr.adapters.out.llm.LlmTaskGenerator(
                                MeteredLlmClients.create(s.settings(), meter), new StructuredOutput(s.repairs()), 10));
        PreviewBacklogService preview = new PreviewBacklogService(null, generator, null, 200_000, s.languages(), chain);
        System.out.println("pipeline=" + pipeline);
        JsonMapper json = JsonMapper.builder().build();
        for (int i = 0; i < specs.size(); i++) {
            Path spec = Path.of(specs.get(i));
            String name = spec.getFileName().toString().replaceFirst("\\.md$", "");
            String content = Files.readString(spec, StandardCharsets.UTF_8);
            long worstIn = Math.round((content.length() + 12_000) / CHARS_PER_TOKEN); // prompt + schema ~12 000 caracteres
            System.out.printf(Locale.ROOT, "%s: %d chars, estimated input ~%d tokens, worst cost <= %s%n", name, content.length(), worstIn,
                    price == null ? "n/a" : String.format(Locale.ROOT, "%.4f USD", price.cost(worstIn, s.settings().maxOutputTokens())));
            meter.run = 1;
            meter.batch = i + 1;
            List<Epic> epics;
            try {
                epics = pipeline ? preview.previewText(name, content, null, true,
                        fr.heliocraft.refinr.application.port.in.AnalysisProgress.NONE) : preview.previewText(name, content);
            } catch (Stop stop) {
                stopFile(out, stop.reason, 1, i + 1, meter);
                return;
            } catch (RuntimeException e) {
                // message sans corps de reponse (garanti par les clients) ; on s'arrete sur la premiere erreur
                stopFile(out, "error-" + e.getClass().getSimpleName(), 1, i + 1, meter);
                System.out.println("error: " + e.getMessage());
                return;
            }
            writeString(out.resolve(name + ".json"), json.writerWithDefaultPrettyPrinter().writeValueAsString(java.util.Map.of("epics", epics)));
            System.out.printf(Locale.ROOT, "%s: %d epic(s), %d stor(ies), calls today %d%s%n", name, epics.size(),
                    epics.stream().mapToInt(e -> e.getStories().size()).sum(), meter.calls(),
                    price == null ? "" : String.format(Locale.ROOT, ", spent %.4f USD", meter.spent()));
        }
        System.out.println("done: " + out);
    }

    // ---------- resultats ----------

    static List<Observation> observations(Path results) throws IOException {
        List<Observation> observations = new ArrayList<>();
        for (List<String> r : Csv.read(results.resolve("runs.csv")).stream().skip(1).toList()) {
            observations.add(new Observation(Integer.parseInt(r.get(0)), r.get(4), r.get(5).isEmpty() ? null : Boolean.valueOf(r.get(5)), r.get(7)));
        }
        return observations;
    }

    /**
     * Rapport : jeu d'origine (50 stories, arbitrage applique, et resultat avant arbitrage) ; jeu de controle a part,
     * jamais arbitre ; {@code baselines} (autres dossiers de resultats) : comparaison cote a cote sur le jeu d'origine.
     */
    void summarize(Path results, List<Path> baselines) throws IOException {
        List<Observation> observations = observations(results);
        if ("en".equals(set)) {
            summarizeEnglish(results, observations);
            return;
        }
        VerdictMetrics metrics = new VerdictMetrics(references(), observations);
        List<List<String>> adjudicated = adjudication();
        StringBuilder md = new StringBuilder();
        if (Files.exists(results.resolve("meta.md"))) md.append(Files.readString(results.resolve("meta.md"))).append("\n");
        Path calls = results.resolve("calls.csv");
        if (Files.exists(calls)) {
            List<List<String>> rows = Csv.read(calls).stream().skip(1).toList();
            long in = rows.stream().mapToLong(r -> r.get(6).isEmpty() ? 0 : Long.parseLong(r.get(6))).sum();
            long outTok = rows.stream().mapToLong(r -> r.get(7).isEmpty() ? 0 : Long.parseLong(r.get(7))).sum();
            double cost = rows.stream().mapToDouble(r -> r.get(8).isEmpty() ? 0 : Double.parseDouble(r.get(8))).sum();
            long failed = rows.stream().filter(r -> !r.get(4).equals("ok")).count();
            md.append(String.format(Locale.ROOT, "- Appels : %d (%d en échec) ; tokens facturés : %d en entrée, %d en sortie%s%n%n",
                    rows.size(), failed, in, outTok, rows.stream().anyMatch(r -> !r.get(8).isEmpty())
                            ? String.format(Locale.ROOT, " ; coût : %.4f USD", cost) : ""));
        }
        if (!adjudicated.isEmpty()) {
            md.append("- Étiquettes : `labels.csv` corrigé par `").append(ADJUDICATION).append("` (")
                    .append(String.join(", ", adjudicated.stream().map(r -> r.get(0) + " " + r.get(1) + "/" + r.get(2)
                            + " → " + r.get(3) + "/" + r.get(4)).toList()))
                    .append(").\n  **Arbitrage fait après avoir vu les réponses des modèles** : il biaise la mesure en leur faveur.\n\n");
        }
        List<String> holdout = holdoutIds();
        boolean measuredHoldout = observations.stream().anyMatch(o -> holdout.contains(o.id()));
        if (measuredHoldout) md.append("# Jeu d'origine (").append(metrics.results().size()).append(" stories, étiquettes arbitrées)\n\n");
        if (!baselines.isEmpty()) {
            md.append("## Comparaison sur le jeu d'origine (mêmes étiquettes arbitrées, même seuil)\n\n|");
            List<Map<String, String>> columns = new ArrayList<>();
            for (Path b : baselines) {
                md.append(" | `").append(b.getFileName()).append("`");
                columns.add(new VerdictMetrics(references(), observations(b)).keyFigures());
            }
            md.append(" | `").append(results.getFileName()).append("` |\n|---|").append("---|".repeat(baselines.size() + 1)).append("\n");
            columns.add(metrics.keyFigures());
            for (String k : metrics.keyFigures().keySet()) {
                md.append("| ").append(k);
                for (Map<String, String> col : columns) md.append(" | ").append(col.get(k));
                md.append(" |\n");
            }
            md.append("\n");
        }
        md.append(metrics.markdown());
        if (!adjudicated.isEmpty()) {
            md.append("\n## Avant arbitrage (`labels.csv` seul, même règle de seuil)\n\n")
                    .append(new VerdictMetrics(references(false), observations).acceptanceMarkdown());
        }
        for (ControlSet c : controlSets()) {
            List<String> ids = controlIds(c);
            if (observations.stream().noneMatch(o -> ids.contains(o.id()))) continue; // jeu cree apres cette mesure
            md.append("\n# Jeu de contrôle `").append(c.stories()).append("` (").append(ids.size()).append(" stories, `")
                    .append(c.labels()).append("`, jamais arbitré)\n\n").append(new VerdictMetrics(controlReferences(c), observations).markdown());
        }
        writeString(results.resolve("summary.md"), md.toString());
        System.out.print(md);
    }

    /** Jeu anglais : un seul jeu, etiquettes du controle 1 reprises telles quelles, jamais arbitrees. */
    void summarizeEnglish(Path results, List<Observation> observations) throws IOException {
        StringBuilder md = new StringBuilder();
        if (Files.exists(results.resolve("meta.md"))) md.append(Files.readString(results.resolve("meta.md"))).append("\n");
        md.append("# Contrôle 1, anglais (`").append(ENGLISH_SET).append("`, étiquettes `").append(ENGLISH_CONTROL.labels())
                .append("` reprises telles quelles, jamais arbitrées)\n\n")
                .append(new VerdictMetrics(controlReferences(ENGLISH_CONTROL), observations).markdown());
        writeString(results.resolve("summary.md"), md.toString());
        System.out.print(md);
    }

    // ---------- utilitaires ----------

    static int readInt(Path file) {
        try {
            return Files.exists(file) ? Integer.parseInt(Files.readString(file).strip()) : 0;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void writeString(Path file, String content) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    static void appendLine(Path file, String header, String line) {
        try {
            Files.createDirectories(file.getParent());
            if (!Files.exists(file)) Files.writeString(file, header + "\n", StandardCharsets.UTF_8);
            Files.writeString(file, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** CSV minimal (RFC 4180 : guillemets, virgules et sauts de ligne dans un champ). */
    static final class Csv {
        static List<List<String>> read(Path file) throws IOException {
            String text = Files.readString(file, StandardCharsets.UTF_8);
            List<List<String>> rows = new ArrayList<>();
            List<String> row = new ArrayList<>();
            StringBuilder field = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (quoted) {
                    if (c == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                    else if (c == '"') quoted = false;
                    else field.append(c);
                } else if (c == '"') quoted = true;
                else if (c == ',') { row.add(field.toString()); field.setLength(0); }
                else if (c == '\n' || c == '\r') {
                    if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                    row.add(field.toString()); field.setLength(0);
                    rows.add(row); row = new ArrayList<>();
                } else field.append(c);
            }
            if (field.length() > 0 || !row.isEmpty()) { row.add(field.toString()); rows.add(row); }
            return rows;
        }

        static String line(List<String> fields) {
            List<String> out = new ArrayList<>();
            for (String f : fields) {
                out.add(f.contains(",") || f.contains("\"") || f.contains("\n") || f.contains("\r")
                        ? "\"" + f.replace("\"", "\"\"") + "\"" : f);
            }
            return String.join(",", out);
        }
    }
}
