// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.eval;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Metriques du verdict contre le jeu de reference (E0) : verdict majoritaire, faux {@code ai-ready}, faux
 * {@code validation-necessaire}, stabilite, matrice de confusion, seuil d'acceptation. Calcul pur, sans appel au modele.
 */
public final class VerdictMetrics {

    public enum Label { READY, NOT_READY }

    public enum Confidence { SURE, HESITANT }

    /** Etiquette de reference d'une story. */
    public record Reference(String id, Label label, Confidence confidence, String reason) {}

    /** Verdict d'une exécution : {@code aiReady} null = repli (aucun verdict du modele). */
    public record Observation(int run, String id, Boolean aiReady, String reason) {}

    /** Verdict majoritaire : ai-ready si au moins autant de « prete » que de « pas prete » (egalite = cas dangereux). */
    public enum Majority { AI_READY, VALIDATION, NONE }

    public record StoryResult(Reference reference, int trueCount, int falseCount, int fallbackCount, Majority majority,
                              String firstDisagreeingReason) {
        int verdicts() {
            return trueCount + falseCount;
        }

        /** Au moins une observation (verdict ou repli) ; sinon story pas encore mesuree (mesure interrompue). */
        boolean measured() {
            return trueCount + falseCount + fallbackCount > 0;
        }

        boolean stable() {
            return verdicts() >= 2 && (trueCount == 0 || falseCount == 0);
        }

        /** Jugee prete dans plus d'une execution sur 5 (part > 20 %). */
        boolean readyTooOften() {
            return verdicts() > 0 && trueCount * 5 > verdicts();
        }
    }

    /** {@code none} : mesurees mais sans verdict du modele (repli) ; {@code unmeasured} : aucune observation. */
    public record Rates(int notReady, int falseAiReady, int ready, int falseValidation, int none, int unmeasured) {
        double falseAiReadyRate() {
            return notReady == 0 ? 0 : (double) falseAiReady / notReady;
        }

        double falseValidationRate() {
            return ready == 0 ? 0 : (double) falseValidation / ready;
        }

        /** Accord : verdict majoritaire egal a l'etiquette, sur les stories qui ont un verdict. */
        double agreement() {
            int judged = notReady + ready;
            return judged == 0 ? Double.NaN : (double) (judged - falseAiReady - falseValidation) / judged;
        }
    }

    /**
     * Seuil valide (PLAN E0) : bloquant sur les etiquettes sure seulement. Faux validation-necessaire lu sur les
     * etiquettes ready / sure (regle fixee apres la premiere mesure E0, les hesitant sont rapportees a part).
     */
    public record Acceptance(boolean zeroFalseAiReadySure, List<String> readyTooOftenSure, boolean falseValidationOk,
                             boolean stabilityOk) {
        boolean blockingPassed() {
            return zeroFalseAiReadySure && readyTooOftenSure.isEmpty();
        }
    }

    static final double MAX_FALSE_VALIDATION = 0.25;
    static final double MIN_STABILITY = 0.90;

    private final List<StoryResult> results;
    private final int runs;

    public VerdictMetrics(List<Reference> references, List<Observation> observations) {
        Map<String, List<Observation>> byId = new LinkedHashMap<>();
        observations.forEach(o -> byId.computeIfAbsent(o.id(), k -> new ArrayList<>()).add(o));
        this.runs = (int) observations.stream().mapToInt(Observation::run).distinct().count();
        this.results = new ArrayList<>();
        for (Reference ref : references) {
            List<Observation> obs = byId.getOrDefault(ref.id(), List.of());
            int t = 0, f = 0, n = 0;
            for (Observation o : obs) {
                if (o.aiReady() == null) n++;
                else if (o.aiReady()) t++;
                else f++;
            }
            Majority majority = t + f == 0 ? Majority.NONE : t >= f ? Majority.AI_READY : Majority.VALIDATION;
            boolean wrongWay = ref.label() == Label.READY ? Majority.VALIDATION == majority : Majority.AI_READY == majority;
            String reason = null;
            if (wrongWay) {
                Boolean wrong = ref.label() != Label.READY;
                reason = obs.stream().filter(o -> wrong.equals(o.aiReady())).map(Observation::reason).findFirst().orElse(null);
            }
            results.add(new StoryResult(ref, t, f, n, majority, reason));
        }
    }

    public int runs() {
        return runs;
    }

    public List<StoryResult> results() {
        return results;
    }

    /** {@code confidence} null = toutes les etiquettes. */
    public Rates rates(Confidence confidence) {
        int notReady = 0, falseReady = 0, ready = 0, falseValidation = 0, none = 0, unmeasured = 0;
        for (StoryResult r : filter(confidence)) {
            if (!r.measured()) {
                unmeasured++;
                continue;
            }
            if (r.majority() == Majority.NONE) {
                none++;
                continue;
            }
            if (r.reference().label() == Label.NOT_READY) {
                notReady++;
                if (r.majority() == Majority.AI_READY) falseReady++;
            } else {
                ready++;
                if (r.majority() == Majority.VALIDATION) falseValidation++;
            }
        }
        return new Rates(notReady, falseReady, ready, falseValidation, none, unmeasured);
    }

