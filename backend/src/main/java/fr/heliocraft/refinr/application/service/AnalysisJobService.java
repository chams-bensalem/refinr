// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.service;

import fr.heliocraft.refinr.application.port.in.AnalysisJobs;
import fr.heliocraft.refinr.application.port.in.AnalysisProgress;
import fr.heliocraft.refinr.application.port.in.AnalyzeDocument;
import fr.heliocraft.refinr.application.port.in.BacklogCreationResult;
import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.DocumentNotFoundException;
import fr.heliocraft.refinr.application.port.in.DocumentSourceUnavailableException;
import fr.heliocraft.refinr.application.port.in.FeatureLevelMissingException;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.application.port.in.JobView.Failure;
import fr.heliocraft.refinr.application.port.in.JobView.Failure.Kind;
import fr.heliocraft.refinr.application.port.in.ModelOutputException;
import fr.heliocraft.refinr.application.port.in.ModelOverloadedException;
import fr.heliocraft.refinr.application.port.in.ModelRateLimitedException;
import fr.heliocraft.refinr.application.port.in.ModelUnavailableException;
import fr.heliocraft.refinr.application.port.in.PreviewBacklog;
import fr.heliocraft.refinr.application.port.out.DocumentSourcePort;
import fr.heliocraft.refinr.application.port.out.JobStorePort;
import fr.heliocraft.refinr.domain.model.Epic;
import fr.heliocraft.refinr.domain.model.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.UnaryOperator;

/**
 * Analyses en arriere-plan : au plus {@code maxConcurrent} jobs a la fois, {@code maxQueued} en attente (au-dela :
 * refus), chacun borne a {@code maxDuration} avant la creation des tickets (une creation commencee va toujours au
 * bout). Les nouveaux essais du fournisseur et le decoupage des lots tronques restent ceux des cas d'usage ; un job
 * n'en ajoute aucun. Arret progressif ({@link #beginShutdown()}, puis {@link #close()}) : plus de soumission, les
 * jobs en attente et ceux qui n'ont pas commence la creation sont interrompus, ceux qui creent les tickets ont
 * {@code shutdownGrace} pour finir, compte depuis le debut de l'arret.
 * <p>
 * Journaux : type, etat, etape, duree et les 8 premiers caracteres de l'identifiant ; jamais le contenu du document,
 * le resultat ni l'identifiant complet (sans cle d'API, il suffit a lire le resultat).
 */
public class AnalysisJobService implements AnalysisJobs, AutoCloseable {

    static final String QUEUE_FULL = "Too many analysis jobs waiting, try again later";
    static final String SHUTTING_DOWN = "The server is shutting down, try again later";
    private static final String CORRELATION_ID = "correlationId";
    private static final long EVICTION_PERIOD_SECONDS = 60;

    private static final Logger log = LoggerFactory.getLogger(AnalysisJobService.class);

    /** Limites d'execution (proprietes {@code refinr.jobs.*}). */
    public record Settings(int maxConcurrent, int maxQueued, Duration maxDuration, Duration shutdownGrace) {
        public Settings {
            if (maxConcurrent < 1) {
                throw new IllegalArgumentException("refinr.jobs.max-concurrent must be at least 1, got " + maxConcurrent);
            }
            if (maxQueued < 1) {
                throw new IllegalArgumentException("refinr.jobs.max-queued must be at least 1, got " + maxQueued);
            }
            if (maxDuration == null || maxDuration.isNegative() || maxDuration.isZero()) {
                throw new IllegalArgumentException("refinr.jobs.max-duration must be positive, got " + maxDuration);
            }
            if (shutdownGrace == null || shutdownGrace.isNegative()) {
                throw new IllegalArgumentException("refinr.jobs.shutdown-grace must not be negative, got " + shutdownGrace);
            }
        }
    }

