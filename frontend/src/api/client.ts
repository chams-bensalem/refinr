// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import axios, { isAxiosError } from 'axios';
import { apiBaseUrl } from '../config';
import type { AnalyzeRequest, ApiErrorBody, CreationResponse, HealthStatus } from '../types';

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

    constructor(message: string, body?: ApiErrorBody) {
        super(message);
        this.name = 'ApiError';
        this.correlationId = body?.correlationId;
        this.body = body;
    }
}

function toApiError(error: unknown): ApiError {
    if (isAxiosError<ApiErrorBody>(error)) {
        const body = error.response?.data;
        if (body && typeof body === 'object' && typeof body.error === 'string') {
            return new ApiError(body.error, body);
        }
        return new ApiError(error.response ? `Request failed (HTTP ${error.response.status})` : 'Backend unreachable');
    }
    return new ApiError(error instanceof Error ? error.message : 'Unexpected error');
}

/** 200 : tout est cree ; 207 : creation partielle (created / failed) ; autre statut : ApiError. */
export const analyzeAndCreate = async (request: AnalyzeRequest): Promise<CreationResponse> => {
    try {
        const response = await api.post<CreationResponse>('/analyze-and-create', request, {
            validateStatus: (status) => status === 200 || status === 207,
        });
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