    /** Part des stories (au moins 2 verdicts du modele) au meme verdict a chaque execution. */
    public double stability(Confidence confidence) {
        List<StoryResult> measured = filter(confidence).stream().filter(r -> r.verdicts() >= 2).toList();
        return measured.isEmpty() ? Double.NaN : (double) measured.stream().filter(StoryResult::stable).count() / measured.size();
    }

    public Acceptance acceptance() {
        Rates sure = rates(Confidence.SURE);
        List<String> tooOften = filter(Confidence.SURE).stream()
                .filter(r -> r.reference().label() == Label.NOT_READY && r.readyTooOften())
                .map(r -> r.reference().id()).toList();
        double stability = stability(null);
        return new Acceptance(sure.falseAiReady() == 0, tooOften,
                sure.falseValidationRate() <= MAX_FALSE_VALIDATION,
                !Double.isNaN(stability) && stability >= MIN_STABILITY);
    }

    /** Lignes : etiquette ; colonnes : verdict majoritaire (ai-ready, validation-necessaire, aucun). */
    public int[][] confusion(Confidence confidence) {
        int[][] m = new int[2][3];
        for (StoryResult r : filter(confidence)) {
            if (!r.measured()) continue;
            m[r.reference().label() == Label.READY ? 0 : 1][r.majority().ordinal()]++;
        }
        return m;
    }

    /** Stories sans aucune observation (mesure interrompue, a reprendre). */
    public List<String> unmeasured() {
        return results.stream().filter(r -> !r.measured()).map(r -> r.reference().id()).toList();
    }

    private List<StoryResult> filter(Confidence confidence) {
        return confidence == null ? results
                : results.stream().filter(r -> r.reference().confidence() == confidence).toList();
    }

    // ---------- rapport Markdown ----------

    /** Chiffres cles, dans l'ordre d'affichage, pour comparer deux mesures cote a cote. */
    public Map<String, String> keyFigures() {
        Acceptance a = acceptance();
        Rates sure = rates(Confidence.SURE), hesitant = rates(Confidence.HESITANT), all = rates(null);
        Map<String, String> k = new LinkedHashMap<>();
        k.put("exécutions", String.valueOf(runs));
        k.put("**seuil bloquant**", a.blockingPassed() ? "atteint" : "**NON atteint**");
        k.put("faux `ai-ready`, `not-ready` / `sure` (majorité)", sure.falseAiReady() + " sur " + sure.notReady());
        k.put("`not-ready` / `sure` jugées prêtes > 1 fois sur 5", a.readyTooOftenSure().isEmpty() ? "aucune" : String.join(", ", a.readyTooOftenSure()));
        k.put("faux `validation-necessaire`, `ready` / `sure` (≤ 25 %)", sure.falseValidation() + " sur " + sure.ready()
                + " (" + pct(sure.falseValidationRate()) + ")" + (a.falseValidationOk() ? "" : " **ÉCHEC**"));
        k.put("faux `validation-necessaire`, `ready` / `hesitant`", hesitant.falseValidation() + " sur " + hesitant.ready());
        k.put("faux `ai-ready`, `not-ready` / `hesitant`", hesitant.falseAiReady() + " sur " + hesitant.notReady());
        k.put("accord `sure` / toutes", pct(sure.agreement()) + " / " + pct(all.agreement()));
        double st = stability(null);
        k.put("stabilité (≥ 90 %)", Double.isNaN(st) ? "non mesurée" : pct(st) + (a.stabilityOk() ? "" : " **ÉCHEC**"));
        k.put("sans verdict (repli)", String.valueOf(all.none()));
        k.put("non mesurées", all.unmeasured() == 0 ? "0" : all.unmeasured() + " (" + String.join(", ", unmeasured()) + ")");
        return k;
    }

