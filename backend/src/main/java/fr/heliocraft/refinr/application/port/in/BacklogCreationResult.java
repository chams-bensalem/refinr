package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Epic;

import java.util.List;

/**
 * Resultat de la creation d'un backlog : un echec n'interrompt pas la creation des autres elements, et les elements
 * dont le parent a echoue ne sont pas crees. Les epics portent la cle des tickets effectivement crees.
 *
 * @param created tickets crees, dans l'ordre de creation
 * @param failed  elements non crees, avec une raison sure a afficher
 */
public record BacklogCreationResult(List<Epic> epics, List<CreatedIssue> created, List<FailedIssue> failed) {

    /** @param type epic, story ou subtask */
    public record CreatedIssue(String type, String title, String key) {}

    /** @param type epic, story ou subtask */
    public record FailedIssue(String type, String title, String reason) {}

    public BacklogCreationResult {
        epics = List.copyOf(epics);
        created = List.copyOf(created);
        failed = List.copyOf(failed);
    }

    public boolean isComplete() {
        return failed.isEmpty();
    }
}
