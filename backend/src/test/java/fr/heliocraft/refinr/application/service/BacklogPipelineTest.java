// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.out.BacklogGeneratorPort;
import fr.heliocraft.refinr.application.port.out.LlmException;
import fr.heliocraft.refinr.application.port.out.ReadinessAssessorPort;
import fr.heliocraft.refinr.application.port.out.TaskGeneratorPort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import fr.heliocraft.refinr.domain.model.Readiness;
import fr.heliocraft.refinr.domain.model.Story;
import fr.heliocraft.refinr.domain.model.Task;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/** Chaine C1 : generation sans sous-taches, verdict du prompt 2 toujours, sous-taches des seules stories pretes. */
class BacklogPipelineTest {

    private static Story story(String title, boolean readyForTheGenerator) {
        Story story = new Story();
        story.setTitle(title);
        story.setAiReady(readyForTheGenerator);
        story.setAiReadyReason("generation verdict");
        return story;
    }

    private static Task task(String title) {
        Task task = new Task();
        task.setTitle(title);
        return task;
    }

    private static List<Epic> backlog(Story... stories) {
        Epic epic = new Epic();
        epic.setName("E");
        epic.setStories(new ArrayList<>(List.of(stories)));
        return new ArrayList<>(List.of(epic));
    }

    @Test
    @DisplayName("Prompt 2 verdict replaces the generation's; tasks generated for the ready stories only, steps in order")
    void tasksForReadyStoriesOnly() {
        BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
        ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);
        TaskGeneratorPort tasks = mock(TaskGeneratorPort.class);
        Story a = story("A", false), b = story("B", true);
        when(generator.generateStories("SPEC", Language.FR)).thenReturn(backlog(a, b));
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "clair"),
                new Readiness(false, "Quel format ?")));
        when(tasks.generateTasks(anyList(), anyList(), any())).thenReturn(List.of(List.of(task("A1"), task("A2"))));
        List<JobView.Step> steps = new ArrayList<>();
        AnalysisProgress progress = steps::add;

        List<Epic> epics = new BacklogPipeline(generator, assessor, tasks).run("SPEC", Language.FR, progress);

        assertSame(a, epics.getFirst().getStories().get(0));
        assertEquals(List.of("A1", "A2"), a.getTasks().stream().map(Task::getTitle).toList());
        assertTrue(b.getTasks().isEmpty(), "not ready: no task");
        assertEquals("Quel format ?", b.getAiReadyReason(), "prompt 2 verdict");
        verify(tasks).generateTasks(List.of(a), Arrays.asList((String) null), Language.FR);
        verify(generator, never()).generate(any(), any());
        assertEquals(List.of(JobView.Step.GENERATING, JobView.Step.ASSESSING, JobView.Step.GENERATING_TASKS), steps);
    }

    @Test
    @DisplayName("No ready story: no task call at all")
    void noReadyStoryNoCall() {
        BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
        ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);
        TaskGeneratorPort tasks = mock(TaskGeneratorPort.class);
        when(generator.generateStories(any(), any())).thenReturn(backlog(story("A", true)));
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(false, "Quel format ?")));

        new BacklogPipeline(generator, assessor, tasks).run("SPEC", Language.EN, AnalysisProgress.NONE);

        verifyNoInteractions(tasks);
    }

    @Test
    @DisplayName("Ready story without an answer for its tasks: no tasks (story-tasks later); all failed: provider error")
    void missingTasks() {
        BacklogGeneratorPort generator = mock(BacklogGeneratorPort.class);
        ReadinessAssessorPort assessor = mock(ReadinessAssessorPort.class);
        TaskGeneratorPort tasks = mock(TaskGeneratorPort.class);
        Story a = story("A", true), b = story("B", true);
        when(generator.generateStories(any(), any())).thenReturn(backlog(a, b));
        when(assessor.assess(anyList(), anyList())).thenReturn(List.of(new Readiness(true, "ok"), new Readiness(true, "ok")));
        when(tasks.generateTasks(anyList(), anyList(), any())).thenReturn(Arrays.asList(List.of(task("A1")), null));

        new BacklogPipeline(generator, assessor, tasks).run("SPEC", Language.EN, AnalysisProgress.NONE);
        assertEquals(1, a.getTasks().size());
        assertTrue(b.getTasks().isEmpty());

        when(tasks.generateTasks(anyList(), anyList(), any()))
                .thenThrow(new LlmException(LlmException.Reason.UNAVAILABLE, "down"));
        assertThrows(ModelUnavailableException.class,
                () -> new BacklogPipeline(generator, assessor, tasks).run("SPEC", Language.EN, AnalysisProgress.NONE));
    }
}
