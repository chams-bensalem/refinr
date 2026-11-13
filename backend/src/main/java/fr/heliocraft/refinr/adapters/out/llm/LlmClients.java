// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.out.llm;

/** Client du fournisseur configure. */
public final class LlmClients {

    private LlmClients() {}

    public static LlmClient create(LlmSettings settings) {
        return switch (settings.provider()) {
            case ANTHROPIC -> new AnthropicLlmClient(settings);
            case GOOGLE -> new GoogleLlmClient(settings);
            case OPENAI_COMPATIBLE -> new OpenAiCompatibleLlmClient(settings);
        };
    }
}
