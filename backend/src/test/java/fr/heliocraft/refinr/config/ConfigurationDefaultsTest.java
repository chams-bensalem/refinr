package fr.heliocraft.refinr.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Les valeurs par defaut d'une propriete doivent etre les memes partout : dans le code ({@link LlmConfiguration}),
 * dans la configuration d'exemple, dans {@code .env.example} et dans le README.
 */
class ConfigurationDefaultsTest {

    // Les tests Maven tournent depuis backend/
    private static final Path EXAMPLE = Path.of("src/main/resources/application-example.properties");
    private static final Path ENV_EXAMPLE = Path.of("../.env.example");
    private static final Path README = Path.of("../README.md");

    @Test
    @DisplayName("The default analysis provider (local Ollama, OpenAI-compatible API) is aligned everywhere")
    void analysisDefaultsAreAligned() throws IOException {
        assertAligned("refinr.llm.analysis.provider", "LLM_ANALYSIS_PROVIDER", LlmConfiguration.DEFAULT_PROVIDER);
        assertAligned("refinr.llm.analysis.base-url", "LLM_ANALYSIS_BASE_URL", LlmConfiguration.DEFAULT_BASE_URL);
        assertAligned("refinr.llm.analysis.model", "LLM_ANALYSIS_MODEL", LlmConfiguration.DEFAULT_MODEL);
    }

    @Test
    @DisplayName("Timeouts and output token limits have the same defaults in the code, the example config and the README")
    void limitsAreAligned() throws IOException {
        assertEquals(LlmConfiguration.DEFAULT_ANALYSIS_TIMEOUT.toSeconds() + "s", exampleDefault("refinr.llm.analysis.timeout"));
        assertEquals(LlmConfiguration.DEFAULT_VERDICT_TIMEOUT.toSeconds() + "s", exampleDefault("refinr.llm.verdict.timeout"));
        assertEquals(String.valueOf(LlmConfiguration.DEFAULT_ANALYSIS_MAX_OUTPUT_TOKENS),
                exampleDefault("refinr.llm.analysis.max-output-tokens"));
        assertEquals(String.valueOf(LlmConfiguration.DEFAULT_VERDICT_MAX_OUTPUT_TOKENS),
                exampleDefault("refinr.llm.verdict.max-output-tokens"));
        for (String key : new String[] {"refinr.llm.analysis.timeout", "refinr.llm.verdict.timeout",
                "refinr.llm.analysis.max-output-tokens", "refinr.llm.verdict.max-output-tokens"}) {
            assertEquals(exampleDefault(key), readmeTableDefault(key), key);
        }
    }

    @Test
    @DisplayName("No separate verdict provider by default")
    void verdictIsOptional() throws IOException {
        assertEquals("", exampleDefault("refinr.llm.verdict.provider"));
        assertEquals("", envExample("LLM_VERDICT_PROVIDER"));
    }

    @Test
    @DisplayName("One Gemini model everywhere it is mentioned: README (badge, examples) and .env.example")
    void geminiModelIsAligned() throws IOException {
        String model = "gemini-2.5-flash";
        String readme = Files.readString(README);
        assertTrue(readme.contains("badge/Gemini-2.5%20Flash-"), "README badge should show " + model);
        for (Path file : new Path[] {README, ENV_EXAMPLE}) {
            Matcher mentioned = Pattern.compile("gemini-\\d[\\w.-]*").matcher(Files.readString(file));
            while (mentioned.find()) {
                assertEquals(model, mentioned.group(), "Gemini model mentioned in " + file);
            }
        }
    }

    private static void assertAligned(String key, String variable, String codeDefault) throws IOException {
        assertEquals(codeDefault, exampleDefault(key), "default of " + key + " in " + EXAMPLE);
        assertEquals(codeDefault, envExample(variable), variable + " in " + ENV_EXAMPLE);
        assertEquals(codeDefault, readmeTableDefault(key), key + " in the README property table");
    }

    /** Defaut d'une propriete de application-example.properties ecrite {@code cle=${VAR:defaut}} ou {@code cle=valeur}. */
    static String exampleDefault(String key) throws IOException {
        for (String line : Files.readAllLines(EXAMPLE)) {
            if (line.startsWith(key + "=")) {
                return placeholderDefault(line.substring(key.length() + 1).trim());
            }
        }
        throw new AssertionError(key + " missing from " + EXAMPLE);
    }

    static String envExample(String variable) throws IOException {
        for (String line : Files.readAllLines(ENV_EXAMPLE)) {
            if (line.startsWith(variable + "=")) {
                return line.substring(variable.length() + 1).trim();
            }
        }
        throw new AssertionError(variable + " missing from " + ENV_EXAMPLE);
    }

    /** Colonne « Defaut » du tableau des proprietes du README : {@code | `cle` | `defaut` | ... |}. */
    static String readmeTableDefault(String key) throws IOException {
        Pattern row = Pattern.compile("^\\| `" + Pattern.quote(key) + "` \\| `([^`]*)` \\|");
        for (String line : Files.readAllLines(README)) {
            Matcher matcher = row.matcher(line);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        throw new AssertionError(key + " missing from the README property table");
    }

    /** {@code ${cle:defaut}} -> defaut (le premier ':' separe la variable de son defaut) ; sinon la valeur. */
    static String placeholderDefault(String expression) {
        if (!expression.startsWith("${")) {
            return expression;
        }
        String inner = expression.substring(2, expression.length() - 1);
        int colon = inner.indexOf(':');
        return colon < 0 ? "" : inner.substring(colon + 1);
    }
}
