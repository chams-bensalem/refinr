// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.adapters.in.rest;

import fr.heliocraft.refinr.application.port.in.AnalysisJobs;
import fr.heliocraft.refinr.application.port.in.ContentTooLargeException;
import fr.heliocraft.refinr.application.port.in.DescribeSource;
import fr.heliocraft.refinr.application.port.in.DescribeTracker;
import fr.heliocraft.refinr.application.port.in.InvalidRequestException;
import fr.heliocraft.refinr.application.port.in.JobView;
import fr.heliocraft.refinr.domain.model.Language;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Jobs d'analyse : {@code analyze-preview} et {@code analyze-and-create} executes en arriere-plan. La soumission
 * controle la requete comme l'endpoint synchrone (memes 400 et 413) et repond 202 avec l'adresse du job ;
 * {@code GET /{id}} donne son etat ; {@code GET /{id}/result} rejoue la reponse de l'endpoint synchrone (statut,
 * en-tetes, corps) ; {@code DELETE /{id}} annule un job en attente ou efface un job termine. Les endpoints synchrones
 * ne changent pas.
 */
@RestController
@RequestMapping(JobPaths.BASE)
public class JobController {

    static final String UNKNOWN_JOB = "Unknown or expired job";
    static final String NOT_FINISHED = "The job is not finished";
    static final String RUNNING = "The job is running and cannot be cancelled";
    static final String TIMEOUT = "The analysis exceeded its maximum duration";
    static final String INTERRUPTED = "The job was interrupted by a server shutdown";
    static final String SUBMISSION_FAILED = "Failed to submit the job";

    private static final Logger log = LoggerFactory.getLogger(JobController.class);

    private final AnalysisJobs jobs;
    private final DescribeTracker describeTracker;
    private final DescribeSource describeSource;

    public JobController(AnalysisJobs jobs, DescribeTracker describeTracker, DescribeSource describeSource) {
        this.jobs = jobs;
        this.describeTracker = describeTracker;
        this.describeSource = describeSource;
    }

    @PostMapping("/analyze-preview")
    public ResponseEntity<Map<String, Object>> submitPreview(@RequestBody Map<String, String> request) {
        Optional<Language> language;
        try {
            language = Optional.ofNullable(AnalysisRequests.language(request.get(AnalysisRequests.LANGUAGE)));
        } catch (IllegalArgumentException e) {
            return BacklogResponses.invalidRequest(AnalysisRequests.INVALID_LANGUAGE);
        }
        return submit(() -> {
            if (AnalysisRequests.isTextInput(request)) {
                AnalysisRequests.logTextInput(log, "Preview job", request);
                return jobs.submitPreviewText(request.get(AnalysisRequests.TITLE), request.get(AnalysisRequests.CONTENT),
                        language.orElse(null));
            }
            String documentId = AnalysisRequests.documentId(request);
            log.info("Preview job request for {} document: {}", describeSource.displayName(), documentId);
            return jobs.submitPreview(documentId, language.orElse(null));
        });
    }

    @PostMapping("/analyze-and-create")
    public ResponseEntity<Map<String, Object>> submitAnalyzeAndCreate(@RequestBody Map<String, String> request) {
        String projectKey = request.get(AnalysisRequests.PROJECT_KEY);
        if (projectKey != null && !projectKey.isBlank() && !describeTracker.acceptsProjectId(projectKey)) {
            return BacklogResponses.invalidRequest("Invalid projectKey: " + projectKey);
        }
        Optional<Language> language;
        try {
            language = Optional.ofNullable(AnalysisRequests.language(request.get(AnalysisRequests.LANGUAGE)));
        } catch (IllegalArgumentException e) {
            return BacklogResponses.invalidRequest(AnalysisRequests.INVALID_LANGUAGE);
        }
        return submit(() -> {
            if (AnalysisRequests.isTextInput(request)) {
                AnalysisRequests.logTextInput(log, "Analyze job", request);
                return jobs.submitAnalyzeTextAndCreate(request.get(AnalysisRequests.TITLE),
                        request.get(AnalysisRequests.CONTENT), projectKey, language.orElse(null));
            }
            String documentId = AnalysisRequests.documentId(request);
            log.info("Analyze job request for {} document: {}", describeSource.displayName(), documentId);
            return jobs.submitAnalyzeAndCreate(documentId, projectKey, language.orElse(null));
        });
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> status(@PathVariable String id) {
        Optional<JobView> found = find(id);
        if (found.isEmpty()) {
            return unknownJob();
        }
        JobView job = found.get();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("jobId", job.id());
        body.put("type", job.type().id());
        body.put("status", job.status().id());
        if (job.step() != null) {
            body.put("step", job.step().id());
        }
        body.put("createdAt", job.createdAt().toString());
        if (job.startedAt() != null) {
            body.put("startedAt", job.startedAt().toString());
        }
        if (job.finishedAt() != null) {
            body.put("finishedAt", job.finishedAt().toString());
        }
        if (job.isFinished()) {
            Map<String, Object> outcome = new LinkedHashMap<>();
            outcome.put("httpStatus", result(job).getStatusCode().value());
            if (job.failure() != null) {
                job.failure().retryAfterDelay().ifPresent(delay -> outcome.put("retryAfterSeconds", delay.toSeconds()));
            }
            body.put("outcome", outcome);
        }
        return ResponseEntity.ok(body);
    }

    /** Reponse de l'endpoint synchrone equivalent, rejouee : meme statut, memes en-tetes, meme corps. */
    @GetMapping("/{id}/result")
    public ResponseEntity<Map<String, Object>> jobResult(@PathVariable String id) {
        Optional<JobView> found = find(id);
        if (found.isEmpty()) {
            return unknownJob();
        }
        if (!found.get().isFinished()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(NOT_FINISHED));
        }
        return result(found.get());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Map<String, Object>> cancel(@PathVariable String id) {
        if (!JobPaths.isJobId(id)) {
            return unknownJob();
        }
        return switch (jobs.cancel(id)) {
            case REMOVED -> ResponseEntity.noContent().build();
            case RUNNING -> ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponses.error(RUNNING));
            case NOT_FOUND -> unknownJob();
        };
    }

