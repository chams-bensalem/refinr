import { useLocation, useNavigate } from 'react-router-dom';
import { CheckCircle2, Target, FileText, ListChecks, ExternalLink, ArrowRight } from 'lucide-react';

interface AnalysisResult {
    epicsCreated: number;
    storiesCreated: number;
    subtasksCreated: number;
    jiraBoardUrl: string;
}

export default function Results() {
    const location = useLocation();
    const navigate = useNavigate();
    const results = location.state as AnalysisResult;

    if (!results) {
        navigate('/analyze');
        return null;
    }

    const stats = [
        {
            label: 'Epics Created',
            value: results.epicsCreated,
            icon: Target,
            color: 'text-purple-600',
            bgColor: 'bg-purple-100'
        },
        {
            label: 'Stories Created',
            value: results.storiesCreated,
            icon: FileText,
            color: 'text-blue-600',
            bgColor: 'bg-blue-100'
        },
        {
            label: 'Subtasks Created',
            value: results.subtasksCreated,
            icon: ListChecks,
            color: 'text-green-600',
            bgColor: 'bg-green-100'
        }
    ];

    return (
        <div className="min-h-screen bg-gradient-to-br from-violet-50 via-white to-rose-50 py-12 px-4">
            <div className="max-w-6xl mx-auto space-y-8">

                <div className="bg-green-50 border-l-4 border-green-500 p-6 rounded-lg shadow-sm">
                    <div className="flex items-center gap-3">
                        <CheckCircle2 className="w-8 h-8 text-green-600" />
                        <div>
                            <h1 className="text-2xl font-bold text-green-900">Analysis Complete!</h1>
                            <p className="text-green-700 mt-1">All Jira tickets have been created successfully</p>
                        </div>
                    </div>
                </div>

                <div className="grid md:grid-cols-3 gap-6">
                    {stats.map((stat, index) => (
                        <div key={index} className="bg-white rounded-xl shadow-md p-6 hover:shadow-lg transition-shadow">
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

                <div className="bg-white rounded-xl shadow-md p-8">
                    <h2 className="text-2xl font-bold text-gray-900 mb-6">Next Steps</h2>

                    <a
                        href={results.jiraBoardUrl}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="flex items-center justify-between p-4 border border-gray-200 rounded-lg hover:bg-gray-50 transition-colors mb-4"
                    >
                        <div className="flex items-center gap-3">
                            <ExternalLink className="w-5 h-5 text-blue-600" />
                            <div>
                                <p className="font-semibold text-gray-900">View Jira Board</p>
                                <p className="text-sm text-gray-500">Open your project in Jira</p>
                            </div>
                        </div>
                        <ArrowRight className="w-5 h-5 text-gray-400" />
                    </a>

                    <button
                        onClick={() => navigate('/analyze')}
                        className="w-full px-6 py-3 bg-white border-2 border-blue-600 text-blue-600 rounded-lg font-semibold hover:bg-blue-50 transition-all"
                    >
                        Start New Analysis
                    </button>
                </div>

                <div className="bg-gradient-to-r from-violet-500 to-rose-500 rounded-xl shadow-xl p-8 text-white">
                    <h3 className="text-2xl font-bold mb-4">🎉 Great Job!</h3>
                    <p className="text-lg opacity-90">
                        You've successfully created {results.epicsCreated + results.storiesCreated + results.subtasksCreated} Jira tickets from your Confluence documentation. Your team can now start working on these tasks!
                    </p>
                </div>

            </div>
        </div>
    );
}