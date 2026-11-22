// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.adapters.out.jobs.InMemoryJobStore;
import fr.heliocraft.refinr.application.port.in.AnalysisJobs.CancelResult;
import fr.heliocraft.refinr.application.port.in.AnalysisJobs.RejectedException;
import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.DocumentNotFoundException;
import fr.heliocraft.refinr.application.port.in.DocumentSourceUnavailableException;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.JobView.Failure.Kind;
import fr.heliocraft.refinr.application.port.in.JobView.Status;
import fr.heliocraft.refinr.application.port.in.JobView.Step;
import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelOverloadedException;
import fr.heliocraft.refinr.application.port.in.ModelRateLimitedException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(OutputCaptureExtension.class)
class AnalysisJobServiceTest {

    private static final int MAX_CONTENT_CHARS = 1000;
    private static final String TEXT = "Le magasinier saisit chaque entree de stock avec la reference et la quantite.";

    /** Comportement d'une analyse simulee : recoit le suivi des etapes, renvoie le resultat ou leve une exception. */
    @FunctionalInterface
    interface Behaviour {
        Object run(AnalysisProgress progress) throws Exception;
    }

    /** Apercu et analyse simules : chaque appel consomme le comportement suivant (le dernier est repete). */
    static final class FakeAnalyses implements PreviewBacklog, AnalyzeDocument {
        final List<Behaviour> behaviours = new ArrayList<>();
        final AtomicInteger calls = new AtomicInteger();

        FakeAnalyses then(Behaviour behaviour) {
            behaviours.add(behaviour);
            return this;
        }

        @SuppressWarnings("unchecked")
        private <T> T next(AnalysisProgress progress) {
            int call = calls.getAndIncrement();
            Behaviour behaviour = behaviours.get(Math.min(call, behaviours.size() - 1));
            try {
                return (T) behaviour.run(progress);
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        @Override
        public List<Epic> preview(String documentId, Language language) {
            throw new UnsupportedOperationException("jobs pass a progress");
        }

        @Override
        public List<Epic> previewText(String title, String content, Language language) {
            throw new UnsupportedOperationException("jobs pass a progress");
        }

        @Override
        public List<Epic> preview(String documentId, Language language, AnalysisProgress progress) {
            return next(progress);
        }

        @Override
        public List<Epic> previewText(String title, String content, Language language, AnalysisProgress progress) {
            return next(progress);
        }

        @Override
        public BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language) {
            throw new UnsupportedOperationException("jobs pass a progress");
        }

        @Override
        public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project, Language language) {
            throw new UnsupportedOperationException("jobs pass a progress");
        }

        @Override
        public BacklogCreationResult analyzeAndCreate(String documentId, String project, Language language,
                                                      AnalysisProgress progress) {
            return next(progress);
        }

        @Override
        public BacklogCreationResult analyzeTextAndCreate(String title, String content, String project,
                                                          Language language, AnalysisProgress progress) {
            return next(progress);
        }
    }

    private final List<AnalysisJobService> services = new ArrayList<>();
    private InMemoryJobStore<JobView> store;
    private int maxRetained = 100;

    @AfterEach
    void closeServices() {
        services.forEach(AnalysisJobService::close);
    }

    private AnalysisJobService service(FakeAnalyses analyses, int maxConcurrent, int maxQueued, Duration maxDuration,
                                       Duration shutdownGrace) {
        DocumentSourcePort source = mock(DocumentSourcePort.class);
        when(source.acceptsDocumentId(anyString())).thenAnswer(call -> !"bad id".equals(call.getArgument(0)));
        store = new InMemoryJobStore<>(maxRetained, Duration.ofHours(24), Clock.systemUTC());
        AnalysisJobService service = new AnalysisJobService(analyses, analyses, source, MAX_CONTENT_CHARS, store,
                new AnalysisJobService.Settings(maxConcurrent, maxQueued, maxDuration, shutdownGrace), Clock.systemUTC());
        services.add(service);
        return service;
    }

