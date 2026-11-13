// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import com.networknt.schema.Error;
import com.networknt.schema.Schema;
import com.networknt.schema.SchemaRegistry;
import com.networknt.schema.SpecificationVersion;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.LlmException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Appel a sortie structuree : texte du modele -> JSON -> validation par le schema JSON -> objet.
 * <p>
 * Sortie invalide (JSON illisible, non conforme au schema ou illisible par le mapping) : la conversation est
 * prolongee par la reponse fautive et la liste des erreurs, au plus {@code maxRepairAttempts} fois, puis
 * {@link Reason#INVALID_OUTPUT}. Une sortie tronquee ou un fournisseur indisponible ne sont jamais relances ici.
 * Le detail des erreurs n'est renvoye qu'au modele ; les logs et l'exception n'en gardent que des categories et des
 * tailles (jamais d'extrait de la sortie du modele).
 */
public final class StructuredOutput {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutput.class);

    // Erreurs renvoyees au modele par relance (les suivantes sont resumees)
    private static final int MAX_REPORTED_ERRORS = 10;

    private static final Pattern SAFE_KEYWORD = Pattern.compile("[A-Za-z]{1,32}");

    // Comportement de parsing de Jackson 2 (proprietes inconnues refusees...), comme avant la phase 5
    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();
    private final SchemaRegistry registry = SchemaRegistry.withDefaultDialect(SpecificationVersion.DRAFT_2020_12);
    private final Map<JsonNode, Schema> schemas = new ConcurrentHashMap<>();
    private final int maxRepairAttempts;

    public StructuredOutput(int maxRepairAttempts) {
        if (maxRepairAttempts < 0) {
            throw new IllegalArgumentException("max-repair-attempts must be >= 0");
        }
        this.maxRepairAttempts = maxRepairAttempts;
    }

    ObjectMapper mapper() {
        return mapper;
    }

    /**
     * @param schema schema complet (celui de {@link LlmSchemas#load}) ; la requete porte sa version fournisseur
     */
    public <T> T call(LlmClient client, LlmRequest request, JsonNode schema, Function<JsonNode, T> reader) {
        LlmRequest current = request;
        for (int attempt = 0; ; attempt++) {
            String text = client.complete(current);
            // problems : renvoyes au modele seulement ; summary : categories et tailles, seules a aller dans les logs
            List<String> problems;
            String summary;
            JsonNode json = null;
            try {
                json = mapper.readTree(JsonOutput.extract(text));
                List<Error> errors = validate(schema, json);
                if (errors.isEmpty()) {
                    return reader.apply(json);
                }
                problems = errors.stream().map(Error::toString).toList();
                summary = "schema: " + keywordCounts(errors);
            } catch (RuntimeException e) {
                // Jackson cite des extraits du texte lu : le message ne va qu'au modele
                boolean syntax = json == null && e instanceof JacksonException;
                problems = List.of(syntax ? "JSON invalide : " + ((JacksonException) e).getOriginalMessage()
                        : e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
                summary = syntax ? "json-syntax" : "mapping: " + e.getClass().getSimpleName();
            }
            log.warn("Invalid {} output from {} (attempt {}/{}): {} problem(s) [{}], {} characters received",
                    client.settings().role(), client.settings().describe(), attempt + 1, maxRepairAttempts + 1,
                    problems.size(), summary, text == null ? 0 : text.length());
            if (attempt >= maxRepairAttempts) {
                throw new LlmException(Reason.INVALID_OUTPUT, "The " + client.settings().role()
                        + " model did not produce a valid result after " + (attempt + 1) + " attempt(s)");
            }
            current = current.followUp(text, repairMessage(problems));
        }
    }

    /** Erreurs de validation, vide si le JSON est conforme. */
    List<Error> validate(JsonNode schema, JsonNode json) {
        Schema compiled = schemas.computeIfAbsent(schema, registry::getSchema);
        return compiled.validate(json);
    }

    /**
     * Nombre d'erreurs par mot-cle du schema (ex. {@code type=1, required=2}) : jamais le message, qui peut citer des
     * noms de champs ou des valeurs produits par le modele.
     */
    static String keywordCounts(List<Error> errors) {
        Map<String, Integer> counts = new TreeMap<>();
        for (Error error : errors) {
            String keyword = error.getKeyword();
            counts.merge(keyword != null && SAFE_KEYWORD.matcher(keyword).matches() ? keyword : "other", 1, Integer::sum);
        }
        return counts.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining(", "));
    }

    static String repairMessage(List<String> problems) {
        StringBuilder message = new StringBuilder("Ta réponse précédente n'est pas valide :\n");
        problems.stream().limit(MAX_REPORTED_ERRORS).forEach(p -> message.append("- ").append(p).append('\n'));
        if (problems.size() > MAX_REPORTED_ERRORS) {
            message.append("- ... et ").append(problems.size() - MAX_REPORTED_ERRORS).append(" autre(s) erreur(s)\n");
        }
        message.append("Renvoie uniquement l'objet JSON corrigé, complet et conforme au format demandé, sans texte autour.");
        return message.toString();
    }
}
