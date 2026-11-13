// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Les valeurs par defaut d'une propriete doivent etre les memes partout : dans le code ({@link LlmConfiguration}),
 * dans la configuration d'exemple, dans {@code .env.example} et dans les deux README (anglais et francais).
 */
class ConfigurationDefaultsTest {

    // Les tests Maven tournent depuis backend/
    private static final Path EXAMPLE = Path.of("src/main/resources/application-example.properties");
    private static final Path ENV_EXAMPLE = Path.of("../.env.example");
    private static final Path README = Path.of("../README.md");
    private static final Path README_FR = Path.of("../README.fr.md");
    private static final Path[] READMES = {README, README_FR};

    @Test
    @DisplayName("The default analysis provider (local Ollama, OpenAI-compatible API) is aligned everywhere")
    void analysisDefaultsAreAligned() throws IOException {
        // .env.example configure explicitement l'Ollama local, avec les memes valeurs que le defaut du code
        assertEquals(LlmConfiguration.DEFAULT_PROVIDER, envExample("LLM_ANALYSIS_PROVIDER"));
        assertEquals(LlmConfiguration.DEFAULT_BASE_URL, envExample("LLM_ANALYSIS_BASE_URL"));
        assertEquals(LlmConfiguration.DEFAULT_MODEL, envExample("LLM_ANALYSIS_MODEL"));
        // README : fournisseur par defaut, et preset Ollama decrit sur la meme ligne
        assertEquals(LlmConfiguration.DEFAULT_PROVIDER, readmeTableDefault("refinr.llm.analysis.provider"));
        for (String providerRow : readmeTableRows("refinr.llm.analysis.provider")) {
            assertTrue(providerRow.contains(LlmConfiguration.DEFAULT_BASE_URL) && providerRow.contains(LlmConfiguration.DEFAULT_MODEL), providerRow);
        }
    }

    @Test
    @DisplayName("The example config gives no Ollama default to an explicit provider: provider, base-url and model are empty")
    void explicitProviderGetsNoOllamaDefault() throws IOException {
        // Sinon LLM_ANALYSIS_PROVIDER=google seul enverrait les appels Gemini vers l'URL Ollama (HTTP 404)
        for (String key : new String[] {"refinr.llm.analysis.provider", "refinr.llm.analysis.base-url", "refinr.llm.analysis.model"}) {
            assertEquals("", exampleDefault(key), key);
        }
    }

    @Test
    @DisplayName("Retry defaults are aligned between the code, the example config and the README")
    void retryDefaultsAreAligned() throws IOException {
        String[][] expected = {
                {"refinr.llm.analysis.max-retries", String.valueOf(LlmConfiguration.DEFAULT_MAX_RETRIES)},
                {"refinr.llm.analysis.retry-base-delay", LlmConfiguration.DEFAULT_RETRY_BASE_DELAY.toSeconds() + "s"},
                {"refinr.llm.analysis.retry-multiplier", formatNumber(LlmConfiguration.DEFAULT_RETRY_MULTIPLIER)},
                {"refinr.llm.analysis.retry-jitter", formatNumber(LlmConfiguration.DEFAULT_RETRY_JITTER)},
                {"refinr.llm.analysis.retry-max-delay", LlmConfiguration.DEFAULT_RETRY_MAX_DELAY.toSeconds() + "s"}};
        for (String[] entry : expected) {
            assertEquals(entry[1], exampleDefault(entry[0]), entry[0]);
            assertEquals(entry[1], readmeTableDefault(entry[0]), entry[0]);
        }
    }

    @Test
    @DisplayName("Temperature and max-tokens-param defaults are aligned between the code, the example config and the README")
    void temperatureAndLimitParamAreAligned() throws IOException {
        assertEquals(String.valueOf(LlmConfiguration.DEFAULT_ANALYSIS_TEMPERATURE), exampleDefault("refinr.llm.analysis.temperature"));
        assertEquals(String.valueOf(LlmConfiguration.DEFAULT_VERDICT_TEMPERATURE), exampleDefault("refinr.llm.verdict.temperature"));
        assertEquals(exampleDefault("refinr.llm.analysis.temperature"), readmeTableDefault("refinr.llm.analysis.temperature"));
        for (String row : readmeTableRows("refinr.llm.analysis.temperature")) {
            assertTrue(row.contains("`" + LlmConfiguration.DEFAULT_VERDICT_TEMPERATURE + "`"), row);
        }
        for (String key : new String[] {"refinr.llm.analysis.max-tokens-param", "refinr.llm.verdict.max-tokens-param"}) {
            assertEquals("max_tokens", exampleDefault(key), key);
        }
        assertEquals("max_tokens", readmeTableDefault("refinr.llm.analysis.max-tokens-param"));
    }

