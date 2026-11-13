// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

// Contrat JSON de l'API backend (/api/v1). Champs optionnels : absents ou null selon l'etape (apercu / creation).

export interface AnalyzeRequest {
    /** Identifiant du document dans la source active (id Confluence ou BookStack, reference XWiki). */
    documentId: string;
    /** Projet cible du gestionnaire de tickets actif (cle Jira ou identifiant OpenProject) ; absent : projet par defaut. */
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
    /** Adresse web du ticket dans le gestionnaire actif (null si inconnue). */
    url?: string | null;
}

export interface FailedIssue {
    type: IssueType;
    title: string;
    reason: string;
}

/** Reponse de analyze-preview : backlog genere, rien n'est cree. */
export interface PreviewResponse {
    success: boolean;
    message: string;
    epics: Epic[];
    /** Lien vers le document analyse dans la source active, si la source sait le construire. */
    documentUrl?: string | null;
}

/** Reponse de analyze-and-create / create-jira : 200 (tout cree) ou 207 (creation partielle). */
export interface CreationResponse {
    success: boolean;
    message: string;
    created: CreatedIssue[];
    failed: FailedIssue[];
    epics: Epic[];
}

/** Corps d'erreur des endpoints du backlog (/api/v1/*) : jamais le message brut d'une exception. */
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
    /** source active (confluence, bookstack ou xwiki), gestionnaire de tickets actif (jira ou openproject), ai, aiVerdict eventuel. */
    services: Record<string, ServiceHealth>;
}

/** Source documentaire active de l'instance (GET /source). */
export interface SourceInfo {
    name: string;
}

/** Gestionnaire de tickets actif de l'instance (GET /tracker). */
export interface TrackerInfo {
    name: string;
}
