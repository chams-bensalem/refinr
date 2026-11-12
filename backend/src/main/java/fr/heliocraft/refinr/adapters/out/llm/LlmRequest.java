package fr.heliocraft.refinr.adapters.out.llm;

import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Requete de sortie structuree : instructions systeme, conversation (le dernier message est celui de l'utilisateur)
 * et schema JSON attendu.
 */
public record LlmRequest(String system, List<Turn> messages, String schemaName, JsonNode schema) {

    public LlmRequest {
        messages = List.copyOf(messages);
    }

    /** Message de la conversation. */
    public record Turn(Role role, String text) {}

    public enum Role { USER, ASSISTANT }

    public static LlmRequest of(String system, String user, String schemaName, JsonNode schema) {
        return new LlmRequest(system, List.of(new Turn(Role.USER, user)), schemaName, schema);
    }

    /** La meme requete prolongee par la reponse du modele et un nouveau message de l'utilisateur. */
    public LlmRequest followUp(String assistantText, String userText) {
        List<Turn> turns = new java.util.ArrayList<>(messages);
        turns.add(new Turn(Role.ASSISTANT, assistantText));
        turns.add(new Turn(Role.USER, userText));
        return new LlmRequest(system, turns, schemaName, schema);
    }
}
