// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { useRef, useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { ApiError, createBacklog, previewBacklog } from '../api/client';
import BacklogPreview from '../components/BacklogPreview';
import { documentIdHelp, GENERIC_SOURCE, providedTextSize, useMaxContentChars, useSourceName } from '../hooks/useSource';
import { useTrackerName } from '../hooks/useTrackerName';
import type { Epic } from '../types';
import { AlertCircle, CheckCircle, ClipboardPaste, ExternalLink, Eye, FileText, Loader2, RotateCcw, Send, Sparkles } from 'lucide-react';

/** Where the spec comes from: a page of the active source, or text pasted in the form. */
type InputMode = 'page' | 'text';

const NUMBER = new Intl.NumberFormat('en-US');

function ErrorBox({ title, error, backLabel, onBack, className = 'border-b border-red-100' }: {
    title: string;
    error: Error;
    backLabel: string;
    onBack: () => void;
    className?: string;
}) {
    return (
        <div role="alert" className={`bg-red-50 p-6 ${className}`}>
            <div className="flex items-start">
                <AlertCircle className="h-6 w-6 text-red-600 flex-shrink-0" />
                <div className="ml-3 flex-1">
                    <h3 className="text-sm font-medium text-red-800">{title}</h3>
                    <div className="mt-2 text-sm text-red-700">
                        <p>{error.message || 'Unexpected error. Check the backend logs.'}</p>
                        {error instanceof ApiError && error.status === 413 && (
                            <p className="mt-1">Shorten the text, or split the spec and analyze each part separately.</p>
                        )}
                        {error instanceof ApiError && error.correlationId && (
                            <p className="mt-1 text-xs text-red-500">Reference: {error.correlationId}</p>
                        )}
                    </div>
                    <button onClick={onBack} className="mt-4 text-sm font-medium text-red-600 hover:text-red-500">
                        ← {backLabel}
                    </button>
                </div>
            </div>
        </div>
    );
}

function countBacklog(epics: Epic[]) {
    const stories = epics.flatMap((epic) => epic.stories ?? []);
    return {
        epics: epics.length,
        stories: stories.length,
        subtasks: stories.reduce((total, story) => total + (story.tasks ?? []).length, 0),
        aiReady: stories.filter((story) => story.aiReady === true).length,
    };
}

