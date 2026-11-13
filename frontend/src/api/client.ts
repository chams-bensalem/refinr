// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import axios, { isAxiosError } from 'axios';
import { apiBaseUrl } from '../config';
import type { AnalyzeRequest, ApiErrorBody, CreationResponse, Epic, HealthStatus, PreviewResponse, SourceInfo, TrackerInfo } from '../types';

const api = axios.create({
    baseURL: apiBaseUrl(),
    headers: {
        'Content-Type': 'application/json',
    },
});

/** Erreur d'appel a l'API : message renvoye par le backend et correlationId pour retrouver le detail dans ses logs. */
export class ApiError extends Error {
    readonly correlationId?: string;
    readonly body?: ApiErrorBody;
    /** Code HTTP de la reponse, absent si le backend n'a pas repondu. */
    readonly status?: number;

    constructor(message: string, body?: ApiErrorBody, status?: number) {
        super(message);
        this.name = 'ApiError';
        this.correlationId = body?.correlationId;
        this.body = body;
        this.status = status;
    }
}

/** 413 sans corps JSON : refuse par le proxy (nginx) avant d'atteindre le backend. */
const TOO_LARGE = 'The document is too large for the server (HTTP 413). Shorten the text or split the spec into several analyses.';

function toApiError(error: unknown): ApiError {
    if (isAxiosError<ApiErrorBody>(error)) {
        const status = error.response?.status;
        const body = error.response?.data;
        if (body && typeof body === 'object' && typeof body.error === 'string') {
            return new ApiError(body.error, body, status);
        }
        if (status === 413) {
            return new ApiError(TOO_LARGE, undefined, status);
        }
        return new ApiError(status ? `Request failed (HTTP ${status})` : 'Backend unreachable', undefined, status);
    }
    return new ApiError(error instanceof Error ? error.message : 'Unexpected error');
}

/** Apercu du backlog genere depuis un document de la source active ou un texte fourni, sans rien creer (verdict ai-ready inclus). */
export const previewBacklog = async (request: AnalyzeRequest): Promise<PreviewResponse> => {
    try {
        const response = await api.post<PreviewResponse>('/analyze-preview', request);
        return response.data;
    } catch (error) {
        throw toApiError(error);
    }
};

function isCreationResponse(body: unknown): body is CreationResponse {
    return typeof body === 'object' && body !== null && Array.isArray((body as CreationResponse).failed)
        && Array.isArray((body as CreationResponse).created);
}

/**
 * Cree le backlog affiche dans le gestionnaire de tickets actif (le serveur recalcule le verdict ai-ready sur ce contenu).
 * 200 : tout est cree ; 207 : creation partielle ; 502 avec created / failed : rien n'a pu etre cree, raisons incluses.
 * Toute autre reponse : ApiError.
 */
export const createBacklog = async (epics: Epic[], projectKey?: string): Promise<CreationResponse> => {
    try {
        const response = await api.post<CreationResponse | ApiErrorBody>('/create-jira', epics, {
            params: projectKey ? { projectKey } : undefined,
            validateStatus: (status) => status === 200 || status === 207 || status === 502,
        });
        if (isCreationResponse(response.data)) {
            return response.data;
        }
        const body = response.data as ApiErrorBody;
        throw new ApiError(typeof body?.error === 'string' ? body.error : `Request failed (HTTP ${response.status})`, body);
    } catch (error) {
        throw error instanceof ApiError ? error : toApiError(error);
    }
};

/** Source documentaire active (Confluence, BookStack ou XWiki) et taille maximale d'un texte fourni, pour l'interface. */
export const getSource = async (): Promise<SourceInfo> => {
    try {
        const response = await api.get<SourceInfo>('/source');
        return response.data;
    } catch (error) {
        throw toApiError(error);
    }
};

/** Nom du gestionnaire de tickets actif (Jira ou OpenProject), pour les libelles. */
export const getTracker = async (): Promise<TrackerInfo> => {
    try {
        const response = await api.get<TrackerInfo>('/tracker');
        return response.data;
    } catch (error) {
        throw toApiError(error);
    }
};

export const getHealth = async (): Promise<HealthStatus> => {
    try {
        const response = await api.get<HealthStatus>('/health');
        return response.data;
    } catch (error) {
        throw toApiError(error);
    }
};

export default api;
