// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.application.port.out.TestCaseGeneratorPort;
import fr.heliocraft.refinr.domain.model.TestCase;
import fr.heliocraft.refinr.domain.service.TestCasePolicy;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Chaine C1 ({@code "pipeline": true}) : generation sans sous-taches, verdict du prompt 2 (toujours, celui que mesure
 * E0), puis sous-taches des seules stories pretes. Une story non prete n'a aucune sous-tache : elles viendront quand
 * elle deviendra prete ({@code story-tasks}, option {@code createTasks}). Une story prete dont les sous-taches n'ont pas
 * pu etre obtenues reste sans sous-taches (meme recours) ; si aucune ne peut l'etre, l'erreur du fournisseur remonte.
 * Chaine C2 : puis les cas de test des stories pretes (prompt 5), un cas invalide etant ecarte.
 */
public class BacklogPipeline {

    private static final Logger log = LoggerFactory.getLogger(BacklogPipeline.class);

    private final BacklogGeneratorPort generator;
    private final ReadinessAssessorPort assessor;
    private final TaskGeneratorPort taskGenerator;
    // Chaine C2 : cas de test des stories pretes (prompt 5), ou null (pas de cas de test)
    private final TestCaseGeneratorPort testCaseGenerator;

    public BacklogPipeline(BacklogGeneratorPort generator, ReadinessAssessorPort assessor, TaskGeneratorPort taskGenerator) {
        this(generator, assessor, taskGenerator, null);
    }

    public BacklogPipeline(BacklogGeneratorPort generator, ReadinessAssessorPort assessor, TaskGeneratorPort taskGenerator,
                           TestCaseGeneratorPort testCaseGenerator) {
        this.generator = generator;
        this.assessor = assessor;
        this.taskGenerator = taskGenerator;
        this.testCaseGenerator = testCaseGenerator;
    }

    /** Backlog qualifie du texte {@code content} (deja controle), dans la langue {@code language} deja resolue. */
    public List<Epic> run(String content, Language language, AnalysisProgress progress) {
        return run(content, language, progress, null);
    }

    /**
     * Meme chaine avec le contexte projet {@code context} (X1) donne aux prompts 1, 2, 4 et 5 ; null : exactement la
     * chaine sans contexte.
     */
    public List<Epic> run(String content, Language language, AnalysisProgress progress, String context) {
        boolean withContext = context != null && !context.isBlank();
        BacklogGeneratorPort generator = withContext ? this.generator.withContext(context) : this.generator;
        ReadinessAssessorPort assessor = withContext ? this.assessor.withContext(context) : this.assessor;
        TaskGeneratorPort taskGenerator = withContext ? this.taskGenerator.withContext(context) : this.taskGenerator;
        TestCaseGeneratorPort testCaseGenerator = withContext && this.testCaseGenerator != null
                ? this.testCaseGenerator.withContext(context) : this.testCaseGenerator;
        progress.step(JobView.Step.GENERATING);
        List<Epic> epics = LlmFailures.translate(() -> generator.generateStories(content, language));
        progress.step(JobView.Step.ASSESSING);
        StoryVerdicts.apply(epics, assessor, story -> language);
        List<Story> ready = new ArrayList<>();
        for (Epic epic : epics) {
            for (Story story : epic.getStories()) {
                story.setTasks(new ArrayList<>());
                story.setTestCases(new ArrayList<>());
                if (Boolean.TRUE.equals(story.getAiReady())) {
                    ready.add(story);
                }
            }
        }
        if (ready.isEmpty()) {
            return epics;
        }
        progress.step(JobView.Step.GENERATING_TASKS);
        List<List<Task>> tasks = LlmFailures.translate(() ->
                taskGenerator.generateTasks(ready, Collections.nCopies(ready.size(), null), language));
        int missing = 0;
        for (int i = 0; i < ready.size(); i++) {
            List<Task> found = i < tasks.size() ? tasks.get(i) : null;
            if (found == null) {
                missing++;
            } else {
                ready.get(i).setTasks(new ArrayList<>(found));
            }
        }
        if (missing > 0) {
            log.warn("Sub-tasks unavailable for {} of {} ready stories", missing, ready.size());
        }
        if (testCaseGenerator != null) {
            progress.step(JobView.Step.GENERATING_TEST_CASES);
            List<List<TestCase>> testCases;
            try {
                testCases = LlmFailures.translate(() -> testCaseGenerator.generateTestCases(ready, language));
            } catch (fr.heliocraft.refinr.application.port.in.ModelUnavailableException
                     | fr.heliocraft.refinr.application.port.in.ModelOutputException e) {
                // Mode degrade : le reste du backlog (verdicts, sous-taches) continue ; reprise par story-tasks
                log.warn("Test cases unavailable for the {} ready stories: {}", ready.size(), e.getClass().getSimpleName());
                for (Story story : ready) {
                    if (story.getAcceptanceCriteria() != null && !story.getAcceptanceCriteria().isEmpty()) {
                        story.setTestCasesError(TestCasePolicy.MODEL_UNAVAILABLE);
                    }
                }
                return epics;
            }
            int rejected = 0;
            for (int i = 0; i < ready.size(); i++) {
                Story story = ready.get(i);
                List<TestCase> found = i < testCases.size() ? testCases.get(i) : null;
                int criteria = story.getAcceptanceCriteria() != null ? story.getAcceptanceCriteria().size() : 0;
                if (criteria == 0) {
                    // Rien a couvrir : ni cas de test, ni avertissement
                    continue;
                }
                List<String> problems = found == null ? List.of("the model gave no test case")
                        : TestCasePolicy.coverageProblems(found, criteria);
                if (problems.isEmpty()) {
                    story.setTestCases(new ArrayList<>(TestCasePolicy.valid(found, criteria)));
                } else {
                    // Couverture non conforme apres la reparation : aucune section partielle, raison sur la story
                    rejected++;
                    story.setTestCasesError(TestCasePolicy.notWritten(problems));
                }
            }
            if (rejected > 0) {
                log.warn("Test cases not written for {} of {} ready stories (coverage)", rejected, ready.size());
            }
        }
        return epics;
    }
}
