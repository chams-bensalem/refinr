// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.domain.service;

import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.TestCase;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Regles des cas de test (chaine C2) : critere existant ({@code AC1}…{@code ACn}), {@code kind} parmi nominal, error,
 * edge, given / when / then remplis, longueurs bornees. Les messages ne recopient jamais le contenu.
 */
public final class TestCasePolicy {

    public static final Set<String> KINDS = Set.of("nominal", "error", "edge");
    public static final int MAX_TITLE = 200;
    public static final int MAX_STEP = 1_000;
    /** Au plus 3 cas par critere (prompt 5) ; sert a dimensionner le schema (10 criteres). */
    public static final int MAX_PER_CRITERION = 3;

    private static final Pattern CRITERION = Pattern.compile("AC([1-9][0-9]{0,2})");

    private TestCasePolicy() {}

    /** Premiere violation d'un cas de test de {@code story} (position comprise), ou null si tout est valide. */
    public static String violation(Story story, String storyPosition) {
        List<TestCase> testCases = story.getTestCases();
        if (testCases == null || testCases.isEmpty()) {
            return null;
        }
        int criteria = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria().size() : 0;
        for (int i = 0; i < testCases.size(); i++) {
            String where = "testCases[" + i + "] of story " + storyPosition;
            String problem = violation(testCases.get(i), criteria);
            if (problem != null) {
                return where + ": " + problem;
            }
        }
        return null;
    }

    /** Violation d'un cas de test pour une story de {@code criteria} criteres, ou null. */
    public static String violation(TestCase testCase, int criteria) {
        if (testCase == null) {
            return "must not be null";
        }
        Matcher m = CRITERION.matcher(testCase.criterion() != null ? testCase.criterion().strip() : "");
        if (!m.matches() || Integer.parseInt(m.group(1)) > criteria) {
            return "criterion must name an existing acceptance criterion (AC1 to AC" + criteria + ")";
        }
        if (testCase.kind() == null || !KINDS.contains(testCase.kind().strip())) {
            return "kind must be nominal, error or edge";
        }
        if (blank(testCase.title()) || blank(testCase.given()) || blank(testCase.when()) || blank(testCase.then())) {
            return "title, given, when and then are required";
        }
        if (testCase.title().length() > MAX_TITLE) {
            return "title is longer than " + MAX_TITLE + " characters";
        }
        for (String step : List.of(testCase.given(), testCase.when(), testCase.then())) {
            if (step.length() > MAX_STEP) {
                return "given, when and then are limited to " + MAX_STEP + " characters each";
            }
        }
        return null;
    }

    /**
     * Problemes de couverture des cas de test d'une story de {@code criteria} criteres : chaque critere a 1 a 3 cas,
     * chaque cas designe un critere existant et a ses champs valides. Liste vide si tout est conforme ; messages en
     * anglais, envoyes au modele pour la reparation (par exemple « AC2 has no test case »), jamais de contenu.
     */
    public static List<String> coverageProblems(List<TestCase> testCases, int criteria) {
        List<String> problems = new ArrayList<>();
        int[] counts = new int[criteria + 1];
        List<TestCase> cases = testCases != null ? testCases : List.of();
        for (int i = 0; i < cases.size(); i++) {
            TestCase testCase = cases.get(i);
            String violation = violation(testCase, criteria);
            if (violation != null) {
                problems.add("test case " + (i + 1) + ": " + violation);
                continue;
            }
            Matcher m = CRITERION.matcher(testCase.criterion().strip());
            if (m.matches()) {
                counts[Integer.parseInt(m.group(1))]++;
            }
        }
        for (int c = 1; c <= criteria; c++) {
            if (counts[c] == 0) {
                problems.add("AC" + c + " has no test case");
            } else if (counts[c] > MAX_PER_CRITERION) {
                problems.add("AC" + c + " has " + counts[c] + " test cases (at most " + MAX_PER_CRITERION + ")");
            }
        }
        return problems;
    }

    /** Prompt 5 indisponible pour tout le lot (chaine en mode degrade) : raison posee sur chaque story prete. */
    public static final String MODEL_UNAVAILABLE = "test cases not generated: the model could not answer";

    /** Raison ecrite quand les cas de test d'une story ne le sont pas (aucune section partielle). */
    public static String notWritten(List<String> problems) {
        return "test cases not written: " + String.join("; ", problems);
    }

    /** Cas valides seulement (sortie du modele : un cas invalide est ecarte, jamais corrige). */
    public static List<TestCase> valid(List<TestCase> testCases, int criteria) {
        List<TestCase> kept = new ArrayList<>();
        for (TestCase testCase : testCases != null ? testCases : List.<TestCase>of()) {
            if (violation(testCase, criteria) == null) {
                kept.add(new TestCase(testCase.title().strip(), testCase.criterion().strip(), testCase.kind().strip(),
                        testCase.given().strip(), testCase.when().strip(), testCase.then().strip()));
            }
        }
        return kept;
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
