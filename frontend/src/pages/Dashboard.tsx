// SPDX-FileCopyrightText: 2026 Chams Bensalem
// SPDX-License-Identifier: AGPL-3.0-only

import { useNavigate } from 'react-router-dom';
import { FileText, Zap, TrendingUp, ArrowRight, Sparkles } from 'lucide-react';

export default function Dashboard() {
    const navigate = useNavigate();

    return (
        <div className="min-h-screen bg-gradient-to-br from-indigo-100 via-purple-50 to-pink-100">
            {/* Hero Section with Gradient */}
            <div className="relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-r from-purple-600/20 to-pink-600/20 backdrop-blur-3xl" />

                <div className="relative max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pt-20 pb-32">
                    <div className="text-center">
                        <h1 className="text-6xl font-extrabold text-gray-900 mb-6">
                            Transform Your
                            <span className="bg-gradient-to-r from-purple-600 to-pink-600 bg-clip-text text-transparent"> CDC </span>
                            into Jira Magic
                        </h1>

                        <p className="text-xl text-gray-600 mb-10 max-w-3xl mx-auto">
                            Automate your workflow with AI-powered analysis. Convert Confluence requirements into structured Jira tickets.
                        </p>

                        <div className="flex items-center justify-center space-x-4">
                            <button
                                onClick={() => navigate('/analyze')}
                                className="group relative inline-flex items-center px-8 py-4 bg-gradient-to-r from-purple-600 to-pink-600 text-white text-lg font-bold rounded-2xl shadow-2xl hover:shadow-purple-500/50 transform hover:scale-105 transition-all duration-200"
                            >
                                <Zap className="h-5 w-5 mr-2" />
                                Start Analysis
                                <ArrowRight className="h-5 w-5 ml-2 group-hover:translate-x-1 transition-transform" />
                            </button>
                        </div>
                    </div>
                </div>
            </div>

            {/* Features Section */}
            <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 pb-20 -mt-16">
                <div className="text-center mb-16">
                    <h2 className="text-4xl font-bold text-gray-900 mb-4">How It Works</h2>
                    <p className="text-xl text-gray-600">Three simple steps to automate your Jira workflow</p>
                </div>

                <div className="grid grid-cols-1 md:grid-cols-3 gap-8">
                    <div className="relative group">
                        <div className="absolute inset-0 bg-gradient-to-r from-blue-500 to-blue-600 rounded-3xl transform rotate-3 group-hover:rotate-6 transition-transform" />
                        <div className="relative bg-white rounded-3xl shadow-2xl p-8 transform -rotate-1 group-hover:rotate-0 transition-transform">
                            <div className="w-16 h-16 bg-gradient-to-br from-blue-500 to-blue-600 rounded-2xl flex items-center justify-center mb-6">
                                <FileText className="h-8 w-8 text-white" />
                            </div>
                            <h3 className="text-2xl font-bold text-gray-900 mb-3">1. Connect Confluence</h3>
                            <p className="text-gray-600">
                                Simply provide your Confluence page ID. We'll fetch and analyze your requirements automatically.
                            </p>
                        </div>
                    </div>

                    <div className="relative group">
                        <div className="absolute inset-0 bg-gradient-to-r from-purple-500 to-purple-600 rounded-3xl transform rotate-3 group-hover:rotate-6 transition-transform" />
                        <div className="relative bg-white rounded-3xl shadow-2xl p-8 transform -rotate-1 group-hover:rotate-0 transition-transform">
                            <div className="w-16 h-16 bg-gradient-to-br from-purple-500 to-purple-600 rounded-2xl flex items-center justify-center mb-6">
                                <Sparkles className="h-8 w-8 text-white" />
                            </div>
                            <h3 className="text-2xl font-bold text-gray-900 mb-3">2. AI Analysis</h3>
                            <p className="text-gray-600">
                                The configured language model (local Ollama or Gemini) structures your CDC into Epics, Stories and Tasks, and flags which stories are ready for a coding agent.
                            </p>
                        </div>
                    </div>

                    <div className="relative group">
                        <div className="absolute inset-0 bg-gradient-to-r from-pink-500 to-pink-600 rounded-3xl transform rotate-3 group-hover:rotate-6 transition-transform" />
                        <div className="relative bg-white rounded-3xl shadow-2xl p-8 transform -rotate-1 group-hover:rotate-0 transition-transform">
                            <div className="w-16 h-16 bg-gradient-to-br from-pink-500 to-pink-600 rounded-2xl flex items-center justify-center mb-6">
                                <TrendingUp className="h-8 w-8 text-white" />
                            </div>
                            <h3 className="text-2xl font-bold text-gray-900 mb-3">3. Jira Creation</h3>
                            <p className="text-gray-600">
                                Complete ticket hierarchy created in Jira with proper formatting, labels, and priorities.
                            </p>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    );
}