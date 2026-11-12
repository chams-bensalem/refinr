package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.LlmPort;
import fr.heliocraft.refinr.application.service.AnalyzeDocumentService;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.application.service.CreateEditedBacklogService;
import fr.heliocraft.refinr.application.service.PreviewBacklogService;
import fr.heliocraft.refinr.application.service.ReassessStoryService;
import fr.heliocraft.refinr.application.service.SearchDocumentsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cablage des cas d'usage : les services applicatifs restent du Java pur, sans annotation Spring. */
@Configuration
public class UseCaseConfig {

    @Bean
    public CreateBacklog createBacklog(IssueTrackerPort issueTracker) {
        return new CreateBacklogService(issueTracker);
    }

    @Bean
    public CreateEditedBacklog createEditedBacklog(LlmPort llm, CreateBacklog createBacklog) {
        return new CreateEditedBacklogService(llm, createBacklog);
    }

    @Bean
    public PreviewBacklog previewBacklog(DocumentSourcePort documentSource, LlmPort llm) {
        return new PreviewBacklogService(documentSource, llm);
    }

    @Bean
    public AnalyzeDocument analyzeDocument(DocumentSourcePort documentSource, LlmPort llm, CreateBacklog createBacklog) {
        return new AnalyzeDocumentService(documentSource, llm, createBacklog);
    }

    @Bean
    public ReassessStory reassessStory(IssueTrackerPort issueTracker, LlmPort llm) {
        return new ReassessStoryService(issueTracker, llm);
    }

    @Bean
    public SearchDocuments searchDocuments(DocumentSourcePort documentSource) {
        return new SearchDocumentsService(documentSource);
    }
}
