// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.domain.model.Story;
import tools.jackson.databind.JsonNode;

import java.util.List;

/**
 * Clients de production instrumentes pour le programme de mesure du verdict ({@code VerdictEval}) : chaque requete
 * passe par un {@link Meter} (plafond, budget) et l'usage facture de chaque reponse lui est transmis. Le corps de la
 * requete et la reponse ne sont ni modifies ni conserves. Hors des tests automatiques.
 */
public final class MeteredLlmClients {

    /** Observateur des appels ; {@link #before} peut refuser l'appel en levant une exception non {@link LlmException}. */
    public interface Meter {
        void before(LlmSettings settings, LlmRequest request);

        /** Reponse HTTP 200 recue (avant extraction du texte) : {@code response} sert seulement a lire l'usage. */
        void usage(JsonNode response);

        void success();

        void failure(LlmException e);
    }

    private MeteredLlmClients() {}

    public static LlmClient create(LlmSettings settings, Meter meter) {
        return switch (settings.provider()) {
            case ANTHROPIC -> new AnthropicLlmClient(settings) {
                @Override public String complete(LlmRequest request) { return metered(meter, settings, request, () -> super.complete(request)); }
                @Override protected String text(JsonNode response) { meter.usage(response); return super.text(response); }
            };
            case GOOGLE -> new GoogleLlmClient(settings) {
                @Override public String complete(LlmRequest request) { return metered(meter, settings, request, () -> super.complete(request)); }
                @Override protected String text(JsonNode response) { meter.usage(response); return super.text(response); }
            };
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleLlmClient(settings) {
                @Override public String complete(LlmRequest request) { return metered(meter, settings, request, () -> super.complete(request)); }
                @Override protected String text(JsonNode response) { meter.usage(response); return super.text(response); }
            };
        };
    }

    private static String metered(Meter meter, LlmSettings settings, LlmRequest request, java.util.function.Supplier<String> call) {
        meter.before(settings, request);
        try {
            String text = call.get();
            meter.success();
            return text;
        } catch (LlmException e) {
            meter.failure(e);
            throw e;
        }
    }

    /** Taille en caracteres de la requete de verdict d'un lot (systeme + prompt + schema), pour l'estimation du cout. */
    public static int verdictRequestChars(List<Story> batch, Language language) {
        List<String> ids = new java.util.ArrayList<>();
        for (int i = 0; i < batch.size(); i++) {
            ids.add("s" + (i + 1));
        }
        JsonNode schema = LlmSchemas.forProvider(LlmSchemas.load(LlmSchemas.READINESS_BATCH));
        return LlmPrompts.system(language).length() + LlmPrompts.verdicts(batch, ids, language).length()
                + schema.toString().length();
    }

    /** Taille en caracteres d'une requete (systeme + messages + schema). */
    public static int requestChars(LlmRequest request) {
        int chars = request.system() == null ? 0 : request.system().length();
        for (LlmRequest.Turn turn : request.messages()) {
            chars += turn.text() == null ? 0 : turn.text().length();
        }
        return chars + (request.schema() == null ? 0 : request.schema().toString().length());
    }

    public static String verdictPromptVersion() {
        return LlmPrompts.VERDICT_PROMPT_VERSION;
    }
}
