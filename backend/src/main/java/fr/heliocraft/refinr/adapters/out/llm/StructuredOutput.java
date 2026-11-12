package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.LlmException.Reason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.function.Function;

/** Appel a sortie structuree : texte du modele -> JSON -> objet. */
public final class StructuredOutput {

    private static final Logger log = LoggerFactory.getLogger(StructuredOutput.class);

    // Comportement de parsing de Jackson 2 (proprietes inconnues refusees...), comme avant la phase 5
    private final ObjectMapper mapper = JsonMapper.builderWithJackson2Defaults().build();

    ObjectMapper mapper() {
        return mapper;
    }

    public <T> T call(LlmClient client, LlmRequest request, Function<JsonNode, T> reader) {
        String text = client.complete(request);
        try {
            return reader.apply(mapper.readTree(JsonOutput.extract(text)));
        } catch (RuntimeException e) {
            log.warn("Invalid {} output from {}: {}", client.settings().role(), client.settings().describe(), e.getMessage());
            throw new LlmException(Reason.INVALID_OUTPUT,
                    "The " + client.settings().role() + " model did not produce a valid result", e);
        }
    }
}
