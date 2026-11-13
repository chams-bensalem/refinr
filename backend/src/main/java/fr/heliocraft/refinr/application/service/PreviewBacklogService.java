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

    public PreviewBacklogService(DocumentSourcePort documentSource, BacklogGeneratorPort generator,
                                 ReadinessAssessorPort separateAssessor) {
        this.documentSource = documentSource;
        this.generator = generator;
        this.separateAssessor = separateAssessor;
    }

    @Override
    public List<Epic> preview(String documentId) {
        DocumentAnalysis.requireValidId(documentSource, documentId);
        String content = documentSource.getDocumentContent(documentId);
        DocumentAnalysis.requireUsefulText(content);
        List<Epic> epics = LlmFailures.translate(() -> generator.generate(content));
        if (separateAssessor != null) {
            StoryVerdicts.apply(epics, separateAssessor);
        }
        return epics;
    }
}
