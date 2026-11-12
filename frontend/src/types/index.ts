// Contrat JSON de l'API backend (/api/v1). Champs optionnels : absents ou null selon l'etape (apercu / creation).

export interface AnalyzeRequest {
    confluencePageId: string;
    /** Projet Jira cible (format PROJ) ; absent : projet par defaut du backend. */
    projectKey?: string;
}

export interface Task {
    title: string;
    description?: string | null;
    labels?: string[] | null;
    estimatedHours?: number | null;
    jiraKey?: string | null;
    jiraUrl?: string | null;
    parentStoryKey?: string | null;
}

export interface Story {
    title: string;
    description?: string | null;
    asA?: string | null;
    iWant?: string | null;
    soThat?: string | null;
    acceptanceCriteria?: string[] | null;
    storyPoints?: number | null;
    estimatedStoryPoints?: number | null;
    priority?: string | null;
    labels?: string[] | null;
    dependencies?: string[] | null;
    /** Verdict calcule par le serveur (ignore s'il est renvoye par le client). */
    aiReady?: boolean | null;
    aiReadyReason?: string | null;
    tasks?: Task[] | null;
    jiraKey?: string | null;
    jiraUrl?: string | null;
    epicKey?: string | null;
}

export interface Epic {
    name: string;
    description?: string | null;
    priority?: string | null;
    labels?: string[] | null;
    stories?: Story[] | null;
    jiraKey?: string | null;
    jiraUrl?: string | null;
}

export type IssueType = 'epic' | 'story' | 'subtask';

export interface CreatedIssue {
    type: IssueType;
    title: string;
    key: string;
}

export interface FailedIssue {
    type: IssueType;
    title: string;
    reason: string;
}

/** Reponse de analyze-and-create / create-jira : 200 (tout cree) ou 207 (creation partielle). */
export interface CreationResponse {
    success: boolean;
    message: string;
    created: CreatedIssue[];
    failed: FailedIssue[];
    epics: Epic[];
}

/** Corps d'erreur des endpoints /mvp/* (jamais le message brut d'une exception). */
export interface ApiErrorBody {
    success: false;
    error: string;
    correlationId?: string;
    created?: CreatedIssue[];
    failed?: FailedIssue[];
}

export type ServiceStatus = 'UP' | 'DOWN';

export interface ServiceHealth {
    status: ServiceStatus;
    error?: string;
    provider?: string;
    model?: string;
}

export interface HealthStatus {
    status: 'UP' | 'DEGRADED';
    services: {
        confluence: ServiceHealth;
        jira: ServiceHealth;
        ai: ServiceHealth;
    };
}
