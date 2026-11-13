// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

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

    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor, int maxContentChars) {
        this.documentSource = documentSource;
        this.generator = generator;
        this.separateAssessor = separateAssessor;
        this.maxContentChars = DocumentAnalysis.requirePositiveLimit(maxContentChars);
    }

    @Override
    public List<Epic> preview(String documentId) {
        DocumentAnalysis.requireValidId(documentSource, documentId);
        String content = SourceFailures.translate(() -> documentSource.getDocumentContent(documentId));
        return analyze(content);
    }

    @Override
    public List<Epic> previewText(String title, String content) {
        // Texte fourni : aucun appel a la source active
        return analyze(DocumentAnalysis.providedText(title, content, maxContentChars));
    }

    /** Chemin commun aux deux entrees : texte utile, generation, verdict distinct eventuel. */
    private List<Epic> analyze(String content) {
        DocumentAnalysis.requireUsefulText(content);
        List<Epic> epics = LlmFailures.translate(() -> generator.generate(content));
        if (separateAssessor != null) {
            StoryVerdicts.apply(epics, separateAssessor);
        }
        return epics;
    }
}
