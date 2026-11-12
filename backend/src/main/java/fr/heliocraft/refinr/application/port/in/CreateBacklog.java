package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Cree epics, stories et sous-taches dans le gestionnaire de tickets et affecte a chacun la cle de son ticket. */
public interface CreateBacklog {

    /** @param project projet cible ; null ou vide : projet par defaut */
    void create(List<Epic> epics, String project);
}
