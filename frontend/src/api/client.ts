import axios from 'axios';
import type {AnalyzeRequest, AnalysisResult, HealthStatus} from '../types';

const api = axios.create({
    baseURL: '/api/v1',
    headers: {
        'Content-Type': 'application/json',
    },
});

export const analyzeAndCreate = async (request: AnalyzeRequest): Promise<string> => {
    const response = await api.post<string>('/mvp/analyze-and-create', request);
    return response.data;
};

export const getHealth = async (): Promise<HealthStatus> => {
    const response = await api.get<HealthStatus>('/health');
    return response.data;
};

export default api;