    @FunctionalInterface
    private interface Submission {
        JobView submit();
    }

    /** 202 avec l'adresse du job ; refus de la requete identiques a l'endpoint synchrone ; file pleine : 503. */
    private ResponseEntity<Map<String, Object>> submit(Submission submission) {
        try {
            JobView job = submission.submit();
            String location = JobPaths.BASE + "/" + job.id();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("jobId", job.id());
            body.put("type", job.type().id());
            body.put("status", job.status().id());
            body.put("statusUrl", location);
            body.put("resultUrl", location + "/result");
            return ResponseEntity.accepted().location(URI.create(location)).body(body);
        } catch (InvalidRequestException e) {
            return BacklogResponses.invalidRequest(e.getMessage());
        } catch (ContentTooLargeException e) {
            log.warn("Provided text rejected: {}", e.getMessage());
            return BacklogResponses.contentTooLarge(e.getMessage());
        } catch (AnalysisJobs.RejectedException e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponses.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error submitting a job", e);
            return BacklogResponses.internalError(SUBMISSION_FAILED);
        }
    }

    private Optional<JobView> find(String id) {
        return JobPaths.isJobId(id) ? jobs.find(id) : Optional.empty();
    }

    private static ResponseEntity<Map<String, Object>> unknownJob() {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ErrorResponses.error(UNKNOWN_JOB));
    }

    /** Reponse synchrone d'un job termine : memes fonctions que BacklogController. */
    private ResponseEntity<Map<String, Object>> result(JobView job) {
        boolean preview = job.type() == JobView.Type.ANALYZE_PREVIEW;
        // Job alle au bout (meme une creation sans aucun ticket, 502) : reponse construite sur son resultat
        if (job.failure() == null) {
            ResponseEntity<Map<String, Object>> response = preview
                    ? BacklogResponses.preview(job.preview())
                    : BacklogResponses.creation(job.creation());
            // Comme l'endpoint synchrone : lien vers le document seulement s'il a ete lu dans la source active
            return job.documentId() == null ? response
                    : BacklogResponses.withDocumentUrl(response, describeSource.documentUrl(job.documentId()));
        }
        JobView.Failure failure = job.failure();
        return switch (failure.kind()) {
            case INVALID_REQUEST -> BacklogResponses.invalidRequest(failure.message());
            case CONTENT_TOO_LARGE -> BacklogResponses.contentTooLarge(failure.message());
            case DOCUMENT_NOT_FOUND -> BacklogResponses.documentNotFound(failure.message());
            case SOURCE_UNAVAILABLE -> BacklogResponses.sourceUnavailable(failure.message());
            case MODEL_OUTPUT_INVALID -> BacklogResponses.modelOutputFailure(false);
            case MODEL_OUTPUT_TRUNCATED -> BacklogResponses.modelOutputFailure(true);
            case MODEL_RATE_LIMITED -> BacklogResponses.modelBusy(true, failure.retryAfterDelay());
            case MODEL_OVERLOADED -> BacklogResponses.modelBusy(false, failure.retryAfterDelay());
            case MODEL_UNAVAILABLE -> BacklogResponses.modelUnavailable();
            case TIMEOUT -> ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(ErrorResponses.error(TIMEOUT));
            case INTERRUPTED -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ErrorResponses.error(INTERRUPTED));
            case UNEXPECTED -> BacklogResponses.internalError(
                    preview ? BacklogResponses.PREVIEW_FAILED : BacklogResponses.CREATION_FAILED);
        };
    }
}
