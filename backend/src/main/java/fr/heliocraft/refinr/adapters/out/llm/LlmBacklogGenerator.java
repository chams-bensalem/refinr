package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

import java.util.List;

/** Analyse d'un document par le fournisseur d'analyse : structure Epic/Story/Subtask, verdict compris. */
public class LlmBacklogGenerator implements BacklogGeneratorPort {

    private static final Logger log = LoggerFactory.getLogger(LlmBacklogGenerator.class);

    private final LlmClient client;
    private final StructuredOutput output;
    private final JsonNode schema = LlmSchemas.load(LlmSchemas.BACKLOG);

    public LlmBacklogGenerator(LlmClient client, StructuredOutput output) {
        this.client = client;
        this.output = output;
    }

    @Override
    public List<Epic> generate(String documentContent) {
        log.info("Analyzing document with {}", client.settings().describe());
        LlmRequest request = LlmRequest.of(LlmPrompts.SYSTEM, LlmPrompts.analysis(documentContent),
                LlmSchemas.BACKLOG, LlmSchemas.forProvider(schema));
        List<Epic> epics = output.call(client, request,
                json -> GeneratedBacklogMapper.toDomain(output.mapper().treeToValue(json, GeneratedBacklog.class).getEpics()));
        log.info("Analysis complete. Generated {} epics", epics.size());
        return epics;
    }
}
