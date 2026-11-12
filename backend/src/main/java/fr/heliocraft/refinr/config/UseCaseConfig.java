package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.service.AnalyzeDocumentService;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.application.service.CreateEditedBacklogService;
import fr.heliocraft.refinr.application.service.PreviewBacklogService;
import fr.heliocraft.refinr.application.service.ReassessStoryService;
import fr.heliocraft.refinr.application.service.SearchDocumentsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Cablage des cas d'usage : les services applicatifs restent du Java pur, sans annotation Spring. */
@Configuration
public class UseCaseConfig {

    // Fournisseur de verdict distinct configure : le verdict de l'analyse est alors remplace par le sien
    private final boolean separateVerdict;

    public UseCaseConfig(@Value("${refinr.llm.verdict.provider:}") String verdictProvider) {
        this.separateVerdict = verdictProvider != null && !verdictProvider.isBlank();
    }

    @Bean
    public CreateBacklog createBacklog(IssueTrackerPort issueTracker) {
        return new CreateBacklogService(issueTracker);
    }

    @Bean
    public CreateEditedBacklog createEditedBacklog(ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        return new CreateEditedBacklogService(assessor, createBacklog);
    }

    @Bean
    public PreviewBacklog previewBacklog(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                         ReadinessAssessorPort assessor) {
        return new PreviewBacklogService(documentSource, generator, separateVerdict ? assessor : null);
    }

    @Bean
    public AnalyzeDocument analyzeDocument(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                           ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        return new AnalyzeDocumentService(documentSource, generator, separateVerdict ? assessor : null, createBacklog);
    }

    @Bean
    public ReassessStory reassessStory(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor) {
        return new ReassessStoryService(issueTracker, assessor);
    }

    @Bean
    public SearchDocuments searchDocuments(DocumentSourcePort documentSource) {
        return new SearchDocumentsService(documentSource);
    }
}
