package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/**
 * Cree un backlog fourni par l'appelant (apercu eventuellement modifie). Le contenu a pu changer depuis l'apercu :
 * le verdict de chaque story est recalcule sur son contenu final, celui fourni par l'appelant est ignore.
 */
public interface CreateEditedBacklog {

    /** @param project projet cible ; null ou vide : projet par defaut */
    BacklogCreationResult create(List<Epic> epics, String project);
}
