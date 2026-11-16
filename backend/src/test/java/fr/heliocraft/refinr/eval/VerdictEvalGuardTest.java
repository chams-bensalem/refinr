// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.eval;

import fr.heliocraft.refinr.adapters.out.llm.MeteredLlmClients;
import fr.heliocraft.refinr.config.EvalLlmSettings;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** La mesure du verdict appelle le modele : elle ne doit jamais etre executee par mvn test ni par la CI. */
class VerdictEvalGuardTest {

    /** Motifs d'inclusion par defaut de Surefire (aucun include/exclude configure dans le pom.xml). */
    private static final Pattern SUREFIRE_DEFAULT = Pattern.compile("^Test.*|.*Test$|.*Tests$|.*TestCase$");

    private static final List<Class<?>> EVAL_CLASSES = List.of(VerdictEval.class, VerdictMetrics.class,
            EvalLlmSettings.class, MeteredLlmClients.class);

    @Test
    void evaluationClassesAreNeitherTestsNorPickedBySurefire() {
        for (Class<?> c : EVAL_CLASSES) {
            assertFalse(SUREFIRE_DEFAULT.matcher(c.getSimpleName()).matches(), c.getSimpleName() + " matches a Surefire pattern");
            for (Class<?> k = c; k != null; k = k.getEnclosingClass()) {
                for (Method m : k.getDeclaredMethods()) {
                    for (Annotation a : m.getAnnotations()) {
                        assertFalse(a.annotationType().getPackageName().startsWith("org.junit"),
                                k.getSimpleName() + "." + m.getName() + " carries " + a.annotationType().getSimpleName());
                    }
                }
            }
            for (Class<?> inner : c.getDeclaredClasses()) {
                for (Method m : inner.getDeclaredMethods()) {
                    for (Annotation a : m.getAnnotations()) {
                        assertFalse(a.annotationType().getPackageName().startsWith("org.junit"), inner.getName() + "." + m.getName());
                    }
                }
            }
        }
    }

    @Test
    void runIsRefusedUnderSurefireItself() {
        // Ce test tourne sous Surefire : le garde-fou doit le detecter avec les vraies proprietes de la JVM
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> VerdictEval.refuseAutomatedRun(Map.of(), System.getProperties()));
        assertTrue(e.getMessage().contains("refusing to call the model"));
    }

    @Test
    void runIsRefusedInCiAndAllowedLocally() {
        Properties local = new Properties();
        assertThrows(IllegalStateException.class, () -> VerdictEval.refuseAutomatedRun(Map.of("CI", "true"), local));
        assertThrows(IllegalStateException.class, () -> VerdictEval.refuseAutomatedRun(Map.of("GITHUB_ACTIONS", "true"), local));
        assertDoesNotThrow(() -> VerdictEval.refuseAutomatedRun(Map.of("CI", "false"), local));
        assertDoesNotThrow(() -> VerdictEval.refuseAutomatedRun(Map.of(), local));
    }

    @Test
    void pricesAreTieredPerRequest() throws Exception {
        Map<String, VerdictEval.Price> prices = VerdictEval.parsePrices(
                VerdictEval.Csv.read(Path.of("..", "eval", "verdict", "prices.csv")));
        VerdictEval.Price haiku = prices.get("anthropic|claude-haiku-5-5");
        // jusqu'a 100 000 tokens de prompt : 0,10 / 0,50 USD par million ; au-dela : 0,50 / 2,50
        assertEquals((100_000 * 0.10 + 1_000 * 0.50) / 1_000_000d, haiku.cost(100_000, 1_000), 1e-12);
        assertEquals((100_001 * 0.50 + 1_000 * 2.50) / 1_000_000d, haiku.cost(100_001, 1_000), 1e-12);
        assertEquals((10_000 * 4 + 1_000 * 20) / 1_000_000d, prices.get("anthropic|claude-opus-5-5").cost(10_000, 1_000), 1e-12);
        assertEquals((10_000 * 2 + 1_000 * 10) / 1_000_000d, prices.get("anthropic|claude-sonnet-5-5").cost(10_000, 1_000), 1e-12);
    }

    @Test
    void adjudicationOverridesOnlyTheStoriesItNames() throws Exception {
        VerdictEval eval = new VerdictEval(Path.of("..").toAbsolutePath().normalize());
        List<VerdictMetrics.Reference> v1 = eval.references(false);
        List<VerdictMetrics.Reference> v2 = eval.references();
        assertEquals(v1.size(), v2.size());
        List<String> changed = new java.util.ArrayList<>();
        for (int i = 0; i < v1.size(); i++) if (!v1.get(i).equals(v2.get(i))) changed.add(v2.get(i).id());
        assertEquals(eval.adjudication().stream().map(r -> r.get(0)).sorted().toList(), changed);
    }

    @Test
    void holdoutIsSeparateAndNeverAdjudicated() throws Exception {
        VerdictEval eval = new VerdictEval(Path.of("..").toAbsolutePath().normalize());
        List<String> holdout = eval.holdoutIds();
        List<String> labelled = new java.util.ArrayList<>();
        for (VerdictEval.ControlSet c : eval.controlSets()) {
            List<String> ids = eval.controlIds(c);
            if (!eval.unlabeled(c)) assertEquals(ids, eval.controlReferences(c).stream().map(VerdictMetrics.Reference::id).toList());
            labelled.addAll(ids);
        }
        assertEquals(holdout, labelled);
        assertTrue(eval.references().stream().noneMatch(r -> holdout.contains(r.id())), "the 50 reference labels exclude the holdout");
        assertTrue(eval.adjudication().stream().noneMatch(r -> holdout.contains(r.get(0))), "the holdout is never adjudicated");
        List<String> all = new java.util.ArrayList<>();
        eval.stories(all);
        assertEquals(eval.references().size() + holdout.size(), all.size(), "run measures both sets");
    }

    @Test
    void dailyCapIs100ForAnthropicAnd40Otherwise() {
        assertEquals(100, VerdictEval.defaultCap("anthropic"));
        assertEquals(40, VerdictEval.defaultCap("google"));
        assertEquals(40, VerdictEval.defaultCap("openai-compatible"));
    }

    @Test
    void callFarBeyondTheTimeoutIsSuspendedNotAProviderTimeout() {
        java.time.Duration timeout = java.time.Duration.ofSeconds(90);
        assertFalse(VerdictEval.suspended(java.time.Duration.ofSeconds(91), timeout));
        assertFalse(VerdictEval.suspended(java.time.Duration.ofSeconds(120), timeout));
        assertTrue(VerdictEval.suspended(java.time.Duration.ofSeconds(327), timeout));
    }
}
