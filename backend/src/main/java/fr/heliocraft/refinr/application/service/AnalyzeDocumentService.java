package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.domain.model.Epic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AnalyzeDocumentService implements AnalyzeDocument {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeDocumentService.class);

    private final DocumentSourcePort documentSource;
    private final LlmPort llm;
    private final CreateBacklog createBacklog;

    public AnalyzeDocumentService(DocumentSourcePort documentSource, LlmPort llm, CreateBacklog createBacklog) {
        this.documentSource = documentSource;
        this.llm = llm;
        this.createBacklog = createBacklog;
    }

    @Override
    public BacklogCreationResult analyzeAndCreate(String documentId, String project) {
        log.info("Step 1: Reading source document...");
        String content = documentSource.getDocumentContent(documentId);

        log.info("Step 2: Analyzing document with AI...");
        List<Epic> epics = llm.analyzeDocument(content);

        log.info("Step 3: Creating issue hierarchy ({} epics)...", epics.size());
        return createBacklog.create(epics, project);
    }
}
