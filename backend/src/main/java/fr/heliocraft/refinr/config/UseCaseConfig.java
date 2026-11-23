// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.CheckHealth;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.in.ReassessStory;
import fr.heliocraft.refinr.application.port.in.ValidateStory;
import fr.heliocraft.refinr.application.port.in.SearchDocuments;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.HealthCheckable;
import fr.heliocraft.refinr.application.port.out.IssueTrackerPort;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort;
import fr.heliocraft.refinr.application.port.in.GenerateStoryTasks;
import fr.heliocraft.refinr.application.service.BacklogPipeline;
import fr.heliocraft.refinr.application.service.StoryTasksService;
import fr.heliocraft.refinr.application.service.StoryLocks;
import fr.heliocraft.refinr.application.service.AnalyzeDocumentService;
import fr.heliocraft.refinr.application.service.CheckHealthService;
import fr.heliocraft.refinr.application.service.CreateBacklogService;
import fr.heliocraft.refinr.application.service.CreateEditedBacklogService;
import fr.heliocraft.refinr.application.service.DescribeSourceService;
import fr.heliocraft.refinr.application.service.DescribeTrackerService;
import fr.heliocraft.refinr.application.service.FeatureLevels;
import fr.heliocraft.refinr.application.service.PreviewBacklogService;
import fr.heliocraft.refinr.application.service.ReassessStoryService;
import fr.heliocraft.refinr.application.service.ValidateStoryService;
import fr.heliocraft.refinr.application.service.SearchDocumentsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;
import java.util.List;

/** Cablage des cas d'usage : les services applicatifs restent du Java pur, sans annotation Spring. */
@Configuration
public class UseCaseConfig {

    /** Taille maximale par defaut d'un document fourni en texte, en caracteres Unicode (justifiee dans DECISIONS.md). */
    public static final int DEFAULT_MAX_CONTENT_CHARS = 200_000;
    static final String MAX_CONTENT_CHARS_PROPERTY = "refinr.document.max-content-chars";

    // Fournisseur de verdict distinct configure : le verdict de l'analyse est alors remplace par le sien
    private final boolean separateVerdict;
    private final int maxContentChars;
    // Langue de sortie : auto (detection francais / anglais) ou un code ISO 639-1 ; refus au demarrage sinon
    private final OutputLanguagePolicy languagePolicy;
    // Gestionnaire sans niveau Feature : label (defaut) ou refus ; refus au demarrage d'une autre valeur
    private final FeatureLevels.Policy featurePolicy;

    public UseCaseConfig(String verdictProvider, int maxContentChars, String outputLanguage) {
        this(verdictProvider, maxContentChars, outputLanguage, "label");
    }

    @Autowired
    public UseCaseConfig(@Value("${refinr.llm.verdict.provider:}") String verdictProvider,
                         @Value("${" + MAX_CONTENT_CHARS_PROPERTY + ":" + DEFAULT_MAX_CONTENT_CHARS + "}") int maxContentChars,
                         @Value("${" + OutputLanguagePolicy.PROPERTY + ":" + OutputLanguagePolicy.AUTO + "}") String outputLanguage,
                         @Value("${" + FeatureLevels.PROPERTY + ":label}") String featurePolicy) {
        try {
            this.featurePolicy = FeatureLevels.Policy.of(featurePolicy);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(e.getMessage(), e);
        }
        this.separateVerdict = verdictProvider != null && !verdictProvider.isBlank();
        this.languagePolicy = OutputLanguagePolicy.fromProperty(outputLanguage);
        if (maxContentChars <= 0) {
            throw new IllegalStateException(MAX_CONTENT_CHARS_PROPERTY + " must be a positive number of characters, got "
                    + maxContentChars);
        }
        this.maxContentChars = maxContentChars;
    }

    /** Verrous par story partages par la creation et story-tasks (sous-taches jamais creees par deux chemins). */
    @Bean
    public StoryLocks storyLocks() {
        return new StoryLocks();
    }

    @Bean
    public CreateBacklog createBacklog(IssueTrackerPort issueTracker, StoryLocks storyLocks) {
        return new CreateBacklogService(issueTracker, featurePolicy, storyLocks);
    }

    @Bean
    public CreateEditedBacklog createEditedBacklog(ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        return new CreateEditedBacklogService(assessor, createBacklog, languagePolicy);
    }

    /** Chaine C1 ("pipeline": true) : le verdict du prompt 2 y est toujours execute, fournisseur distinct ou non. */
    @Bean
    public BacklogPipeline backlogPipeline(BacklogGeneratorPort generator, ReadinessAssessorPort assessor,
                                           TaskGeneratorPort taskGenerator, TestCaseGeneratorPort testCaseGenerator) {
        return new BacklogPipeline(generator, assessor, taskGenerator, testCaseGenerator);
    }

    @Bean
    public PreviewBacklog previewBacklog(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                         ReadinessAssessorPort assessor, BacklogPipeline pipeline) {
        return new PreviewBacklogService(documentSource, generator, separateVerdict ? assessor : null, maxContentChars,
                languagePolicy, pipeline);
    }

    @Bean
    public AnalyzeDocument analyzeDocument(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                           ReadinessAssessorPort assessor, CreateBacklog createBacklog,
                                           BacklogPipeline pipeline) {
        return new AnalyzeDocumentService(documentSource, generator, separateVerdict ? assessor : null, createBacklog,
                maxContentChars, languagePolicy, pipeline);
    }

    @Bean
    public GenerateStoryTasks generateStoryTasks(IssueTrackerPort issueTracker, TaskGeneratorPort taskGenerator,
                                                 StoryLocks storyLocks, TestCaseGeneratorPort testCaseGenerator) {
        return new StoryTasksService(issueTracker, taskGenerator, languagePolicy, storyLocks, testCaseGenerator);
    }

    @Bean
    public ReassessStory reassessStory(IssueTrackerPort issueTracker, ReadinessAssessorPort assessor) {
        return new ReassessStoryService(issueTracker, assessor, languagePolicy);
    }

    @Bean
    public ValidateStory validateStory(IssueTrackerPort issueTracker) {
        return new ValidateStoryService(issueTracker, languagePolicy);
    }

    @Bean
    public DescribeTracker describeTracker(IssueTrackerPort issueTracker) {
        return new DescribeTrackerService(issueTracker, featurePolicy);
    }

    @Bean
    public DescribeSource describeSource(DocumentSourcePort documentSource) {
        return new DescribeSourceService(documentSource, maxContentChars);
    }

    @Bean
    public SearchDocuments searchDocuments(DocumentSourcePort documentSource) {
        return new SearchDocumentsService(documentSource);
    }

    /** Composants dans l'ordre de leur {@code @Order} : source documentaire active, gestionnaire de tickets actif, puis les fournisseurs de modele. */
    @Bean
    public CheckHealth checkHealth(List<HealthCheckable> components,
                                   @Value("${refinr.health.cache-ttl:45s}") Duration cacheTtl) {
        return new CheckHealthService(components, cacheTtl);
    }
}
