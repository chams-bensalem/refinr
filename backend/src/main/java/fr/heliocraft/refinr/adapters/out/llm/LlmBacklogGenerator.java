// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
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
    private final JsonNode storiesSchema = LlmSchemas.load(LlmSchemas.BACKLOG_STORIES);

    // Contexte projet (X1), ou null
    private final String context;

    public LlmBacklogGenerator(LlmClient client, StructuredOutput output) {
        this(client, output, null);
    }

    private LlmBacklogGenerator(LlmClient client, StructuredOutput output, String context) {
        this.client = client;
        this.output = output;
        this.context = context;
    }

    @Override
    public LlmBacklogGenerator withContext(String context) {
        return new LlmBacklogGenerator(client, output, context);
    }

    @Override
    public List<Epic> generate(String documentContent, Language language) {
        log.info("Analyzing document with {}, output language {}", client.settings().describe(), language.code());
        LlmRequest request = LlmRequest.of(LlmPrompts.system(language), LlmPrompts.analysis(documentContent, language, context),
                LlmSchemas.BACKLOG, LlmSchemas.forProvider(schema));
        List<Epic> epics = output.call(client, request, schema,
                json -> GeneratedBacklogMapper.toDomain(output.mapper().treeToValue(json, GeneratedBacklog.class).getEpics()));
        log.info("Analysis complete. Generated {} epics", epics.size());
        return epics;
    }

    /** Chaine C1 : prompt generation-v3, schema sans sous-taches. */
    @Override
    public List<Epic> generateStories(String documentContent, Language language) {
        log.info("Analyzing document without sub-tasks (pipeline) with {}, output language {}", client.settings().describe(),
                language.code());
        LlmRequest request = LlmRequest.of(LlmPrompts.system(language), LlmPrompts.stories(documentContent, language, context),
                LlmSchemas.BACKLOG_STORIES, LlmSchemas.forProvider(storiesSchema));
        List<Epic> epics = output.call(client, request, storiesSchema,
                json -> GeneratedBacklogMapper.toDomain(output.mapper().treeToValue(json, GeneratedBacklog.class).getEpics()));
        log.info("Analysis complete. Generated {} epics", epics.size());
        return epics;
    }
}
