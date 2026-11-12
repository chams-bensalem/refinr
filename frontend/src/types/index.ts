export interface AnalyzeRequest {
    confluencePageId: string;
}

export interface Epic {
    name: string;
    description: string;
    priority: string;
    labels: string[];
    jiraKey?: string;
    stories: Story[];
}

export interface Story {
    asA: string;
    iWant: string;
    soThat: string;
    acceptanceCriteria: string[];
    priority: string;
    labels: string[];
    estimatedStoryPoints?: number;
    jiraKey?: string;
    tasks: Task[];
}

export interface Task {
    name: string;
    description: string;
    jiraKey?: string;
}

export interface AnalysisResult {
    epics: Epic[];
}

export interface HealthStatus {
    status: 'UP' | 'DOWN' | 'DEGRADED';
    services: {
        confluence: ServiceHealth;
        jira: ServiceHealth;
        gemini: ServiceHealth;
    };
}

export interface ServiceHealth {
    status: 'UP' | 'DOWN';
    error?: string;
}