export default function Analyze() {
    const [mode, setMode] = useState<InputMode>('page');
    const [pageId, setPageId] = useState('');
    const [title, setTitle] = useState('');
    const [content, setContent] = useState('');
    const navigate = useNavigate();
    const tracker = useTrackerName();
    const source = useSourceName();
    const maxChars = useMaxContentChars();
    const help = documentIdHelp(source);
    const page = source === GENERIC_SOURCE ? 'the page' : `the ${source} page`;
    const sourcePage = source === GENERIC_SOURCE ? 'Wiki page' : `${source} page`;

    const textSize = providedTextSize(title, content);
    const tooLong = textSize > maxChars;
    const canSubmit = mode === 'page' ? pageId.trim() !== '' : content.trim() !== '' && !tooLong;

    // Synchronous lock against a double click or a double Enter: isPending only changes after the next render,
    // so two events in the same tick would both pass a check on isPending.
    const inFlight = useRef(false);

    const preview = useMutation({
        mutationFn: previewBacklog,
        retry: false,
        onSettled: () => {
            inFlight.current = false;
        },
    });
    const creation = useMutation({
        mutationFn: (epics: Epic[]) => createBacklog(epics),
        // Never replayed: POST /create-jira is not idempotent (a retry could duplicate issues in the tracker)
        retry: false,
        onSuccess: (data) => navigate('/results', { state: data }),
        onSettled: () => {
            inFlight.current = false;
        },
    });

    const epics = preview.data?.epics ?? [];
    const counts = countBacklog(epics);

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (!canSubmit || inFlight.current) {
            return;
        }
        inFlight.current = true;
        creation.reset();
        preview.mutate(mode === 'page'
            ? { documentId: pageId.trim() }
            : { content, ...(title.trim() ? { title: title.trim() } : {}) });
    };

    const startCreation = () => {
        if (epics.length === 0 || inFlight.current) {
            return;
        }
        inFlight.current = true;
        creation.mutate(epics);
    };

    const startOver = () => {
        creation.reset();
        preview.reset();
    };

    return (
        <div className="max-w-4xl mx-auto">
            <div className="text-center mb-8">
                <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-br from-primary-500 to-primary-700 rounded-2xl mb-4 shadow-lg">
                    <Sparkles className="h-8 w-8 text-white" />
                </div>
                <h1 className="text-4xl font-bold text-gray-900 mb-3">Analyze a spec</h1>
                <p className="text-xl text-gray-600">
                    Preview the backlog drafted from a requirements document in {source} or pasted as text, then create it in {tracker}
                </p>
            </div>

            <div className="bg-white shadow-2xl rounded-2xl overflow-hidden border border-gray-100">
                {preview.isError && (
                    <ErrorBox title="Preview failed" error={preview.error} backLabel="Back to the form" onBack={() => preview.reset()} />
                )}
                {preview.isPending ? (
                    <div className="p-12 flex flex-col items-center">
                        <Loader2 className="h-16 w-16 animate-spin text-primary-600 mb-6" />
                        <h3 className="text-xl font-semibold text-gray-900 mb-2">Analyzing your spec</h3>
                        <p className="text-gray-600 mb-8 text-center max-w-md">
                            The language model is reading the requirements document and drafting Epics, Stories and Sub-tasks.
                            Nothing is created in {tracker} yet.
                        </p>
                        <div className="w-full max-w-md space-y-4 text-sm">
                            <div className="flex items-center space-x-3">
                                <div className="flex-shrink-0 w-6 h-6 bg-green-500 rounded-full flex items-center justify-center">
                                    <CheckCircle className="h-4 w-4 text-white" />
                                </div>
                                <span className="text-gray-700">{mode === 'page' ? `Fetching ${page}` : 'Reading the pasted text'}</span>
                            </div>
                            <div className="flex items-center space-x-3">
                                <div className="flex-shrink-0 w-6 h-6 bg-primary-500 rounded-full flex items-center justify-center animate-pulse">
                                    <div className="w-2 h-2 bg-white rounded-full" />
                                </div>
                                <span className="text-gray-700 font-medium">Analyzing with AI and judging each story</span>
                            </div>
                        </div>
                    </div>
                ) : preview.isSuccess ? (
                    <div className="p-8">
                        <div className="flex flex-wrap items-baseline justify-between gap-2 mb-6">
                            <h2 className="text-2xl font-bold text-gray-900">Backlog preview</h2>
                            {preview.data?.documentUrl && (
                                <a
                                    href={preview.data.documentUrl}
                                    target="_blank"
                                    rel="noopener noreferrer"
                                    className="inline-flex items-center text-sm font-medium text-primary-600 hover:text-primary-700"
                                >
                                    <ExternalLink className="h-4 w-4 mr-1" />
                                    Open the spec in {source}
                                </a>
                            )}
                            <p className="text-sm text-gray-600">
                                {counts.epics} epic(s) · {counts.stories} stories · {counts.subtasks} sub-tasks ·{' '}
                                <span className="font-semibold text-green-700">{counts.aiReady} ai-ready</span>
                            </p>
                        </div>

                        {epics.length === 0 ? (
                            <p className="text-gray-600">The analysis produced no epic. Check the content of the spec and try again.</p>
                        ) : (
                            <BacklogPreview epics={epics} />
                        )}

                        {creation.isError ? (
                            <>
                                {/* Next to the buttons: visible even when the preview is long */}
                                <ErrorBox
                                    title="Creation failed"
                                    error={creation.error}
                                    backLabel="Back to the preview"
                                    onBack={() => creation.reset()}
                                    className="mt-6 rounded-xl border border-red-100"
                                />
                                <p className="mt-4 text-sm text-gray-500">
                                    Some issues may already have been created: check the {tracker} project before creating again.
                                </p>
                            </>
                        ) : (
                            <p className="mt-6 text-sm text-gray-500">
                                Nothing has been created yet. The ai-ready verdict is computed again by the server on this content
                                when the issues are created.
                            </p>
                        )}

                        <div className="mt-6 flex flex-col sm:flex-row gap-3">
                            <button
                                onClick={startCreation}
                                disabled={epics.length === 0 || creation.isPending}
                                className="flex-1 flex items-center justify-center px-6 py-4 text-lg font-semibold rounded-xl text-white bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed transition-all shadow-lg"
                            >
                                {creation.isPending ? (
                                    <>
                                        <Loader2 className="h-6 w-6 mr-2 animate-spin" />
                                        Creating issues in {tracker}…
                                    </>
                                ) : (
                                    <>
                                        <Send className="h-6 w-6 mr-2" />
                                        Create in {tracker}
                                    </>
                                )}
                            </button>
                            <button
                                onClick={startOver}
                                disabled={creation.isPending}
                                className="flex items-center justify-center px-6 py-4 text-lg font-semibold rounded-xl text-gray-700 bg-gray-100 hover:bg-gray-200 disabled:cursor-not-allowed transition-all"
                            >
                                <RotateCcw className="h-5 w-5 mr-2" />
                                Start over
                            </button>
                        </div>
                    </div>
                ) : (
                    <form onSubmit={handleSubmit} className="p-8">
                        <div role="radiogroup" aria-label="Spec input" className="mb-6 grid grid-cols-2 gap-2 p-1 bg-gray-100 rounded-xl">
                            {([['page', sourcePage, FileText], ['text', 'Pasted text', ClipboardPaste]] as const).map(([value, label, Icon]) => (
                                <button
                                    key={value}
                                    type="button"
                                    role="radio"
                                    aria-checked={mode === value}
                                    onClick={() => setMode(value)}
                                    className={`flex items-center justify-center px-4 py-3 text-sm font-semibold rounded-lg transition-all ${
                                        mode === value ? 'bg-white text-primary-700 shadow' : 'text-gray-600 hover:text-gray-900'
                                    }`}
                                >
                                    <Icon className="h-4 w-4 mr-2" />
                                    {label}
                                </button>
                            ))}
                        </div>

                        {mode === 'text' ? (
                        <div className="mb-8 space-y-4">
                            <div>
                                <label htmlFor="specTitle" className="block text-sm font-semibold text-gray-900 mb-2">
                                    Title <span className="font-normal text-gray-500">(optional)</span>
                                </label>
                                <input
                                    type="text"
                                    id="specTitle"
                                    value={title}
                                    onChange={(e) => setTitle(e.target.value)}
                                    placeholder="e.g. Inventory management – requirements"
                                    className="block w-full px-4 py-3 border-2 border-gray-200 rounded-xl focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                                />
                            </div>
                            <div>
                                <label htmlFor="specContent" className="block text-sm font-semibold text-gray-900 mb-2">
                                    Spec (Markdown or plain text)
                                </label>
                                <textarea
                                    id="specContent"
                                    value={content}
                                    onChange={(e) => setContent(e.target.value)}
                                    rows={14}
                                    placeholder={'# Inventory management\n\n## Requirements\n- Create an item\n- …'}
                                    aria-describedby="specSize"
                                    aria-invalid={tooLong}
                                    className={`block w-full px-4 py-3 font-mono text-sm border-2 rounded-xl focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all ${
                                        tooLong ? 'border-red-400' : 'border-gray-200'
                                    }`}
                                    required
                                />
                                <p id="specSize" aria-live="polite" className={`mt-2 text-sm flex flex-wrap justify-between gap-2 ${tooLong ? 'text-red-600 font-medium' : 'text-gray-500'}`}>
                                    <span>
                                        {tooLong
                                            ? `Too long by ${NUMBER.format(textSize - maxChars)} character${textSize - maxChars === 1 ? '' : 's'}: shorten the text or split the spec.`
                                            : 'Sent as is to the AI model; nothing is read from ' + source + '.'}
                                    </span>
                                    <span>
                                        {NUMBER.format(textSize)} / {NUMBER.format(maxChars)} characters{title.trim() ? ' (title included)' : ''}
                                    </span>
                                </p>
                            </div>
                        </div>
                        ) : (
                        <div className="mb-8">
                            <label htmlFor="pageId" className="block text-sm font-semibold text-gray-900 mb-3">
                                {help.label}
                            </label>
                            <div className="relative">
                                <div className="absolute inset-y-0 left-0 pl-4 flex items-center pointer-events-none">
                                    <FileText className="h-5 w-5 text-gray-400" />
                                </div>
                                <input
                                    type="text"
                                    id="pageId"
                                    value={pageId}
                                    onChange={(e) => setPageId(e.target.value)}
                                    placeholder={help.placeholder}
                                    className="block w-full pl-12 pr-4 py-4 text-lg border-2 border-gray-200 rounded-xl focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                                    required
                                />
                            </div>
                            <p className="mt-3 text-sm text-gray-500 flex items-center">
                                <span className="inline-block w-1.5 h-1.5 bg-gray-400 rounded-full mr-2" />
                                {help.hint}
                            </p>
                        </div>
                        )}

                        <div className="bg-gradient-to-br from-blue-50 to-indigo-50 border-2 border-blue-100 rounded-xl p-6 mb-8">
                            <h4 className="text-sm font-bold text-blue-900 mb-3 flex items-center">
                                <Sparkles className="h-4 w-4 mr-2" />
                                What happens next?
                            </h4>
                            <ol className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-sm text-blue-800 list-decimal list-inside">
                                <li>{mode === 'page' ? `Fetch the spec from ${source}` : 'Take the pasted spec as is'}</li>
                                <li>Draft Epics, Stories and Sub-tasks with AI</li>
                                <li>Review the preview and each ai-ready verdict</li>
                                <li>Create the backlog in {tracker} when you confirm</li>
                            </ol>
                        </div>

                        <button
                            type="submit"
                            disabled={!canSubmit || preview.isPending}
                            className="w-full flex items-center justify-center px-6 py-4 text-lg font-semibold rounded-xl text-white bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed transition-all shadow-lg hover:shadow-xl"
                        >
                            <Eye className="h-6 w-6 mr-2" />
                            Preview
                        </button>
                    </form>
                )}
            </div>

            <div className="mt-8 text-center">
                <p className="text-sm text-gray-500">
                    {mode === 'page'
                        ? `💡 Tip: make sure the Refinr account can read ${page} and that it contains a well-structured requirements document`
                        : '💡 Tip: headings, lists and tables help the AI model structure the backlog'}
                </p>
            </div>
        </div>
    );
}
