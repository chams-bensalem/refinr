package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Lit un document, le fait analyser et cree le backlog correspondant dans le gestionnaire de tickets. */
public interface AnalyzeDocument {

    /**
     * @param project projet cible ; null ou vide : projet par defaut (permet a un outil appelant de remplir
     *                un projet qu'il vient de creer)
     * @return le backlog cree, chaque element portant la cle de son ticket
     */
    List<Epic> analyzeAndCreate(String documentId, String project);
}
