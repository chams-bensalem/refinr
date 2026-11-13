// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { Link } from 'react-router-dom';
import { Activity } from 'lucide-react';
import { useQuery } from '@tanstack/react-query';
import { getHealth } from '../api/client';

export default function Header() {
    const { data: health } = useQuery({
        queryKey: ['health'],
        queryFn: getHealth,
        refetchInterval: 30000,
    });

    const getStatusColor = (status?: string) => {
        if (!status) return 'bg-gray-400';
        if (status === 'UP') return 'bg-green-500';
        if (status === 'DEGRADED') return 'bg-yellow-500';
        return 'bg-red-500';
    };

    return (
        <header className="bg-white/80 backdrop-blur-md border-b border-gray-200 sticky top-0 z-50">
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
                <div className="flex items-center justify-between h-16">
                    <Link to="/" className="flex items-center gap-3">
                        <div className="w-10 h-10 bg-gradient-to-br from-violet-600 to-rose-600 rounded-lg flex items-center justify-center">
                            <span className="text-white font-bold text-xl">R</span>
                        </div>
                        <span className="text-xl font-bold bg-gradient-to-r from-violet-600 to-rose-600 bg-clip-text text-transparent">
              Refinr
            </span>
                    </Link>

                    <nav className="flex items-center gap-6">
                        <Link
                            to="/analyze"
                            className="text-gray-600 hover:text-gray-900 font-medium transition-colors"
                        >
                            Analyze a spec
                        </Link>
                    </nav>

                    <div className="flex items-center gap-4">
                        {/* Backend Health */}
                        <div className="flex items-center gap-2">
                            <Activity className="w-4 h-4 text-gray-600" />
                            <div className="flex gap-1">
                                <div
                                    className={`w-2 h-2 rounded-full ${getStatusColor(health?.status)}`}
                                    title="Refinr Service"
                                />
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </header>
    );
}