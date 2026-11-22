// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

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

    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars) {
        this(documentSource, generator, separateAssessor, createBacklog, maxContentChars, OutputLanguagePolicy.DETECT);
    }

    public AnalyzeDocumentService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                  ReadinessAssessorPort separateAssessor, CreateBacklog createBacklog,
                                  int maxContentChars, OutputLanguagePolicy languagePolicy) {
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
        DocumentAnalysis.requireValidId(documentSource, documentId);
        log.info("Step 1: Reading source document...");
        progress.step(JobView.Step.READING_DOCUMENT);
        String content = SourceFailures.translate(() -> documentSource.getDocumentContent(documentId));
        return analyzeAndCreateFrom(content, project, language, progress);
    }

    @Override
    public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language) {
        return analyzeTextAndCreate(title, content, project, language, AnalysisProgress.NONE);
    }

    @Override
    public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language,
                                                      AnalysisProgress progress) {
        // Texte fourni : aucun appel a la source active
        log.info("Step 1: Using the provided text...");
        return analyzeAndCreateFrom(DocumentAnalysis.providedText(title, content, maxContentChars), project, language,
                progress);
    }

    /** Chemin commun aux deux entrees : texte utile, generation, verdict distinct eventuel, creation. */
    private BacklogCreationResult analyzeAndCreateFrom(String content, String project, Language requested,
                                                       AnalysisProgress progress) {
        DocumentAnalysis.requireUsefulText(content);

        log.info("Step 2: Analyzing document with AI...");
        // Langue resolue une fois sur le document : la generation et le verdict distinct la partagent
        Language language = languagePolicy.resolve(requested, content);
        progress.step(JobView.Step.GENERATING);
        List<Epic> epics = LlmFailures.translate(() -> generator.generate(content, language));
        if (separateAssessor != null) {
            progress.step(JobView.Step.ASSESSING);
            StoryVerdicts.apply(epics, separateAssessor, story -> language);
        }

        // Dernier point d'arret possible : une creation commencee va toujours au bout
        progress.step(JobView.Step.CREATING_ISSUES);
        log.info("Step 3: Creating issue hierarchy ({} epics)...", epics.size());
        return createBacklog.create(epics, project);
    }
}
