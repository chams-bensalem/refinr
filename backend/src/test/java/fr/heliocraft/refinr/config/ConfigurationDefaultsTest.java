package fr.heliocraft.refinr.config;

import fr.heliocraft.refinr.adapters.in.rest.HealthCheckController;
import fr.heliocraft.refinr.adapters.out.llm.LlmBacklogAnalyzer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Parameter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Les valeurs par defaut d'une propriete doivent etre les memes partout : dans le code ({@code @Value}), dans la
 * configuration d'exemple, dans {@code .env.example} et dans le README.
 */
class ConfigurationDefaultsTest {

    // Les tests Maven tournent depuis backend/
    private static final Path EXAMPLE = Path.of("src/main/resources/application-example.properties");
    private static final Path ENV_EXAMPLE = Path.of("../.env.example");
    private static final Path README = Path.of("../README.md");

    @Test
    @DisplayName("ai.provider has the same default in the code, the example config, .env.example and the README")
    void aiProviderDefaultIsAligned() throws IOException {
        String expected = exampleDefault("ai.provider");
        assertEquals("ollama", expected, "default AI provider of the example configuration");

        for (String codeDefault : codeDefaults("ai.provider")) {
            assertEquals(expected, codeDefault, "@Value default of ai.provider");
        }
        assertEquals(expected, envExample("AI_PROVIDER"));
        assertEquals(expected, readmeTableDefault("ai.provider"));
    }

    @Test
    @DisplayName("One Gemini model everywhere: code, example config, .env.example, README table, badge and examples")
    void geminiModelIsAligned() throws IOException {
        String model = exampleDefault("gemini.model");
        assertEquals("gemini-2.5-flash", model, "default Gemini model of the example configuration");

        for (String codeDefault : codeDefaults("gemini.model")) {
            assertEquals(model, codeDefault, "@Value default of gemini.model");
        }
        assertEquals(model, envExample("GEMINI_MODEL"));
        assertEquals(model, readmeTableDefault("gemini.model"));

        // Le verdict confie a Gemini (ou a un pool Gemini derriere LiteLLM) utilise par defaut le meme modele
        assertEquals(model, exampleDefault("ai.verdict.model"));
        for (String codeDefault : codeDefaults("ai.verdict.model")) {
            assertEquals(model, codeDefault, "@Value default of ai.verdict.model");
        }
        assertEquals(model, envExample("AI_VERDICT_MODEL"));

        String readme = Files.readString(README);
        // Badge : gemini-2.5-flash -> "Gemini-2.5%20Flash"
        String version = model.substring("gemini-".length()).replace("-flash", "%20Flash").replace("-pro", "%20Pro");
        assertTrue(readme.contains("badge/Gemini-" + version + "-"), "README badge should show " + model);
        Matcher mentioned = Pattern.compile("gemini-\\d[\\w.-]*").matcher(readme);
        while (mentioned.find()) {
            assertEquals(model, mentioned.group(), "Gemini model mentioned in the README");
        }
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

    /** Tous les defauts {@code @Value("${cle:defaut}")} declares pour cette propriete dans les beans concernes. */
    static List<String> codeDefaults(String key) {
        List<String> defaults = new ArrayList<>();
        for (Class<?> type : List.of(LlmBacklogAnalyzer.class, HealthCheckController.class)) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (constructor.isAnnotationPresent(Autowired.class) || type.getDeclaredConstructors().length == 1) {
                    for (Parameter parameter : constructor.getParameters()) {
                        collect(parameter.getAnnotation(Value.class), key, defaults);
                    }
                }
            }
            for (Field field : type.getDeclaredFields()) {
                collect(field.getAnnotation(Value.class), key, defaults);
            }
        }
        assertFalse(defaults.isEmpty(), "no @Value default found for " + key);
        return defaults;
    }

    private static void collect(Value value, String key, List<String> defaults) {
        if (value != null && value.value().startsWith("${" + key + ":")) {
            defaults.add(placeholderDefault(value.value()));
        }
    }

    /** {@code ${cle:defaut}} -> defaut (imbrication {@code ${VAR:${x}}} non geree : inutile ici) ; sinon la valeur. */
    static String placeholderDefault(String expression) {
        if (!expression.startsWith("${")) {
            return expression;
        }
        String inner = expression.substring(2, expression.length() - 1);
        int colon = inner.indexOf(':');
        return colon < 0 ? "" : inner.substring(colon + 1);
    }

}
