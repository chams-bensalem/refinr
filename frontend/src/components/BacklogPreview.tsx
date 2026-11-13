// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { Bot, CircleHelp, Layers, ListChecks } from 'lucide-react';
import type { Epic, Story } from '../types';

/** Verdict affiche : un verdict absent vaut validation-necessaire, comme cote serveur. */
function ReadinessBadge({ story }: { story: Story }) {
    const ready = story.aiReady === true;
    return (
        <span
            className={`inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-xs font-semibold whitespace-nowrap ${
                ready ? 'bg-green-100 text-green-800' : 'bg-amber-100 text-amber-800'
            }`}
        >
            {ready ? <Bot className="h-3.5 w-3.5" /> : <CircleHelp className="h-3.5 w-3.5" />}
            {ready ? 'ai-ready' : 'validation-necessaire'}
        </span>
    );
}

/** Arborescence Epics > Stories > Subtasks d'un apercu, avec le verdict ai-ready de chaque story et sa raison. */
export default function BacklogPreview({ epics }: { epics: Epic[] }) {
    return (
        <div className="space-y-6">
            {epics.map((epic, epicIndex) => (
                <section key={`${epic.name}-${epicIndex}`} className="border border-gray-200 rounded-xl overflow-hidden">
                    <header className="flex items-start gap-3 bg-purple-50 px-5 py-4">
                        <Layers className="h-5 w-5 text-purple-600 mt-0.5 flex-shrink-0" />
                        <div className="min-w-0">
                            <p className="text-xs font-semibold uppercase tracking-wide text-purple-700">Epic</p>
                            <h3 className="font-bold text-gray-900">{epic.name}</h3>
                            {epic.description && <p className="mt-1 text-sm text-gray-600">{epic.description}</p>}
                        </div>
                    </header>

                    <ul className="divide-y divide-gray-100">
                        {(epic.stories ?? []).map((story, storyIndex) => (
                            <li key={`${story.title}-${storyIndex}`} className="px-5 py-4">
                                <div className="flex flex-wrap items-center justify-between gap-2">
                                    <p className="font-semibold text-gray-900">
                                        <span className="text-xs font-semibold uppercase tracking-wide text-blue-700 mr-2">Story</span>
                                        {story.title}
                                        {story.estimatedStoryPoints != null && (
                                            <span className="ml-2 text-xs font-normal text-gray-500">{story.estimatedStoryPoints} pts</span>
                                        )}
                                    </p>
                                    <ReadinessBadge story={story} />
                                </div>
                                {story.aiReadyReason && (
                                    <p className={`mt-1 text-sm ${story.aiReady ? 'text-green-700' : 'text-amber-700'}`}>
                                        {story.aiReadyReason}
                                    </p>
                                )}
                                {(story.tasks ?? []).length > 0 && (
                                    <ul className="mt-3 ml-1 space-y-1">
                                        {(story.tasks ?? []).map((task, taskIndex) => (
                                            <li key={`${task.title}-${taskIndex}`} className="flex items-center gap-2 text-sm text-gray-700">
                                                <ListChecks className="h-4 w-4 text-emerald-600 flex-shrink-0" />
                                                <span>{task.title}</span>
                                                {task.estimatedHours != null && (
                                                    <span className="text-xs text-gray-400">{task.estimatedHours} h</span>
                                                )}
                                            </li>
                                        ))}
                                    </ul>
                                )}
                            </li>
                        ))}
                    </ul>
                </section>
            ))}
        </div>
    );
}
