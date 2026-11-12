package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.domain.model.Readiness;

/**
 * Reevalue une story en attente de validation avec une precision de l'utilisateur : la story existante est mise
 * a jour, aucun ticket n'est recree.
 */
public interface ReassessStory {

    /** @throws StoryNotAwaitingValidationException si la story n'est pas en attente de validation */
    Readiness reassess(String storyKey, String precision);
}
