// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.WithContext;
import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.domain.model.ProjectContext;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class AnalyzeDocumentService implements AnalyzeDocument {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeDocumentService.class);

    private final DocumentSourcePort documentSource;
    private final BacklogGeneratorPort generator;
    // Fournisseur de verdict distinct, ou null : le verdict produit par l'analyse est alors conserve
    private final ReadinessAssessorPort separateAssessor;
    private final CreateBacklog createBacklog;
    // Taille maximale (caracteres Unicode) d'un document fourni en texte
    private final int maxContentChars;
    private final OutputLanguagePolicy languagePolicy;
    // Chaine C1, ou null : "pipeline": true refuse
    private final BacklogPipeline pipeline;
    // Contexte projet (X1)
    private final ProjectContextResolver contexts;

    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars) {
        this(documentSource, generator, separateAssessor, createBacklog, maxContentChars, OutputLanguagePolicy.DETECT);
    }

    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars, OutputLanguagePolicy languagePolicy) {
        this(documentSource, generator, separateAssessor, createBacklog, maxContentChars, languagePolicy, null);
    }

    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars, OutputLanguagePolicy languagePolicy, BacklogPipeline pipeline) {
        this(documentSource, generator, separateAssessor, createBacklog, maxContentChars, languagePolicy, pipeline, null);
    }

    /** @param contexts contexte projet (X1) ; null : seul le contexte de la requete s'applique */
    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars, OutputLanguagePolicy languagePolicy, BacklogPipeline pipeline,
                                  ProjectContextResolver contexts) {
        this.contexts = contexts != null ? contexts : ProjectContextResolver.requestOnly(documentSource);
        this.pipeline = pipeline;
        this.languagePolicy = languagePolicy;
        this.documentSource = documentSource;
        this.generator = generator;
        this.separateAssessor = separateAssessor;
        this.createBacklog = createBacklog;
        this.maxContentChars = DocumentAnalysis.requirePositiveLimit(maxContentChars);
    }

    @Override
    public BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language) {
        return analyzeAndCreate(documentId, project, language, AnalysisProgress.NONE);
    }

    @Override
    public BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language,
                                                  AnalysisProgress progress) {
        return analyzeAndCreate(documentId, project, language, false, progress);
    }

    @Override
    public BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language, boolean pipelined,
                                                  AnalysisProgress progress) {
        return analyzeAndCreate(documentId, project, language, pipelined, ContextRequest.NONE, progress).value();
    }

    @Override
    public WithContext<BacklogCreationResult> analyzeAndCreate(String documentId, String project, Language language,
                                                               boolean pipelined, ContextRequest context,
                                                               AnalysisProgress progress) {
        requirePipeline(pipelined);
        DocumentAnalysis.requireValidId(documentSource, documentId);
        contexts.check(context);
        log.info("Step 1: Reading source document...");
        progress.step(JobView.Step.READING_DOCUMENT);
        String content = SourceFailures.translate(() -> documentSource.getDocumentContent(documentId));
        DocumentAnalysis.requireUsefulText(content);
        ProjectContext resolved = contexts.resolve(context, project);
        return new WithContext<>(analyzeAndCreateFrom(content, project, language, pipelined, resolved, progress), resolved);
    }

    @Override
    public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language) {
        return analyzeTextAndCreate(title, content, project, language, AnalysisProgress.NONE);
    }

    @Override
    public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                      AnalysisProgress progress) {
        return analyzeTextAndCreate(title, content, project, language, false, progress);
    }

    @Override
    public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                      boolean pipelined, AnalysisProgress progress) {
        return analyzeTextAndCreate(title, content, project, language, pipelined, ContextRequest.NONE, progress).value();
    }

    @Override
    public WithContext<BacklogCreationResult> analyzeTextAndCreate(String title, String content, String project,
                                                                   Language language, boolean pipelined,
                                                                   ContextRequest context, AnalysisProgress progress) {
        requirePipeline(pipelined);
        // Texte fourni : aucun appel a la source active (sauf pour une page de contexte)
        log.info("Step 1: Using the provided text...");
        String text = DocumentAnalysis.providedText(title, content, maxContentChars);
        contexts.check(context);
        ProjectContext resolved = contexts.resolve(context, project);
        return new WithContext<>(analyzeAndCreateFrom(text, project, language, pipelined, resolved, progress), resolved);
    }

    private void requirePipeline(boolean pipelined) {
        if (pipelined && pipeline == null) {
            throw new UnsupportedOperationException("pipeline not configured");
        }
    }

    /** Chemin commun aux deux entrees : texte utile, generation, verdict distinct eventuel, creation. */
    private BacklogCreationResult analyzeAndCreateFrom(String content, String project, Language requested,
                                                       boolean pipelined, ProjectContext context, AnalysisProgress progress) {
        DocumentAnalysis.requireUsefulText(content);

        log.info("Step 2: Analyzing document with AI...");
        // Langue resolue une fois sur le document : la generation et le verdict distinct la partagent
        Language language = languagePolicy.resolve(requested, content);
        List<Epic> epics;
        if (pipelined) {
            epics = pipeline.run(content, language, progress, context.text());
        } else {
            progress.step(JobView.Step.GENERATING);
            BacklogGeneratorPort generator = context.isPresent() ? this.generator.withContext(context.text()) : this.generator;
            epics = LlmFailures.translate(() -> generator.generate(content, language));
            if (separateAssessor != null) {
                progress.step(JobView.Step.ASSESSING);
                StoryVerdicts.apply(epics, context.isPresent() ? separateAssessor.withContext(context.text())
                        : separateAssessor, story -> language);
            }
        }

        // Dernier point d'arret possible : une creation commencee va toujours au bout
        progress.step(JobView.Step.CREATING_ISSUES);
        log.info("Step 3: Creating issue hierarchy ({} epics)...", epics.size());
        return createBacklog.create(epics, project);
    }
}
