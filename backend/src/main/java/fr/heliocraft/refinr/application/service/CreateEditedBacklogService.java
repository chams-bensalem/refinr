// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

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

    public CreateEditedBacklogService(ReadinessAssessorPort assessor, CreateBacklog createBacklog) {
        this.assessor = assessor;
        this.createBacklog = createBacklog;
    }

    @Override
    public BacklogCreationResult create(List<Epic> epics, String project) {
        StoryVerdicts.apply(epics, assessor);
        return createBacklog.create(epics, project);
    }
}
