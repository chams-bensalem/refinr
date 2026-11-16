// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.eval;

import fr.heliocraft.refinr.eval.VerdictMetrics.Confidence;
import fr.heliocraft.refinr.eval.VerdictMetrics.Label;
import fr.heliocraft.refinr.eval.VerdictMetrics.Majority;
import fr.heliocraft.refinr.eval.VerdictMetrics.Observation;
import fr.heliocraft.refinr.eval.VerdictMetrics.Reference;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VerdictMetricsTest {

    private static List<Observation> runs(String id, Boolean... verdicts) {
        List<Observation> obs = new ArrayList<>();
        for (int i = 0; i < verdicts.length; i++) obs.add(new Observation(i + 1, id, verdicts[i], "raison " + id));
        return obs;
    }

    private static VerdictMetrics metrics(List<Reference> refs, List<List<Observation>> obs) {
        return new VerdictMetrics(refs, obs.stream().flatMap(List::stream).toList());
    }

    @Test
    void majorityTieCountsAsAiReadyAndFallbacksAreIgnored() {
        VerdictMetrics m = metrics(
                List.of(new Reference("a", Label.NOT_READY, Confidence.SURE, "flou"),
                        new Reference("b", Label.READY, Confidence.SURE, "complet"),
                        new Reference("c", Label.READY, Confidence.HESITANT, "presque")),
                List.of(runs("a", true, false, null, null, null), runs("b", true, true, true, false, true), runs("c", null, null)));
        assertEquals(Majority.AI_READY, m.results().get(0).majority());
        assertEquals(Majority.AI_READY, m.results().get(1).majority());
        assertEquals(Majority.NONE, m.results().get(2).majority());
        assertEquals(1, m.rates(Confidence.SURE).falseAiReady());
        assertEquals(1, m.rates(Confidence.HESITANT).none());
    }

    @Test
    void blockingThresholdUsesSureLabelsOnly() {
        List<Reference> refs = List.of(new Reference("a", Label.NOT_READY, Confidence.SURE, "flou"),
                new Reference("b", Label.NOT_READY, Confidence.HESITANT, "limite"),
                new Reference("c", Label.READY, Confidence.SURE, "complet"));
        // b (hesitant) jugee prete 5 fois sur 5 : non bloquant ; a jugee prete 1 fois sur 5 : toleree
        VerdictMetrics ok = metrics(refs, List.of(runs("a", true, false, false, false, false),
                runs("b", true, true, true, true, true), runs("c", true, true, true, true, true)));
        assertTrue(ok.acceptance().blockingPassed());
        assertEquals(1, ok.rates(Confidence.HESITANT).falseAiReady());
        // a jugee prete 2 fois sur 5 : bloquant, meme si la majorite reste correcte
        VerdictMetrics ko = metrics(refs, List.of(runs("a", true, true, false, false, false),
                runs("b", false, false, false, false, false), runs("c", true, true, true, true, true)));
        assertTrue(ko.acceptance().zeroFalseAiReadySure());
        assertEquals(List.of("a"), ko.acceptance().readyTooOftenSure());
        assertFalse(ko.acceptance().blockingPassed());
    }

    @Test
    void stabilityFalseValidationAndConfusion() {
        List<Reference> refs = List.of(new Reference("a", Label.READY, Confidence.SURE, "x"),
                new Reference("b", Label.READY, Confidence.SURE, "y"),
                new Reference("c", Label.NOT_READY, Confidence.SURE, "z"),
                new Reference("d", Label.READY, Confidence.SURE, "w"));
        VerdictMetrics m = metrics(refs, List.of(runs("a", false, false, false), runs("b", true, false, true),
                runs("c", false, false, false), runs("d", true, true, true)));
        assertEquals(3, m.runs());
        assertEquals(0.75, m.stability(null));
        assertEquals(1, m.rates(null).falseValidation());
        assertEquals(1.0 / 3, m.rates(null).falseValidationRate());
        assertEquals(0.75, m.rates(null).agreement());
        assertFalse(m.acceptance().falseValidationOk());
        assertFalse(m.acceptance().stabilityOk());
        int[][] c = m.confusion(null);
        assertEquals(2, c[0][0]);
        assertEquals(1, c[0][1]);
        assertEquals(1, c[1][1]);
        assertTrue(m.markdown().contains("| a | ready | sure | 0 / 3 / 0 | validation-necessaire ✗"));
    }

    @Test
    void falseValidationThresholdUsesReadySureOnly() {
        List<Reference> refs = List.of(new Reference("a", Label.READY, Confidence.SURE, "x"),
                new Reference("b", Label.READY, Confidence.SURE, "y"),
                new Reference("c", Label.READY, Confidence.SURE, "z"),
                new Reference("d", Label.READY, Confidence.SURE, "w"),
                new Reference("e", Label.READY, Confidence.HESITANT, "v"),
                new Reference("f", Label.READY, Confidence.HESITANT, "u"));
        // 1 sur 4 en sure (25 %) : OK ; les 2 hesitant rejetees ne comptent pas (3 sur 6 toutes etiquettes)
        VerdictMetrics m = metrics(refs, List.of(runs("a", false), runs("b", true), runs("c", true), runs("d", true),
                runs("e", false), runs("f", false)));
        assertEquals(0.5, m.rates(null).falseValidationRate());
        assertTrue(m.acceptance().falseValidationOk());
        assertTrue(m.acceptanceMarkdown().contains("| faux `validation-necessaire`, étiquettes `ready` / `hesitant` (indicatif) | 2 sur 2"));
    }

    @Test
    void unmeasuredStoriesAreNotCountedAsFallbacks() {
        List<Reference> refs = List.of(new Reference("a", Label.READY, Confidence.SURE, "x"),
                new Reference("b", Label.NOT_READY, Confidence.SURE, "y"),
                new Reference("c", Label.READY, Confidence.SURE, "z"));
        VerdictMetrics m = metrics(refs, List.of(runs("a", true), runs("b", (Boolean) null)));
        assertEquals(1, m.rates(null).none());
        assertEquals(1, m.rates(null).unmeasured());
        assertEquals(List.of("c"), m.unmeasured());
        String md = m.markdown();
        assertTrue(md.contains("**Non mesurées** (mesure interrompue, à reprendre) : c"));
        assertTrue(md.contains("| b | not-ready | sure | 0 / 0 / 1 | sans verdict (repli) ✗"));
        assertFalse(md.contains("| c | ready"));
    }
}
