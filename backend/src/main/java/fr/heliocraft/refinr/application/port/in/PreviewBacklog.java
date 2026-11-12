package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/** Apercu du backlog d'un document, sans rien creer. */
public interface PreviewBacklog {

    List<Epic> preview(String documentId);
}
