package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.service.ReadinessPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Verdict de preparation par le fournisseur de verdict (ou d'analyse s'il n'y en a pas de distinct). */
public class LlmReadinessAssessor implements ReadinessAssessorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmReadinessAssessor.class);

    private final LlmClient client;
    private final StructuredOutput output;
    private final JsonNode schema = LlmSchemas.load(LlmSchemas.READINESS);

    public LlmReadinessAssessor(LlmClient client, StructuredOutput output) {
        this.client = client;
        this.output = output;
    }

    /** Un appel par story ; null pour une story dont le verdict n'a pas pu etre obtenu. */
    @Override
    public List<Readiness> assess(List<Story> stories) {
        List<Readiness> verdicts = new ArrayList<>();
        for (Story story : stories) {
            log.info("Assessing readiness of story '{}' with {}", story.getTitle(), client.settings().describe());
            try {
                verdicts.add(call(LlmPrompts.verdict(story)));
            } catch (LlmException e) {
                log.warn("Readiness verdict unavailable for '{}': {}", story.getTitle(), e.getMessage());
                verdicts.add(null);
            }
        }
        return verdicts;
    }

    @Override
    public Readiness reassess(String summary, String description, String precision) {
        log.info("Reassessing story '{}' with the user's precision ({})", summary, client.settings().describe());
        return call(LlmPrompts.reassessment(summary, description, precision));
    }

    private Readiness call(String prompt) {
        LlmRequest request = LlmRequest.of(LlmPrompts.SYSTEM, prompt, LlmSchemas.READINESS, LlmSchemas.forProvider(schema));
        return output.call(client, request, LlmReadinessAssessor::readiness);
    }

    /** Seul un true explicite rend la story codable : "yes", absent ou null -> validation humaine. */
    static Readiness readiness(JsonNode json) {
        if (!json.isObject()) {
            throw new IllegalArgumentException("verdict is not a JSON object");
        }
        JsonNode ready = json.get("aiReady");
        JsonNode reason = json.get("aiReadyReason");
        Object answer = ready == null || ready.isNull() ? null : ready.isBoolean() ? ready.booleanValue() : ready.asString();
        return ReadinessPolicy.fromModelAnswer(answer, reason == null || reason.isNull() ? null : reason.asString());
    }
}