    private final PreviewBacklog previewBacklog;
    private final AnalyzeDocument analyzeDocument;
    private final DocumentSourcePort documentSource;
    private final int maxContentChars;
    private final JobStorePort<JobView> store;
    private final Settings settings;
    private final Clock clock;
    // Avertissements de l'apercu (niveau Feature), calcules a la fin du job ; null : aucun
    private final DescribeTracker describeTracker;
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService watchdog;
    private final Map<String, Job> active = new ConcurrentHashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private volatile long shutdownStartNanos;
    private volatile boolean accepting = true;

    public AnalysisJobService(PreviewBacklog previewBacklog, AnalyzeDocument analyzeDocument,
                              DocumentSourcePort documentSource, int maxContentChars, JobStorePort<JobView> store,
                              Settings settings, Clock clock) {
        this(previewBacklog, analyzeDocument, documentSource, maxContentChars, store, settings, clock, null);
    }

    public AnalysisJobService(PreviewBacklog previewBacklog, AnalyzeDocument analyzeDocument,
                              DocumentSourcePort documentSource, int maxContentChars, JobStorePort<JobView> store,
                              Settings settings, Clock clock, DescribeTracker describeTracker) {
        this.describeTracker = describeTracker;
        this.previewBacklog = previewBacklog;
        this.analyzeDocument = analyzeDocument;
        this.documentSource = documentSource;
        this.maxContentChars = DocumentAnalysis.requirePositiveLimit(maxContentChars);
        this.store = store;
        this.settings = settings;
        this.clock = clock;
        this.workers = new ThreadPoolExecutor(settings.maxConcurrent(), settings.maxConcurrent(), 0L,
                TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(settings.maxQueued()), threads("refinr-job-"),
                new ThreadPoolExecutor.AbortPolicy());
        this.watchdog = Executors.newSingleThreadScheduledExecutor(threads("refinr-job-watchdog-"));
        watchdog.scheduleWithFixedDelay(this::evictExpired, EVICTION_PERIOD_SECONDS, EVICTION_PERIOD_SECONDS,
                TimeUnit.SECONDS);
    }

    @Override
    public JobView submitPreview(String documentId, String project, Language language, boolean pipeline) {
        DocumentAnalysis.requireValidId(documentSource, documentId);
        return submit(JobView.Type.ANALYZE_PREVIEW, documentId, pipeline,
                progress -> new Result(previewBacklog.preview(documentId, language, pipeline, progress), null, project));
    }

    @Override
    public JobView submitPreviewText(String title, String content, String project, Language language,
                                     boolean pipeline) {
        // Memes refus que l'endpoint synchrone (413, puis 400), avant de creer le job
        DocumentAnalysis.providedText(title, content, maxContentChars);
        return submit(JobView.Type.ANALYZE_PREVIEW, null, pipeline, progress ->
                new Result(previewBacklog.previewText(title, content, language, pipeline, progress), null, project));
    }

    @Override
    public JobView submitAnalyzeAndCreate(String documentId, String project, Language language, boolean pipeline) {
        DocumentAnalysis.requireValidId(documentSource, documentId);
        return submit(JobView.Type.ANALYZE_AND_CREATE, documentId, pipeline, progress ->
                new Result(null, analyzeDocument.analyzeAndCreate(documentId, project, language, pipeline, progress)));
    }

    @Override
    public JobView submitAnalyzeTextAndCreate(String title, String content, String project, Language language,
                                              boolean pipeline) {
        DocumentAnalysis.providedText(title, content, maxContentChars);
        return submit(JobView.Type.ANALYZE_AND_CREATE, null, pipeline, progress -> new Result(null,
                analyzeDocument.analyzeTextAndCreate(title, content, project, language, pipeline, progress)));
    }

    @Override
    public Optional<JobView> find(String id) {
        return store.find(id);
    }

