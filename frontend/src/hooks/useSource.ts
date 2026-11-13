// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { useQuery } from '@tanstack/react-query';
import { getSource } from '../api/client';

/** Generic label shown until the backend answers (or if it cannot be reached). */
export const GENERIC_SOURCE = 'your wiki';

/** Backend default of refinr.document.max-content-chars, used until the backend answers (or if it is older than 1.3). */
export const DEFAULT_MAX_CONTENT_CHARS = 200_000;

function useSourceInfo() {
    return useQuery({
        queryKey: ['source'],
        queryFn: getSource,
        staleTime: Infinity,
    }).data;
}

/** Name of the active document source (Confluence, BookStack or XWiki), read once from the backend. */
export function useSourceName(): string {
    return useSourceInfo()?.name ?? GENERIC_SOURCE;
}

/** Maximum size of a document provided as text (Unicode characters, title included), read once from the backend. */
export function useMaxContentChars(): number {
    return useSourceInfo()?.maxContentChars ?? DEFAULT_MAX_CONTENT_CHARS;
}

/**
 * Size of a provided text as the backend counts it: Unicode characters (code points) of the content, plus the
 * "# title" line and the blank line that follows it when a title is given.
 */
export function providedTextSize(title: string, content: string): number {
    const heading = title.replace(/\s+/g, ' ').trim();
    // Code points: a surrogate pair (emoji, rare letters) counts as one character, as on the server
    const codePoints = (text: string) => text.length - (text.match(/[\uD800-\uDBFF][\uDC00-\uDFFF]/g)?.length ?? 0);
    return codePoints(content) + (heading ? codePoints(heading) + 4 : 0);
}

/** Labels and help to enter the identifier of a document in the active source. */
export interface DocumentIdHelp {
    label: string;
    placeholder: string;
    hint: string;
}

export function documentIdHelp(source: string): DocumentIdHelp {
    switch (source) {
        case 'Confluence':
            return {
                label: 'Confluence page ID',
                placeholder: 'e.g. 123456789',
                hint: 'The number in the page URL: …/pages/123456789/…',
            };
        case 'BookStack':
            return {
                label: 'BookStack page ID',
                placeholder: 'e.g. 42',
                hint: 'Select some text on the page: the permalink shown is …/link/42, and 42 is the page ID.',
            };
        case 'XWiki':
            return {
                label: 'XWiki page reference',
                placeholder: 'e.g. Specs.Inventory.WebHome or Specs.Inventory',
                hint: 'Information tab at the bottom of the page → Page reference, or the page path /bin/view/Specs/Inventory/ written with dots.',
            };
        default:
            return {
                label: 'Document ID',
                placeholder: 'Identifier of the page in your wiki',
                hint: 'The identifier of the page that holds your spec.',
            };
    }
}
