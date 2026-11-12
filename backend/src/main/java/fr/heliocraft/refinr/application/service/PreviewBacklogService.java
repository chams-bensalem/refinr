package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

public class PreviewBacklogService implements PreviewBacklog {

    private final DocumentSourcePort documentSource;
    private final LlmPort llm;

    public PreviewBacklogService(DocumentSourcePort documentSource, LlmPort llm) {
        this.documentSource = documentSource;
        this.llm = llm;
    }

    @Override
    public List<Epic> preview(String documentId) {
        String content = documentSource.getDocumentContent(documentId);
        return llm.analyzeDocument(content);
    }
}
