// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

package fr.heliocraft.refinr.application.port.in;

import fr.heliocraft.refinr.application.port.out.JobStorePort;
import fr.heliocraft.refinr.domain.model.Epic;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Etat d'un job d'analyse a un instant donne (valeur immuable) : jamais le contenu du document, seulement son
 * identifiant dans la source active quand il a ete donne (pour le lien {@code documentUrl} du resultat).
 *
 * @param step       etape en cours ou derniere etape atteinte ; null tant que le job est en attente
 * @param preview    resultat d'un apercu reussi, sinon null
 * @param creation   resultat d'une analyse avec creation reussie (meme partielle), sinon null
 * @param failure    cause de l'echec quand le job s'est arrete sur une erreur, sinon null ; un job
 *                   {@code FAILED} sans cause est une creation dont aucun ticket n'a ete cree ({@code creation} garde)
 */
public record JobView(String id, Type type, Status status, Step step, Instant createdAt, Instant startedAt,
                      Instant finishedAt, String documentId, List<Epic> preview, BacklogCreationResult creation,
                      Failure failure) implements JobStorePort.Entry {

    /** Nature du job : l'endpoint synchrone dont il rejoue la reponse. */
    public enum Type {
        ANALYZE_PREVIEW, ANALYZE_AND_CREATE;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    public enum Status {
        QUEUED, RUNNING, SUCCEEDED, FAILED;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Etapes d'une analyse, dans l'ordre ; certaines sont sautees (texte fourni, pas de fournisseur de verdict distinct). */
    public enum Step {
        READING_DOCUMENT, GENERATING, ASSESSING, CREATING_ISSUES;

        public String id() {
            return name().toLowerCase(Locale.ROOT).replace('_', '-');
        }
    }

    /**
     * Cause d'un echec, sans exception ni texte brut : le message n'est garde que pour les causes dont la reponse
     * synchrone renvoie deja le message (ecrit par l'application ou l'adaptateur, sans donnee sensible).
     *
     * @param retryAfter delai annonce par le fournisseur (quota, surcharge), sinon null
     */
    public record Failure(Kind kind, String message, Duration retryAfter) {

        public enum Kind {
            INVALID_REQUEST, CONTENT_TOO_LARGE, DOCUMENT_NOT_FOUND, SOURCE_UNAVAILABLE,
            MODEL_OUTPUT_INVALID, MODEL_OUTPUT_TRUNCATED, MODEL_RATE_LIMITED, MODEL_OVERLOADED, MODEL_UNAVAILABLE,
            FEATURE_LEVEL_MISSING,
            TIMEOUT, INTERRUPTED, UNEXPECTED
        }

        public Failure {
            Objects.requireNonNull(kind, "kind");
        }

        public static Failure of(Kind kind) {
            return new Failure(kind, null, null);
        }

        public Optional<Duration> retryAfterDelay() {
            return Optional.ofNullable(retryAfter);
        }
    }

    public JobView {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        preview = preview == null ? null : List.copyOf(preview);
    }

    /** Nouveau job, en attente. */
    public static JobView queued(String id, Type type, String documentId, Instant now) {
        return new JobView(id, type, Status.QUEUED, null, now, null, null, documentId, null, null, null);
    }

    @Override
    public boolean isFinished() {
        return status == Status.SUCCEEDED || status == Status.FAILED;
    }

    public JobView started(Instant now) {
        return new JobView(id, type, Status.RUNNING, step, createdAt, now, null, documentId, null, null, null);
    }

    public JobView at(Step newStep) {
        return new JobView(id, type, status, newStep, createdAt, startedAt, finishedAt, documentId, preview, creation,
                failure);
    }

    /**
     * Job alle au bout : {@code SUCCEEDED}, sauf une creation dont aucun ticket n'a ete cree ({@code FAILED}, resultat
     * garde pour rejouer la reponse synchrone, 502). Regle : {@code SUCCEEDED} seulement si la reponse rejouee est 2xx.
     */
    public JobView completed(List<Epic> epics, BacklogCreationResult result, Instant now) {
        Status outcome = result != null && result.isNothingCreated() ? Status.FAILED : Status.SUCCEEDED;
        return new JobView(id, type, outcome, step, createdAt, startedAt, now, documentId, epics, result, null);
    }

    public JobView failed(Failure cause, Instant now) {
        return new JobView(id, type, Status.FAILED, step, createdAt, startedAt, now, documentId, null, null, cause);
    }
}
