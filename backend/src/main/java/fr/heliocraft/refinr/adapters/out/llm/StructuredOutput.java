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
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Appel a sortie structuree : texte du modele -> JSON -> validation par le schema JSON -> objet.
 * <p>
 * Sortie invalide (JSON illisible, non conforme au schema ou illisible par le mapping) : la conversation est
 * prolongee par la reponse fautive et la liste des erreurs, au plus {@code maxRepairAttempts} fois, puis
 * {@link Reason#INVALID_OUTPUT}. Une sortie tronquee ou un fournisseur indisponible ne sont jamais relances ici.
 */
public final class StructuredOutput {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutput.class);

    // Erreurs renvoyees au modele par relance (les suivantes sont resumees)
    private static final int MAX_REPORTED_ERRORS = 10;

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
            List<String> problems;
            try {
                JsonNode json = mapper.readTree(JsonOutput.extract(text));
                problems = validate(schema, json);
                if (problems.isEmpty()) {
                    return reader.apply(json);
                }
            } catch (JacksonException e) {
                problems = List.of("JSON invalide : " + e.getOriginalMessage());
            } catch (RuntimeException e) {
                problems = List.of(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
            }
            log.warn("Invalid {} output from {} (attempt {}/{}): {}", client.settings().role(),
                    client.settings().describe(), attempt + 1, maxRepairAttempts + 1, problems);
            if (attempt >= maxRepairAttempts) {
                throw new LlmException(Reason.INVALID_OUTPUT, "The " + client.settings().role()
                        + " model did not produce a valid result after " + (attempt + 1) + " attempt(s)");
            }
            current = current.followUp(text, repairMessage(problems));
        }
    }

    /** Erreurs de validation (chemin + message), vide si le JSON est conforme. */
    List<String> validate(JsonNode schema, JsonNode json) {
        Schema compiled = schemas.computeIfAbsent(schema, registry::getSchema);
        return compiled.validate(json).stream().map(Error::toString).toList();
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
