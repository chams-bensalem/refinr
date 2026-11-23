// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.domain.service.OutputLanguagePolicy;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.CreateBacklog;
import fr.heliocraft.refinr.application.port.in.CreateEditedBacklog;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/**
 * Creation d'un backlog edite par l'utilisateur : le verdict est recalcule sur le contenu final de chaque story
 * (celui envoye par le client est ignore), puis le backlog est cree.
 */
public class CreateEditedBacklogService implements CreateEditedBacklog {

    private final ReadinessAssessorPort assessor;
    private final CreateBacklog createBacklog;
    private final OutputLanguagePolicy languagePolicy;

    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        this(assessor, createBacklog, OutputLanguagePolicy.DETECT);
    }

    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog,
                                      OutputLanguagePolicy languagePolicy) {
        this.languagePolicy = languagePolicy;
        this.assessor = assessor;
        this.createBacklog = createBacklog;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project, Language language) {
        // Cas de test envoyes par le client : controles avant tout appel (modele ou gestionnaire), 400 sinon
        for (int e = 0; e < epics.size(); e++) {
            List<fr.heliocraft.refinr.domain.model.Story> stories = epics.get(e) != null ? epics.get(e).getStories() : List.of();
            for (int s = 0; s < stories.size(); s++) {
                if (stories.get(s) == null) {
                    continue;
                }
                String violation = fr.heliocraft.refinr.domain.service.TestCasePolicy.violation(stories.get(s),
                        (e + 1) + "." + (s + 1));
                if (violation != null) {
                    throw new fr.heliocraft.refinr.application.port.in.InvalidRequestException(violation);
                }
            }
        }
        // Sans langue demandee : la propriete, puis la detection story par story
        StoryVerdicts.apply(epics, assessor, story -> languagePolicy.resolve(language, StoryText.of(story)));
        return createBacklog.create(epics, project);
    }
}