    private AnalysisJobService service(FakeAnalyses analyses) {
        return service(analyses, 2, 20, Duration.ofMinutes(5), Duration.ofSeconds(5));
    }

    private static List<Epic> epics(String name) {
        Epic epic = new Epic();
        epic.setName(name);
        return List.of(epic);
    }

    private static BacklogCreationResult creation() {
        return new BacklogCreationResult(epics("E"), List.of(), List.of());
    }

    private static void await(BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                fail("condition not reached within 5 s");
            }
            Thread.sleep(5);
        }
    }

    private JobView job(AnalysisJobService service, String id) {
        return service.find(id).orElseThrow();
    }

    private void awaitStatus(AnalysisJobService service, String id, Status status) throws InterruptedException {
        await(() -> service.find(id).map(job -> job.status() == status).orElse(false));
    }

    @Test
    @DisplayName("Full life cycle: queued, running with its steps, then succeeded with the result and the dates")
    void lifeCycle() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        List<Epic> result = epics("Stocks");
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            progress.step(Step.READING_DOCUMENT);
            progress.step(Step.GENERATING);
            release.await();
            return result;
        }));

        JobView submitted = service.submitPreview("DOC-1", null);

        assertEquals(Status.QUEUED, submitted.status());
        assertEquals(JobView.Type.ANALYZE_PREVIEW, submitted.type());
        assertEquals("DOC-1", submitted.documentId());
        assertNotNull(submitted.createdAt());
        assertTrue(submitted.id().matches("[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
        await(() -> job(service, submitted.id()).step() == Step.GENERATING);
        JobView running = job(service, submitted.id());
        assertEquals(Status.RUNNING, running.status());
        assertNotNull(running.startedAt());
        assertNull(running.finishedAt());

        release.countDown();
        awaitStatus(service, submitted.id(), Status.SUCCEEDED);
        JobView done = job(service, submitted.id());
        assertEquals(result.getFirst().getName(), done.preview().getFirst().getName());
        assertNull(done.failure());
        assertFalse(done.finishedAt().isBefore(done.startedAt()));
    }

    @Test
    @DisplayName("An analyze-and-create job keeps the creation result (complete or partial)")
    void creationResult() throws Exception {
        BacklogCreationResult result = creation();
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> result));

        JobView submitted = service.submitAnalyzeTextAndCreate("Title", TEXT, "PROJ", Language.of("fr"));

        awaitStatus(service, submitted.id(), Status.SUCCEEDED);
        assertSame(result, job(service, submitted.id()).creation());
        assertNull(submitted.documentId());
    }

    static Stream<Arguments> failures() {
        Throwable cause = new IllegalStateException("provider detail");
        return Stream.of(
                Arguments.of(new ModelRateLimitedException(cause, Duration.ofSeconds(42)), Kind.MODEL_RATE_LIMITED, null, 42L),
                Arguments.of(new ModelRateLimitedException(cause, null), Kind.MODEL_RATE_LIMITED, null, null),
                Arguments.of(new ModelOverloadedException(cause, Duration.ofSeconds(7)), Kind.MODEL_OVERLOADED, null, 7L),
                Arguments.of(new ModelUnavailableException(cause), Kind.MODEL_UNAVAILABLE, null, null),
                Arguments.of(new ModelOutputException(true, cause), Kind.MODEL_OUTPUT_TRUNCATED, null, null),
                Arguments.of(new ModelOutputException(false, cause), Kind.MODEL_OUTPUT_INVALID, null, null),
                Arguments.of(new DocumentNotFoundException("Document not found: DOC-1", cause), Kind.DOCUMENT_NOT_FOUND,
                        "Document not found: DOC-1", null),
                Arguments.of(new DocumentSourceUnavailableException("The document source is unavailable", cause),
                        Kind.SOURCE_UNAVAILABLE, "The document source is unavailable", null),
                Arguments.of(new InvalidRequestException("The document is empty or contains no text to analyze"),
                        Kind.INVALID_REQUEST, "The document is empty or contains no text to analyze", null),
                Arguments.of(new IllegalStateException("raw detail https://internal.example.net"), Kind.UNEXPECTED, null, null));
    }

    @ParameterizedTest
    @MethodSource("failures")
    @DisplayName("A provider or source failure ends the job as failed, with its kind and delay but never a raw message")
    void failureKinds(RuntimeException error, Kind kind, String message, Long retryAfterSeconds) throws Exception {
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            throw error;
        }));

        JobView submitted = service.submitPreview("DOC-1", null);

        awaitStatus(service, submitted.id(), Status.FAILED);
        JobView.Failure failure = job(service, submitted.id()).failure();
        assertEquals(kind, failure.kind());
        assertEquals(message, failure.message());
        assertEquals(retryAfterSeconds, failure.retryAfterDelay().map(Duration::toSeconds).orElse(null));
    }

    @Test
    @DisplayName("The request is checked at submission like the synchronous endpoint: no job for a bad id, an empty or a too long text")
    void requestIsCheckedAtSubmission() {
        FakeAnalyses analyses = new FakeAnalyses().then(progress -> epics("E"));
        AnalysisJobService service = service(analyses);

        assertEquals(DocumentAnalysis.INVALID_ID,
                assertThrows(InvalidRequestException.class, () -> service.submitPreview("bad id", null)).getMessage());
        assertEquals(DocumentAnalysis.MISSING_ID,
                assertThrows(InvalidRequestException.class, () -> service.submitAnalyzeAndCreate(" ", null, null)).getMessage());
        assertEquals(DocumentAnalysis.EMPTY_DOCUMENT,
                assertThrows(InvalidRequestException.class, () -> service.submitPreviewText("T", "  ", null)).getMessage());
        assertThrows(ContentTooLargeException.class,
                () -> service.submitAnalyzeTextAndCreate(null, "x".repeat(MAX_CONTENT_CHARS + 1), null, null));
        assertEquals(0, analyses.calls.get());
        assertEquals(0, store.size());
    }

    @Test
    @DisplayName("max-concurrent: a second job waits while the first runs, then runs")
    void concurrencyLimit() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AnalysisJobService service = service(new FakeAnalyses()
                .then(progress -> {
                    release.await();
                    return epics("A");
                })
                .then(progress -> epics("B")), 1, 5, Duration.ofMinutes(5), Duration.ofSeconds(5));

        JobView first = service.submitPreview("DOC-1", null);
        awaitStatus(service, first.id(), Status.RUNNING);
        JobView second = service.submitPreview("DOC-2", null);
        Thread.sleep(100);
        assertEquals(Status.QUEUED, job(service, second.id()).status());

        release.countDown();
        awaitStatus(service, second.id(), Status.SUCCEEDED);
        assertEquals(Status.SUCCEEDED, job(service, first.id()).status());
    }

    @Test
    @DisplayName("max-queued: beyond the queue, the submission is refused and no job is kept")
    void queueFull() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            release.await();
            return epics("A");
        }), 1, 1, Duration.ofMinutes(5), Duration.ofSeconds(5));
        JobView running = service.submitPreview("DOC-1", null);
        awaitStatus(service, running.id(), Status.RUNNING);
        service.submitPreview("DOC-2", null);

        RejectedException refused = assertThrows(RejectedException.class, () -> service.submitPreview("DOC-3", null));

        assertEquals(AnalysisJobService.QUEUE_FULL, refused.getMessage());
        assertEquals(2, store.size());
        release.countDown();
    }

    @Test
    @DisplayName("A submission refused because the queue is full never evicts a finished job from a full store")
    void refusalKeepsFinishedJobs() throws Exception {
        maxRetained = 3;
        CountDownLatch release = new CountDownLatch(1);
        AnalysisJobService service = service(new FakeAnalyses()
                .then(progress -> epics("done"))
                .then(progress -> {
                    release.await();
                    return epics("A");
                }), 1, 1, Duration.ofMinutes(5), Duration.ofSeconds(5));
        JobView done = service.submitPreview("DOC-0", null);
        awaitStatus(service, done.id(), Status.SUCCEEDED);
        JobView running = service.submitPreview("DOC-1", null);
        awaitStatus(service, running.id(), Status.RUNNING);
        service.submitPreview("DOC-2", null);

        assertThrows(RejectedException.class, () -> service.submitPreview("DOC-3", null));

        assertTrue(service.find(done.id()).isPresent(), "the finished job is still there");
        assertEquals(3, store.size());
        release.countDown();
    }

    @Test
    @DisplayName("max-duration before the creation: the job fails (timeout), is interrupted and never creates issues")
    void timeoutBeforeCreation() throws Exception {
        AtomicBoolean interrupted = new AtomicBoolean();
        AtomicBoolean created = new AtomicBoolean();
        CountDownLatch never = new CountDownLatch(1);
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            progress.step(Step.GENERATING);
            try {
                never.await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                Thread.currentThread().interrupt();
            }
            progress.step(Step.CREATING_ISSUES);
            created.set(true);
            return creation();
        }), 1, 5, Duration.ofMillis(200), Duration.ofSeconds(5));

        JobView submitted = service.submitAnalyzeAndCreate("DOC-1", null, null);

        awaitStatus(service, submitted.id(), Status.FAILED);
        assertEquals(Kind.TIMEOUT, job(service, submitted.id()).failure().kind());
        await(interrupted::get);
        Thread.sleep(50);
        assertFalse(created.get());
    }

    @Test
    @DisplayName("max-duration never interrupts a creation that has started: the job goes to the end")
    void timeoutDuringCreation() throws Exception {
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            progress.step(Step.CREATING_ISSUES);
            Thread.sleep(400);
            return creation();
        }), 1, 5, Duration.ofMillis(100), Duration.ofSeconds(5));

        JobView submitted = service.submitAnalyzeAndCreate("DOC-1", null, null);

        awaitStatus(service, submitted.id(), Status.SUCCEEDED);
        assertNull(job(service, submitted.id()).failure());
    }

    @Test
    @DisplayName("Cancel: a queued job is removed and never runs; a running job is not cancelled; a finished job is removed")
    void cancel() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        FakeAnalyses analyses = new FakeAnalyses().then(progress -> {
            release.await();
            return epics("A");
        });
        AnalysisJobService service = service(analyses, 1, 5, Duration.ofMinutes(5), Duration.ofSeconds(5));
        JobView running = service.submitPreview("DOC-1", null);
        awaitStatus(service, running.id(), Status.RUNNING);
        JobView queued = service.submitPreview("DOC-2", null);

        assertEquals(CancelResult.REMOVED, service.cancel(queued.id()));
        assertTrue(service.find(queued.id()).isEmpty());
        assertEquals(CancelResult.RUNNING, service.cancel(running.id()));

        release.countDown();
        awaitStatus(service, running.id(), Status.SUCCEEDED);
        Thread.sleep(100);
        assertEquals(1, analyses.calls.get());
        assertEquals(CancelResult.REMOVED, service.cancel(running.id()));
        assertEquals(CancelResult.NOT_FOUND, service.cancel(running.id()));
    }

    @Test
    @DisplayName("Shutdown: a job creating issues finishes, a job generating is interrupted, new submissions are refused")
    void gracefulShutdown() throws Exception {
        CountDownLatch creating = new CountDownLatch(1);
        CountDownLatch finishCreation = new CountDownLatch(1);
        CountDownLatch generating = new CountDownLatch(1);
        AtomicBoolean generationInterrupted = new AtomicBoolean();
        FakeAnalyses analyses = new FakeAnalyses();
        AnalysisJobService service = service(analyses, 2, 5, Duration.ofMinutes(5), Duration.ofSeconds(5));
        analyses.then(progress -> {
            progress.step(Step.CREATING_ISSUES);
            creating.countDown();
            // La creation ne reagit pas a l'interruption : elle doit finir dans le delai de grace
            finishCreation.await(5, TimeUnit.SECONDS);
            return creation();
        });
        JobView creation = service.submitAnalyzeAndCreate("DOC-1", null, null);
        assertTrue(creating.await(5, TimeUnit.SECONDS));
        analyses.then(progress -> {
            progress.step(Step.GENERATING);
            generating.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                generationInterrupted.set(true);
                throw e;
            }
            return epics("never");
        });
        JobView generation = service.submitPreview("DOC-2", null);
        assertTrue(generating.await(5, TimeUnit.SECONDS));
        JobView queued = service.submitPreview("DOC-3", null);

        Thread closing = new Thread(service::close);
        closing.start();
        await(generationInterrupted::get);
        assertThrows(RejectedException.class, () -> service.submitPreview("DOC-4", null));
        assertTrue(closing.isAlive(), "the shutdown waits for the job creating issues");
        finishCreation.countDown();
        closing.join(5000);

        assertFalse(closing.isAlive());
        assertEquals(Status.SUCCEEDED, job(service, creation.id()).status());
        assertEquals(Kind.INTERRUPTED, job(service, generation.id()).failure().kind());
        assertEquals(Kind.INTERRUPTED, job(service, queued.id()).failure().kind());
        assertEquals(AnalysisJobService.SHUTTING_DOWN,
                assertThrows(RejectedException.class, () -> service.submitPreview("DOC-5", null)).getMessage());
    }

    @Test
    @DisplayName("Shutdown grace exceeded: a creation still running is interrupted")
    void shutdownGraceExceeded() throws Exception {
        CountDownLatch creating = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> {
            progress.step(Step.CREATING_ISSUES);
            creating.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.set(true);
                throw e;
            }
            return creation();
        }), 1, 5, Duration.ofMinutes(5), Duration.ofMillis(100));
        JobView submitted = service.submitAnalyzeAndCreate("DOC-1", null, null);
        assertTrue(creating.await(5, TimeUnit.SECONDS));

        service.close();

        await(interrupted::get);
        awaitStatus(service, submitted.id(), Status.FAILED);
    }

    @Test
    @DisplayName("Logs: never the document, the title, the result nor the full job id")
    void logsStaySafe(CapturedOutput output) throws Exception {
        AnalysisJobService service = service(new FakeAnalyses().then(progress -> epics("Result-marker-7c1e")));

        JobView submitted = service.submitPreviewText("Title-marker-3f9a", TEXT + " Content-marker-a51d", null);

        awaitStatus(service, submitted.id(), Status.SUCCEEDED);
        String logs = output.getAll();
        assertTrue(logs.contains("Job " + submitted.id().substring(0, 8)), logs);
        assertFalse(logs.contains(submitted.id()), logs);
        assertFalse(logs.contains("Title-marker-3f9a"), logs);
        assertFalse(logs.contains("Content-marker-a51d"), logs);
        assertFalse(logs.contains("Result-marker-7c1e"), logs);
    }

    @Test
    @DisplayName("Settings are validated: at least one job at a time and in the queue, positive duration, grace not negative")
    void settingsAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisJobService.Settings(0, 1, Duration.ofMinutes(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisJobService.Settings(1, 0, Duration.ofMinutes(1), Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisJobService.Settings(1, 1, Duration.ZERO, Duration.ZERO));
        assertThrows(IllegalArgumentException.class,
                () -> new AnalysisJobService.Settings(1, 1, Duration.ofMinutes(1), Duration.ofSeconds(-1)));
    }
}