    /** Seuil d'acceptation et taux (sans matrices ni liste des desaccords). */
    public String acceptanceMarkdown() {
        StringBuilder md = new StringBuilder();
        Acceptance a = acceptance();
        md.append("## Seuil d'acceptation (").append(runs).append(" exécution(s))\n\n");
        md.append("| critère | résultat |\n|---|---|\n");
        md.append("| **bloquant** : 0 faux `ai-ready` au verdict majoritaire (étiquettes `sure`) | ")
                .append(pass(a.zeroFalseAiReadySure())).append(" (").append(rates(Confidence.SURE).falseAiReady()).append(") |\n");
        md.append("| **bloquant** : aucune story `not-ready` / `sure` jugée prête dans plus d'une exécution sur 5 | ")
                .append(pass(a.readyTooOftenSure().isEmpty()))
                .append(a.readyTooOftenSure().isEmpty() ? "" : " (" + String.join(", ", a.readyTooOftenSure()) + ")").append(" |\n");
        md.append("| faux `validation-necessaire` ≤ 25 % (étiquettes `ready` / `sure`) | ").append(pass(a.falseValidationOk()))
                .append(" (").append(rates(Confidence.SURE).falseValidation()).append(" sur ").append(rates(Confidence.SURE).ready())
                .append(", ").append(pct(rates(Confidence.SURE).falseValidationRate())).append(") |\n");
        md.append("| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | ")
                .append(rates(Confidence.HESITANT).falseValidation()).append(" sur ").append(rates(Confidence.HESITANT).ready())
                .append(" (").append(pct(rates(Confidence.HESITANT).falseValidationRate())).append(") |\n");
        md.append("| stabilité ≥ 90 % (toutes étiquettes) | ")
                .append(Double.isNaN(stability(null)) ? "non mesurée (une seule exécution)" : pass(a.stabilityOk()) + " (" + pct(stability(null)) + ")")
                .append(" |\n");
        md.append("\n**Seuil bloquant : ").append(a.blockingPassed() ? "atteint" : "NON atteint").append("**")
                .append(runs < 5 ? " — moins de 5 exécutions : critère « plus d'une sur 5 » lu sur la part (> 20 %)." : "").append("\n\n");

        md.append("## Taux\n\n| étiquettes | accord | `not-ready` | faux `ai-ready` | `ready` | faux `validation-necessaire` | sans verdict (repli) | non mesurée | stabilité |\n|---|---|---|---|---|---|---|---|---|\n");
        for (Confidence c : new Confidence[]{Confidence.SURE, Confidence.HESITANT, null}) {
            Rates r = rates(c);
            md.append("| ").append(c == null ? "toutes" : c == Confidence.SURE ? "`sure` (bloquant)" : "`hesitant` (indicatif)")
                    .append(" | ").append(pct(r.agreement())).append(" | ").append(r.notReady()).append(" | ").append(r.falseAiReady()).append(" (").append(pct(r.falseAiReadyRate()))
                    .append(") | ").append(r.ready()).append(" | ").append(r.falseValidation()).append(" (").append(pct(r.falseValidationRate()))
                    .append(") | ").append(r.none()).append(" | ").append(r.unmeasured()).append(" | ").append(pct(stability(c))).append(" |\n");
        }
        return md.toString();
    }

    public String markdown() {
        StringBuilder md = new StringBuilder(acceptanceMarkdown());

        md.append("\n## Matrices de confusion (verdict majoritaire)\n");
        for (Confidence c : new Confidence[]{Confidence.SURE, Confidence.HESITANT, null}) {
            int[][] m = confusion(c);
            md.append("\n").append(c == null ? "Toutes les étiquettes" : "Étiquettes `" + c.name().toLowerCase(Locale.ROOT) + "`")
                    .append("\n\n| étiquette \\ verdict | ai-ready | validation-necessaire | sans verdict |\n|---|---|---|---|\n")
                    .append("| ready | ").append(m[0][0]).append(" | ").append(m[0][1]).append(" | ").append(m[0][2]).append(" |\n")
                    .append("| not-ready | ").append(m[1][0]).append(" | ").append(m[1][1]).append(" | ").append(m[1][2]).append(" |\n");
        }

        md.append("\n## Désaccords et instabilités\n\n| id | étiquette | confiance | verdicts (prête / pas prête / repli) | majorité | raison de l'étiquette | raison du modèle (désaccord) |\n|---|---|---|---|---|---|---|\n");
        int listed = 0;
        for (StoryResult r : results) {
            if (!r.measured()) continue; // listees a part : « non mesurées »
            boolean disagree = r.firstDisagreeingReason() != null || r.majority() == Majority.NONE;
            if (!disagree && (r.stable() || r.verdicts() < 2)) continue;
            listed++;
            Reference ref = r.reference();
            md.append("| ").append(ref.id()).append(" | ").append(ref.label() == Label.READY ? "ready" : "not-ready")
                    .append(" | ").append(ref.confidence().name().toLowerCase(Locale.ROOT))
                    .append(" | ").append(r.trueCount()).append(" / ").append(r.falseCount()).append(" / ").append(r.fallbackCount())
                    .append(" | ").append(switch (r.majority()) { case AI_READY -> "ai-ready"; case VALIDATION -> "validation-necessaire"; case NONE -> "sans verdict (repli)"; })
                    .append(disagree ? " ✗" : " (instable)")
                    .append(" | ").append(cell(ref.reason())).append(" | ").append(cell(r.firstDisagreeingReason())).append(" |\n");
        }
        if (listed == 0) md.append("| – | | | | | | |\n");
        if (!unmeasured().isEmpty()) {
            md.append("\n**Non mesurées** (mesure interrompue, à reprendre) : ").append(String.join(", ", unmeasured())).append("\n");
        }
        return md.toString();
    }

    private static String pass(boolean ok) {
        return ok ? "OK" : "**ÉCHEC**";
    }

    static String pct(double v) {
        return Double.isNaN(v) ? "–" : String.format(Locale.FRANCE, "%.0f %%", v * 100);
    }

    private static String cell(String s) {
        return s == null ? "" : s.replace("|", "\\|").replace("\n", " ");
    }
}
