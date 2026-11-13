// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { useQuery } from '@tanstack/react-query';
import { getTracker } from '../api/client';

/** Generic label shown until the backend answers (or if it cannot be reached). */
export const GENERIC_TRACKER = 'your issue tracker';

/** Name of the active issue tracker (Jira or OpenProject), read once from the backend. */
export function useTrackerName(): string {
    const { data } = useQuery({
        queryKey: ['tracker'],
        queryFn: getTracker,
        staleTime: Infinity,
    });
    return data?.name ?? GENERIC_TRACKER;
}
