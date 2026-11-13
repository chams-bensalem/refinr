// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { useQuery } from '@tanstack/react-query';
import { getSource } from '../api/client';

/** Generic label shown until the backend answers (or if it cannot be reached). */
export const GENERIC_SOURCE = 'your wiki';

/** Name of the active document source (Confluence, BookStack or XWiki), read once from the backend. */
export function useSourceName(): string {
    const { data } = useQuery({
        queryKey: ['source'],
        queryFn: getSource,
        staleTime: Infinity,
    });
    return data?.name ?? GENERIC_SOURCE;
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
