// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.ContextRequest;
import fr.heliocraft.refinr.domain.model.ProjectContext;
import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

public class PreviewBacklogService implements PreviewBacklog {

    private final DocumentSourcePort documentSource;
    private final BacklogGeneratorPort generator;
    // Fournisseur de verdict distinct, ou null : le verdict produit par l'analyse est alors conserve
    private final ReadinessAssessorPort separateAssessor;
    // Taille maximale (caracteres Unicode) d'un document fourni en texte
    private final int maxContentChars;
    private final OutputLanguagePolicy languagePolicy;
    // Chaine C1, ou null : "pipeline": true refuse
    private final BacklogPipeline pipeline;
    // Contexte projet (X1)
    private final ProjectContextResolver contexts;

    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor, int maxContentChars) {
        this(documentSource, generator, separateAssessor, maxContentChars, OutputLanguagePolicy.DETECT);
    }

    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor, int maxContentChars,
                                 OutputLanguagePolicy languagePolicy) {
        this(documentSource, generator, separateAssessor, maxContentChars, languagePolicy, null);
    }

    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor, int maxContentChars,
                                 OutputLanguagePolicy languagePolicy, BacklogPipeline pipeline) {
        this(documentSource, generator, separateAssessor, maxContentChars, languagePolicy, pipeline, null);
    }

    /** @param contexts contexte projet (X1) ; null : seul le contexte de la requete s'applique */
    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor, int maxContentChars,
                                 OutputLanguagePolicy languagePolicy, BacklogPipeline pipeline,
                                 ProjectContextResolver contexts) {
        this.contexts = contexts != null ? contexts : ProjectContextResolver.requestOnly(documentSource);
        this.pipeline = pipeline;
        this.languagePolicy = languagePolicy;
        this.documentSource = documentSource;
        this.generator = generator;
        this.separateAssessor = separateAssessor;
        this.maxContentChars = DocumentAnalysis.requirePositiveLimit(maxContentChars);
    }

    @Override
    public List<Epic> preview(String documentId, Language language) {
        return preview(documentId, language, AnalysisProgress.NONE);
    }

    @Override
    public List<Epic> preview(String documentId, Language language, AnalysisProgress progress) {
        return preview(documentId, language, false, progress);
    }

    @Override
    public List<Epic> preview(String documentId, Language language, boolean pipelined, AnalysisProgress progress) {
        return preview(documentId, language, pipelined, ContextRequest.NONE, null, progress);
    }

    @Override
    public List<Epic> previewText(String title, String content, Language language, boolean pipelined,
                                  AnalysisProgress progress) {
        return previewText(title, content, language, pipelined, ContextRequest.NONE, null, progress);
    }

    @Override
    public List<Epic> preview(String documentId, Language language, boolean pipelined, ContextRequest context,
                              String projectKey, AnalysisProgress progress) {
        requirePipeline(pipelined);
        DocumentAnalysis.requireValidId(documentSource, documentId);
        contexts.check(context);
        progress.step(JobView.Step.READING_DOCUMENT);
        String content = SourceFailures.translate(() -> documentSource.getDocumentContent(documentId));
        DocumentAnalysis.requireUsefulText(content);
        return analyze(content, language, pipelined, contexts.resolve(context, projectKey), progress);
    }

    @Override
    public List<Epic> previewText(String title, String content, Language language, boolean pipelined,
                                  ContextRequest context, String projectKey, AnalysisProgress progress) {
        requirePipeline(pipelined);
        String text = DocumentAnalysis.providedText(title, content, maxContentChars);
        contexts.check(context);
        return analyze(text, language, pipelined, contexts.resolve(context, projectKey), progress);
    }

    private void requirePipeline(boolean pipelined) {
        if (pipelined && pipeline == null) {
            throw new UnsupportedOperationException("pipeline not configured");
        }
    }

    @Override
    public List<Epic> previewText(String title, String content, Language language) {
        return previewText(title, content, language, AnalysisProgress.NONE);
    }

    @Override
    public List<Epic> previewText(String title, String content, Language language, AnalysisProgress progress) {
        // Texte fourni : aucun appel a la source active
        return previewText(title, content, language, false, progress);
    }

    /** Chemin commun aux deux entrees : texte utile, generation, verdict distinct eventuel. */
    private List<Epic> analyze(String content, Language requested, boolean pipelined, ProjectContext context,
                               AnalysisProgress progress) {
        DocumentAnalysis.requireUsefulText(content);
        // Langue resolue une fois sur le document : la generation et le verdict distinct la partagent
        Language language = languagePolicy.resolve(requested, content);
        if (pipelined) {
            return pipeline.run(content, language, progress, context.text());
        }
        progress.step(JobView.Step.GENERATING);
        BacklogGeneratorPort generator = context.isPresent() ? this.generator.withContext(context.text()) : this.generator;
        List<Epic> epics = LlmFailures.translate(() -> generator.generate(content, language));
        if (separateAssessor != null) {
            progress.step(JobView.Step.ASSESSING);
            StoryVerdicts.apply(epics, context.isPresent() ? separateAssessor.withContext(context.text()) : separateAssessor,
                    story -> language);
        }
        return epics;
    }
}