    private static String formatNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
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
        assertEquals(String.valueOf(LlmConfiguration.DEFAULT_MAX_REPAIR_ATTEMPTS), exampleDefault("refinr.llm.max-repair-attempts"));
        for (String key : new String[] {"refinr.llm.max-repair-attempts", "refinr.llm.analysis.timeout", "refinr.llm.verdict.timeout",
                "refinr.llm.analysis.max-output-tokens", "refinr.llm.verdict.max-output-tokens"}) {
            assertEquals(exampleDefault(key), readmeTableDefault(key), key);
        }
    }

    @Test
    @DisplayName("The health check cache TTL has the same default in the code, the example config and the README")
    void healthCacheTtlIsAligned() throws Exception {
        String codeDefault = ConfigurationDefaultsTest.class.getClassLoader()
                .loadClass("fr.heliocraft.refinr.config.UseCaseConfig")
                .getMethod("checkHealth", java.util.List.class, java.time.Duration.class)
                .getParameters()[1].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
        assertEquals("${refinr.health.cache-ttl:45s}", codeDefault);
        assertEquals("45s", exampleDefault("refinr.health.cache-ttl"));
        assertEquals("45s", readmeTableDefault("refinr.health.cache-ttl"));
    }

    @Test
    @DisplayName("Allowed hosts, CORS origins and API key have the same defaults in the code, the example config, .env.example and the README")
    void httpSecurityDefaultsAreAligned() throws Exception {
        Class<?> config = HttpSecurityConfiguration.class;
        String corsDefault = config.getDeclaredMethod("corsFilter", String.class)
                .getParameters()[0].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
        assertEquals("${refinr.cors.allowed-origins:http://localhost:3000}", corsDefault);
        assertEquals("http://localhost:3000", exampleDefault("refinr.cors.allowed-origins"));
        assertEquals("http://localhost:3000", envExample("CORS_ALLOWED_ORIGINS"));
        assertEquals("http://localhost:3000", readmeTableDefault("refinr.cors.allowed-origins"));

        String hostsDefault = config.getDeclaredMethod("allowedHostFilter", String.class, tools.jackson.databind.ObjectMapper.class)
                .getParameters()[0].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
        assertEquals("${refinr.security.allowed-hosts:localhost,127.0.0.1,[::1]}", hostsDefault);
        assertEquals("localhost,127.0.0.1,[::1]", exampleDefault("refinr.security.allowed-hosts"));
        assertEquals("localhost,127.0.0.1,[::1]", envExample("REFINR_ALLOWED_HOSTS"));
        assertEquals("localhost,127.0.0.1,[::1]", readmeTableDefault("refinr.security.allowed-hosts"));
        String compose = Files.readString(Path.of("../docker-compose.yml"));
        assertEquals(2, compose.split(java.util.regex.Pattern.quote("REFINR_ALLOWED_HOSTS: ${REFINR_ALLOWED_HOSTS:-localhost,127.0.0.1,[::1]}"), -1).length - 1,
                "docker-compose.yml should pass the same default to the backend and the frontend");
        String nginxDefault = Files.readString(Path.of("../frontend/Dockerfile"));
        assertTrue(nginxDefault.contains("REFINR_ALLOWED_HOSTS=\"localhost,127.0.0.1,[::1]\""), "frontend image default");

        String apiKeyDefault = config.getDeclaredMethod("apiKeyFilter", String.class, tools.jackson.databind.ObjectMapper.class)
                .getParameters()[0].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value();
        assertEquals("${refinr.security.api-key:}", apiKeyDefault);
        assertEquals("", exampleDefault("refinr.security.api-key"));
        assertEquals("", envExample("REFINR_API_KEY"));
        for (String row : readmeTableRows("refinr.security.api-key")) {
            assertTrue(row.matches("^\\| `refinr\\.security\\.api-key` \\| \\*\\((empty|vide)\\)\\* \\|.*"), row);
        }
    }

    @Test
    @DisplayName("Verdict batch size and cache defaults are aligned between the code, the example config and the README")
    void verdictBatchAndCacheAreAligned() throws Exception {
        java.lang.reflect.Parameter[] parameters = LlmConfiguration.class.getMethod("readinessAssessor",
                fr.heliocraft.refinr.adapters.out.llm.StructuredOutput.class, int.class, java.time.Duration.class, int.class)
                .getParameters();
        String[][] expected = {{"refinr.llm.verdict.batch-size", "10"}, {"refinr.llm.verdict.cache.ttl", "24h"},
                {"refinr.llm.verdict.cache.max-entries", "1000"}};
        for (int i = 0; i < expected.length; i++) {
            String key = expected[i][0];
            String value = expected[i][1];
            assertEquals("${" + key + ":" + value + "}",
                    parameters[i + 1].getAnnotation(org.springframework.beans.factory.annotation.Value.class).value());
            assertEquals(value, exampleDefault(key), key);
            assertEquals(value, readmeTableDefault(key), key);
        }
    }

    @Test
    @DisplayName("Confluence is the default document source in the code, the example config and .env.example")
    void sourceDefaultIsAligned() throws IOException {
        assertEquals(SourceConfiguration.DEFAULT_PROVIDER, exampleDefault("source.provider"));
        assertEquals(SourceConfiguration.DEFAULT_PROVIDER, envExample("SOURCE_PROVIDER"));
    }

    @Test
    @DisplayName("BookStack: secrets empty by default in the example config, web-url optional and empty everywhere")
    void bookStackDefaultsAreAligned() throws IOException {
        assertEquals("", exampleDefault("bookstack.token-id"));
        assertEquals("", exampleDefault("bookstack.token-secret"));
        assertEquals("", exampleDefault("bookstack.web-url"));
        assertEquals("", envExample("BOOKSTACK_WEB_URL"));
    }

    @Test
    @DisplayName("XWiki: credentials empty by default in the example config, web-url optional, main wiki xwiki by default")
    void xwikiDefaultsAreAligned() throws IOException {
        assertEquals("", exampleDefault("xwiki.username"));
        assertEquals("", exampleDefault("xwiki.password"));
        assertEquals("", exampleDefault("xwiki.web-url"));
        assertEquals("", envExample("XWIKI_WEB_URL"));
        assertEquals("xwiki", exampleDefault("xwiki.wiki"));
        assertEquals("xwiki", envExample("XWIKI_WIKI"));
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
        String model = "gemini-3.6-flash";
        for (Path readme : READMES) {
            assertTrue(Files.readString(readme).contains("badge/Gemini-2.5%20Flash-"), readme + " badge should show " + model);
        }
        for (Path file : new Path[] {README, README_FR, ENV_EXAMPLE}) {
            Matcher mentioned = Pattern.compile("gemini-\\d[\\w.-]*").matcher(Files.readString(file));
            while (mentioned.find()) {
                assertEquals(model, mentioned.group(), "Gemini model mentioned in " + file);
            }
        }
    }

    /** Ligne de la propriete dans le tableau de chaque README (anglais puis francais). */
    static List<String> readmeTableRows(String key) throws IOException {
        List<String> rows = new ArrayList<>();
        for (Path readme : READMES) {
            rows.add(Files.readAllLines(readme).stream().filter(line -> line.startsWith("| `" + key + "` |")).findFirst()
                    .orElseThrow(() -> new AssertionError(key + " missing from the property table of " + readme)));
        }
        return rows;
    }

    /** Defaut d'une propriete de application-example.properties ecrite {@code cle=${VAR:defaut}} ou {@code cle=valeur}. */
    @Test
    @DisplayName("The maximum size of a provided text is aligned between the code, the example config and .env.example")
    void maxContentCharsIsAligned() throws IOException {
        String expected = String.valueOf(UseCaseConfig.DEFAULT_MAX_CONTENT_CHARS);
        assertEquals(expected, exampleDefault("refinr.document.max-content-chars"));
        assertEquals(expected, envExample("DOCUMENT_MAX_CONTENT_CHARS"));
        // Valeur nulle ou negative : echec au demarrage, avec le nom de la propriete
        for (int invalid : new int[] {0, -1}) {
            IllegalStateException e = assertThrows(IllegalStateException.class, () -> new UseCaseConfig("", invalid));
            assertTrue(e.getMessage().contains("refinr.document.max-content-chars"), e.getMessage());
        }
    }

    @Test
    @DisplayName("The maximum request body size is aligned between the code, the example config, .env.example and nginx")
    void maxBodyBytesIsAligned() throws IOException {
        String expected = String.valueOf(HttpSecurityConfiguration.DEFAULT_MAX_BODY_BYTES);
        assertEquals(expected, exampleDefault("refinr.http.max-body-bytes"));
        assertEquals(expected, envExample("HTTP_MAX_BODY_BYTES"));
        // client_max_body_size 5m du proxy du frontend = 5 Mio
        assertTrue(Files.readString(Path.of("../frontend/docker/default.conf.template")).contains("client_max_body_size 5m;"));
        assertEquals(5L * 1024 * 1024, HttpSecurityConfiguration.DEFAULT_MAX_BODY_BYTES);
    }

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

    /**
     * Colonne « Defaut » du tableau des proprietes : {@code | `cle` | `defaut` | ... |}, identique dans les deux README.
     */
    static String readmeTableDefault(String key) throws IOException {
        Pattern row = Pattern.compile("^\\| `" + Pattern.quote(key) + "` \\| `([^`]*)` \\|");
        List<String> defaults = new ArrayList<>();
        for (String line : readmeTableRows(key)) {
            Matcher matcher = row.matcher(line);
            assertTrue(matcher.find(), "no `default` column for " + key + ": " + line);
            defaults.add(matcher.group(1));
        }
        assertEquals(defaults.get(0), defaults.get(1), key + ": README.md and README.fr.md disagree");
        return defaults.get(0);
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
