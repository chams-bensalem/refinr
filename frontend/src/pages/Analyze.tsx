import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { analyzeAndCreate, ApiError } from '../api/client';
import { Play, CheckCircle, Loader2, AlertCircle, FileText, Sparkles } from 'lucide-react';

export default function Analyze() {
    const [pageId, setPageId] = useState('');
    const navigate = useNavigate();

    const mutation = useMutation({
        mutationFn: analyzeAndCreate,
        onSuccess: (data) => {
            navigate('/results', { state: data });
        },
    });

    const handleSubmit = (e: React.FormEvent) => {
        e.preventDefault();
        if (pageId.trim()) {
            mutation.mutate({ confluencePageId: pageId.trim() });
        }
    };

    return (
        <div className="max-w-4xl mx-auto">
            {/* Hero Section */}
            <div className="text-center mb-8">
                <div className="inline-flex items-center justify-center w-16 h-16 bg-gradient-to-br from-primary-500 to-primary-700 rounded-2xl mb-4 shadow-lg">
                    <Sparkles className="h-8 w-8 text-white" />
                </div>
                <h1 className="text-4xl font-bold text-gray-900 mb-3">
                    Analyze Your CDC
                </h1>
                <p className="text-xl text-gray-600">
                    Transform requirements into actionable Jira tickets with AI
                </p>
            </div>

            {/* Main Card */}
            <div className="bg-white shadow-2xl rounded-2xl overflow-hidden border border-gray-100">
                {mutation.isError && (
                    <div className="bg-red-50 border-b border-red-100 p-6">
                        <div className="flex items-start">
                            <div className="flex-shrink-0">
                                <AlertCircle className="h-6 w-6 text-red-600" />
                            </div>
                            <div className="ml-3 flex-1">
                                <h3 className="text-sm font-medium text-red-800">Analysis Failed</h3>
                                <div className="mt-2 text-sm text-red-700">
                                    <p>{mutation.error.message || 'Failed to analyze document. Please check your Confluence permissions and try again.'}</p>
                                    {mutation.error instanceof ApiError && mutation.error.correlationId && (
                                        <p className="mt-1 text-xs text-red-500">Reference: {mutation.error.correlationId}</p>
                                    )}
                                </div>
                                <div className="mt-4">
                                    <button
                                        onClick={() => mutation.reset()}
                                        className="text-sm font-medium text-red-600 hover:text-red-500"
                                    >
                                        Try again →
                                    </button>
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {mutation.isPending ? (
                    <div className="p-12">
                        <div className="flex flex-col items-center">
                            <Loader2 className="h-16 w-16 animate-spin text-primary-600 mb-6" />
                            <h3 className="text-xl font-semibold text-gray-900 mb-2">
                                Analyzing Your Document
                            </h3>
                            <p className="text-gray-600 mb-8 text-center max-w-md">
                                Our AI is reading your CDC, identifying requirements, and creating a structured roadmap...
                            </p>

                            {/* Progress Steps */}
                            <div className="w-full max-w-md space-y-4">
                                <div className="flex items-center space-x-3 text-sm">
                                    <div className="flex-shrink-0 w-6 h-6 bg-green-500 rounded-full flex items-center justify-center">
                                        <CheckCircle className="h-4 w-4 text-white" />
                                    </div>
                                    <span className="text-gray-700">Fetching Confluence content</span>
                                </div>
                                <div className="flex items-center space-x-3 text-sm">
                                    <div className="flex-shrink-0 w-6 h-6 bg-primary-500 rounded-full flex items-center justify-center animate-pulse">
                                        <div className="w-2 h-2 bg-white rounded-full" />
                                    </div>
                                    <span className="text-gray-700 font-medium">Analyzing with AI</span>
                                </div>
                                <div className="flex items-center space-x-3 text-sm">
                                    <div className="flex-shrink-0 w-6 h-6 bg-gray-300 rounded-full" />
                                    <span className="text-gray-500">Creating Jira tickets</span>
                                </div>
                            </div>
                        </div>
                    </div>
                ) : mutation.isSuccess ? (
                    <div className="p-12 text-center">
                        <div className="inline-flex items-center justify-center w-20 h-20 bg-green-100 rounded-full mb-6">
                            <CheckCircle className="h-10 w-10 text-green-600" />
                        </div>
                        <h3 className="text-2xl font-bold text-gray-900 mb-2">
                            Analysis Complete!
                        </h3>
                        <p className="text-gray-600 mb-8 max-w-md mx-auto">
                            Your Jira tickets have been created successfully. Ready to review your roadmap?
                        </p>
                        <button
                            onClick={() => navigate('/results', { state: mutation.data })}
                            className="inline-flex items-center px-6 py-3 border border-transparent text-base font-medium rounded-lg shadow-sm text-white bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 transition-all"
                        >
                            View Results
                        </button>
                    </div>
                ) : (
                    <form onSubmit={handleSubmit} className="p-8">
                        {/* Input Section */}
                        <div className="mb-8">
                            <label htmlFor="pageId" className="block text-sm font-semibold text-gray-900 mb-3">
                                Confluence Page ID
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
                                    placeholder="Enter page ID (e.g., 123456)"
                                    className="block w-full pl-12 pr-4 py-4 text-lg border-2 border-gray-200 rounded-xl focus:ring-2 focus:ring-primary-500 focus:border-transparent transition-all"
                                    required
                                />
                            </div>
                            <p className="mt-3 text-sm text-gray-500 flex items-center">
                                <span className="inline-block w-1.5 h-1.5 bg-gray-400 rounded-full mr-2" />
                                Find the page ID in your Confluence URL
                            </p>
                        </div>

                        {/* Info Box */}
                        <div className="bg-gradient-to-br from-blue-50 to-indigo-50 border-2 border-blue-100 rounded-xl p-6 mb-8">
                            <h4 className="text-sm font-bold text-blue-900 mb-3 flex items-center">
                                <Sparkles className="h-4 w-4 mr-2" />
                                What happens next?
                            </h4>
                            <div className="grid grid-cols-2 gap-3 text-sm text-blue-800">
                                <div className="flex items-start">
                                    <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full mt-1.5 mr-2 flex-shrink-0" />
                                    <span>Fetch CDC from Confluence</span>
                                </div>
                                <div className="flex items-start">
                                    <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full mt-1.5 mr-2 flex-shrink-0" />
                                    <span>AI analysis of the document</span>
                                </div>
                                <div className="flex items-start">
                                    <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full mt-1.5 mr-2 flex-shrink-0" />
                                    <span>Generate Epics & Stories</span>
                                </div>
                                <div className="flex items-start">
                                    <span className="inline-block w-1.5 h-1.5 bg-blue-500 rounded-full mt-1.5 mr-2 flex-shrink-0" />
                                    <span>Create Jira hierarchy</span>
                                </div>
                            </div>
                        </div>

                        {/* Submit Button */}
                        <button
                            type="submit"
                            disabled={!pageId.trim() || mutation.isPending}
                            className="w-full flex items-center justify-center px-6 py-4 border border-transparent text-lg font-semibold rounded-xl text-white bg-gradient-to-r from-primary-600 to-primary-700 hover:from-primary-700 hover:to-primary-800 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-primary-500 disabled:from-gray-400 disabled:to-gray-500 disabled:cursor-not-allowed transition-all shadow-lg hover:shadow-xl"
                        >
                            <Play className="h-6 w-6 mr-2" />
                            Start Analysis
                        </button>
                    </form>
                )}
            </div>

            {/* Tips */}
            <div className="mt-8 text-center">
                <p className="text-sm text-gray-500">
                    💡 Tip: Make sure your Confluence page is accessible and contains a well-structured CDC
                </p>
            </div>
        </div>
    );
}