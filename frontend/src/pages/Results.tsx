// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { AlertTriangle, CheckCircle2, FileText, ListChecks, Target, XCircle } from 'lucide-react';
import type { CreationResponse, IssueType } from '../types';

const TYPE_LABELS: Record<IssueType, string> = {
    epic: 'Epic',
    story: 'Story',
    subtask: 'Subtask',
};

function isCreationResponse(state: unknown): state is CreationResponse {
    return typeof state === 'object' && state !== null
        && Array.isArray((state as CreationResponse).created)
        && Array.isArray((state as CreationResponse).failed);
}

export default function Results() {
    const location = useLocation();
    const navigate = useNavigate();
    const results: unknown = location.state;

    // Pas de resultat (acces direct, rechargement) : retour a l'analyse, sans naviguer pendant le rendu
    if (!isCreationResponse(results)) {
        return <Navigate to="/analyze" replace />;
    }

    const count = (type: IssueType) => results.created.filter((issue) => issue.type === type).length;
    const partial = results.failed.length > 0;

    const stats = [
        { label: 'Epics Created', value: count('epic'), icon: Target, color: 'text-purple-600', bgColor: 'bg-purple-100' },
        { label: 'Stories Created', value: count('story'), icon: FileText, color: 'text-blue-600', bgColor: 'bg-blue-100' },
        { label: 'Subtasks Created', value: count('subtask'), icon: ListChecks, color: 'text-green-600', bgColor: 'bg-green-100' },
    ];

    return (
        <div className="min-h-screen bg-gradient-to-br from-violet-50 via-white to-rose-50 py-12 px-4">
            <div className="max-w-6xl mx-auto space-y-8">

                {partial ? (
                    <div className="bg-amber-50 border-l-4 border-amber-500 p-6 rounded-lg shadow-sm">
                        <div className="flex items-center gap-3">
                            <AlertTriangle className="w-8 h-8 text-amber-600" />
                            <div>
                                <h1 className="text-2xl font-bold text-amber-900">Partially created</h1>
                                <p className="text-amber-800 mt-1">
                                    {results.created.length} issue(s) created, {results.failed.length} not created.
                                    Only the items listed below need to be created again.
                                </p>
                            </div>
                        </div>
                    </div>
                ) : (
                    <div className="bg-green-50 border-l-4 border-green-500 p-6 rounded-lg shadow-sm">
                        <div className="flex items-center gap-3">
                            <CheckCircle2 className="w-8 h-8 text-green-600" />
                            <div>
                                <h1 className="text-2xl font-bold text-green-900">Analysis Complete!</h1>
                                <p className="text-green-700 mt-1">
                                    {results.created.length} Jira issue(s) created
                                </p>
                            </div>
                        </div>
                    </div>
                )}

                <div className="grid md:grid-cols-3 gap-6">
                    {stats.map((stat) => (
                        <div key={stat.label} className="bg-white rounded-xl shadow-md p-6 hover:shadow-lg transition-shadow">
                            <div className="flex items-center justify-between">
                                <div>
                                    <p className="text-sm text-gray-600 font-medium">{stat.label}</p>
                                    <p className="text-4xl font-bold text-gray-900 mt-2">{stat.value}</p>
                                </div>
                                <div className={`${stat.bgColor} p-4 rounded-full`}>
                                    <stat.icon className={`w-8 h-8 ${stat.color}`} />
                                </div>
                            </div>
                        </div>
                    ))}
                </div>

                {partial && (
                    <div className="bg-white rounded-xl shadow-md p-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">Not created</h2>
                        <ul className="divide-y divide-gray-100">
                            {results.failed.map((issue, index) => (
                                <li key={`${issue.type}-${index}`} className="py-3 flex items-start gap-3">
                                    <XCircle className="w-5 h-5 text-red-500 mt-0.5 flex-shrink-0" />
                                    <div>
                                        <p className="font-medium text-gray-900">
                                            {TYPE_LABELS[issue.type]} — {issue.title}
                                        </p>
                                        <p className="text-sm text-gray-600">{issue.reason}</p>
                                    </div>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}

                {results.created.length > 0 && (
                    <div className="bg-white rounded-xl shadow-md p-8">
                        <h2 className="text-2xl font-bold text-gray-900 mb-4">Created issues</h2>
                        <ul className="divide-y divide-gray-100">
                            {results.created.map((issue) => (
                                <li key={issue.key} className="py-3 flex items-center gap-3">
                                    <span className="font-mono text-sm font-semibold text-blue-700 w-24 flex-shrink-0">{issue.key}</span>
                                    <span className="text-xs uppercase tracking-wide text-gray-500 w-16 flex-shrink-0">{TYPE_LABELS[issue.type]}</span>
                                    <span className="text-gray-900">{issue.title}</span>
                                </li>
                            ))}
                        </ul>
                    </div>
                )}

                <button
                    onClick={() => navigate('/analyze')}
                    className="w-full px-6 py-3 bg-white border-2 border-blue-600 text-blue-600 rounded-lg font-semibold hover:bg-blue-50 transition-all"
                >
                    Start New Analysis
                </button>

            </div>
        </div>
    );
}