    @Override
    public CancelResult cancel(String id) {
        Job job = active.get(id);
        if (job != null) {
            if (job.cancelQueued()) {
                workers.remove(job);
                active.remove(id);
                store.remove(id);
                log.info("Job {} cancelled before it started", shortId(id));
                return CancelResult.REMOVED;
            }
            if (!job.isFinished()) {
                return CancelResult.RUNNING;
            }
        }
        if (store.find(id).isEmpty()) {
            return CancelResult.NOT_FOUND;
        }
        store.remove(id);
        log.info("Job {} removed", shortId(id));
        return CancelResult.REMOVED;
    }

    /**
     * Debut de l'arret progressif, sans attendre : plus de soumission ; jobs en attente et jobs qui n'ont pas commence
     * la creation des tickets interrompus tout de suite ; les jobs en creation continuent. Appele des le debut de la
     * fermeture du contexte (avant l'arret du serveur web), pour que le delai {@code shutdownGrace} coure en meme temps
     * que celui des requetes HTTP en cours. Idempotent.
     */
    public void beginShutdown() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        shutdownStartNanos = System.nanoTime();
        accepting = false;
        List<Runnable> queued = new ArrayList<>();
        workers.getQueue().drainTo(queued);
        int interrupted = 0;
        for (Runnable task : queued) {
            if (task instanceof Job job) {
                active.remove(job.id);
                if (job.stopForShutdown()) {
                    interrupted++;
                }
            }
        }
        for (Job job : active.values()) {
            if (job.stopForShutdown()) {
                interrupted++;
            }
        }
        workers.shutdown();
        log.info("Analysis jobs stopping: {} interrupted, jobs creating issues get up to {} to finish", interrupted,
                settings.shutdownGrace());
    }

    /**
     * Fin de l'arret progressif : {@link #beginShutdown()} si ce n'est pas deja fait, puis attente des jobs en creation
     * jusqu'a {@code shutdownGrace} apres le debut de l'arret, puis interruption. Idempotent.
     */
    @Override
    public void close() {
        beginShutdown();
        if (!terminated.compareAndSet(false, true)) {
            return;
        }
        long remainingNanos = settings.shutdownGrace().toNanos() - (System.nanoTime() - shutdownStartNanos);
        try {
            if (!workers.awaitTermination(Math.max(0, remainingNanos), TimeUnit.NANOSECONDS)) {
                log.warn("Analysis jobs still creating issues after the shutdown grace period ({}): interrupted",
                        settings.shutdownGrace());
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            workers.shutdownNow();
            Thread.currentThread().interrupt();
        }
        watchdog.shutdownNow();
        log.info("Analysis jobs stopped");
    }

    private JobView submit(JobView.Type type, String documentId, boolean pipeline, Work work) {
        if (!accepting) {
            throw new RejectedException(SHUTTING_DOWN);
        }
        // File deja pleine : refus avant d'enregistrer le job, pour ne pas evincer pour rien un job termine du stockage
        if (workers.getQueue().remainingCapacity() == 0 && workers.getActiveCount() >= settings.maxConcurrent()) {
            log.warn("Job rejected: the queue is full");
            throw new RejectedException(QUEUE_FULL);
        }
        String id = UUID.randomUUID().toString();
        JobView view = JobView.queued(id, type, documentId, clock.instant(), pipeline);
        if (!store.save(view)) {
            log.warn("Job rejected: the job store is full of unfinished jobs");
            throw new RejectedException(QUEUE_FULL);
        }
        Job job = new Job(view, work, MDC.get(CORRELATION_ID));
        active.put(id, job);
        try {
            workers.execute(job);
        } catch (RejectedExecutionException e) {
            active.remove(id);
            store.remove(id);
            log.warn("Job rejected: {}", accepting ? "the queue is full" : "the server is shutting down");
            throw new RejectedException(accepting ? QUEUE_FULL : SHUTTING_DOWN);
        }
        log.info("Job {} queued ({})", shortId(id), type.id());
        return view;
    }

    private void evictExpired() {
        try {
            store.evictExpired();
        } catch (RuntimeException e) {
            log.warn("Failed to evict expired jobs: {}", e.getClass().getSimpleName());
        }
    }

    /** Cause d'un echec, sans texte brut ; meme journal que l'endpoint synchrone pour les erreurs connues. */
    static Failure failureOf(String shortId, Exception e) {
        if (e instanceof JobStoppedException) {
            return Failure.of(Kind.INTERRUPTED);
        }
        if (e instanceof InvalidRequestException) {
            return new Failure(Kind.INVALID_REQUEST, e.getMessage(), null);
        }
        if (e instanceof FeatureLevelMissingException) {
            return new Failure(Kind.FEATURE_LEVEL_MISSING, e.getMessage(), null);
        }
        if (e instanceof ContentTooLargeException) {
            return new Failure(Kind.CONTENT_TOO_LARGE, e.getMessage(), null);
        }
        if (e instanceof DocumentNotFoundException) {
            log.warn("Job {}: document not found: {}", shortId, e.getMessage());
            return new Failure(Kind.DOCUMENT_NOT_FOUND, e.getMessage(), null);
        }
        if (e instanceof DocumentSourceUnavailableException) {
            log.warn("Job {}: document source unavailable: {}", shortId, e.getMessage());
            return new Failure(Kind.SOURCE_UNAVAILABLE, e.getMessage(), null);
        }
        if (e instanceof ModelOutputException output) {
            log.warn("Job {}: unusable model output: {}", shortId, causeMessage(e));
            return Failure.of(output.isTruncated() ? Kind.MODEL_OUTPUT_TRUNCATED : Kind.MODEL_OUTPUT_INVALID);
        }
        if (e instanceof ModelUnavailableException unavailable) {
            log.warn("Job {}: LLM provider unavailable: {}", shortId, causeMessage(e));
            Kind kind = e instanceof ModelRateLimitedException ? Kind.MODEL_RATE_LIMITED
                    : e instanceof ModelOverloadedException ? Kind.MODEL_OVERLOADED : Kind.MODEL_UNAVAILABLE;
            return new Failure(kind, null, kind == Kind.MODEL_UNAVAILABLE ? null : unavailable.retryAfter().orElse(null));
        }
        log.error("Job {} failed with an unexpected error", shortId, e);
        return Failure.of(Kind.UNEXPECTED);
    }

    private static String causeMessage(Exception e) {
        return e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
    }

    static String shortId(String id) {
        return id.length() > 8 ? id.substring(0, 8) : id;
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger count = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + count.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Travail d'un job : appelle le cas d'usage avec le suivi des etapes. */
    @FunctionalInterface
    private interface Work {
        Result run(AnalysisProgress progress);
    }

    /** {@code project} : projet demande pour un apercu (son niveau Feature donne les avertissements). */
    private record Result(List<Epic> preview, BacklogCreationResult creation, String project) {
        Result(List<Epic> preview, BacklogCreationResult creation) {
            this(preview, creation, null);
        }
    }

    /** Arret demande (delai depasse, arret du serveur) : leve au debut de l'etape suivante. */
    private static final class JobStoppedException extends RuntimeException {
        JobStoppedException() {
            super("The job was stopped", null, false, false);
        }
    }

    /** Un job et son etat d'execution ; tout changement d'etat se fait sous son verrou. */
    private final class Job implements Runnable {

        private final String id;
        private final Work work;
        private final String correlationId;
        private JobView view;
        private Thread thread;
        private boolean started;
        private boolean finished;
        private boolean stopRequested;
        private ScheduledFuture<?> deadline;
        private long startNanos;

        Job(JobView view, Work work, String correlationId) {
            this.id = view.id();
            this.view = view;
            this.work = work;
            this.correlationId = correlationId;
        }

        @Override
        public void run() {
            synchronized (this) {
                if (finished) {
                    return;
                }
                if (!accepting) {
                    finish(v -> v.failed(Failure.of(Kind.INTERRUPTED), clock.instant()));
                    active.remove(id);
                    return;
                }
                started = true;
                thread = Thread.currentThread();
                startNanos = System.nanoTime();
                view = view.started(clock.instant());
                store.save(view);
                try {
                    deadline = watchdog.schedule(this::expire, settings.maxDuration().toMillis(), TimeUnit.MILLISECONDS);
                } catch (RejectedExecutionException e) {
                    // Arret en cours : le job sera interrompu par close()
                }
            }
            String previousCorrelationId = MDC.get(CORRELATION_ID);
            if (correlationId != null) {
                MDC.put(CORRELATION_ID, correlationId);
            }
            log.info("Job {} started ({})", shortId(id), view.type().id());
            try {
                Result result = work.run(this::step);
                // Hors du verrou du job : la lecture du niveau Feature peut interroger le gestionnaire
                List<String> warnings = result.preview() != null && describeTracker != null
                        ? describeTracker.featureWarnings(result.preview(), result.project()) : List.of();
                finish(v -> v.completed(result.preview(), result.creation(), clock.instant()).withWarnings(warnings));
            } catch (Exception e) {
                boolean stopped;
                synchronized (this) {
                    stopped = stopRequested;
                }
                if (!stopped) {
                    Failure failure = failureOf(shortId(id), e);
                    finish(v -> v.failed(failure, clock.instant()));
                }
            } finally {
                // Erreur grave (Error) : le job ne reste jamais « en cours »
                finish(v -> v.failed(Failure.of(Kind.UNEXPECTED), clock.instant()));
                synchronized (this) {
                    thread = null;
                    if (deadline != null) {
                        deadline.cancel(false);
                    }
                }
                // Le fil revient au pool sans interruption en attente
                Thread.interrupted();
                active.remove(id);
                if (previousCorrelationId != null) {
                    MDC.put(CORRELATION_ID, previousCorrelationId);
                } else {
                    MDC.remove(CORRELATION_ID);
                }
            }
        }

        /** Debut d'une etape ; refuse si un arret a ete demande, ou de commencer la creation pendant l'arret du serveur. */
        private synchronized void step(JobView.Step step) {
            if (stopRequested || finished || (step == JobView.Step.CREATING_ISSUES && !accepting)) {
                throw new JobStoppedException();
            }
            view = view.at(step);
            store.save(view);
        }

        /** Delai maximal ecoule : echec (504) et interruption, sauf si la creation des tickets a commence. */
        private void expire() {
            synchronized (this) {
                if (finished || view.step() == JobView.Step.CREATING_ISSUES) {
                    return;
                }
                stopRequested = true;
                finish(v -> v.failed(Failure.of(Kind.TIMEOUT), clock.instant()));
                // Sous le verrou : le fil ne peut pas etre passe a un autre job entre-temps
                if (thread != null) {
                    thread.interrupt();
                }
            }
            log.warn("Job {} exceeded its maximum duration ({})", shortId(id), settings.maxDuration());
        }

        /** Arret du serveur : interrompt le job s'il n'a pas commence la creation des tickets. */
        boolean stopForShutdown() {
            synchronized (this) {
                if (finished || view.step() == JobView.Step.CREATING_ISSUES) {
                    return false;
                }
                stopRequested = true;
                finish(v -> v.failed(Failure.of(Kind.INTERRUPTED), clock.instant()));
                if (thread != null) {
                    thread.interrupt();
                }
            }
            return true;
        }

        /** Annule le job s'il n'a pas commence ; faux sinon. */
        synchronized boolean cancelQueued() {
            if (started || finished) {
                return false;
            }
            finished = true;
            return true;
        }

        synchronized boolean isFinished() {
            return finished;
        }

        private synchronized void finish(UnaryOperator<JobView> change) {
            if (finished) {
                return;
            }
            finished = true;
            view = change.apply(view);
            store.save(view);
            long millis = startNanos == 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.info("Job {} {} after {} ms{}", shortId(id), view.status().id(), millis,
                    view.failure() != null ? " (" + view.failure().kind() + ")" : "");
        }
    }
}
