package fr.heliocraft.refinr.application.port.out;

import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Readiness;

import java.util.List;

/** Modele de langage : structure le backlog d'un document et juge la preparation des stories. */
public interface LlmPort {

    /** Epics, stories et sous-taches du document, chaque story portant son verdict de preparation. */
    List<Epic> analyzeDocument(String documentContent);

    /** Reevalue une story deja creee avec la precision apportee par l'utilisateur. */
    Readiness reassessStory(String summary, String description, String precision);
}
