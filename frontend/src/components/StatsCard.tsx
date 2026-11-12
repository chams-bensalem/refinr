import type {LucideIcon} from 'lucide-react';

interface StatsCardProps {
    title: string;
    value: number;
    icon: LucideIcon;
    color: string;
}

export default function StatsCard({ title, value, icon: Icon, color }: StatsCardProps) {
    return (
        <div className="bg-white rounded-lg shadow p-6">
            <div className="flex items-center justify-between">
                <div>
                    <p className="text-sm font-medium text-gray-600">{title}</p>
                    <p className="mt-2 text-3xl font-semibold text-gray-900">{value}</p>
                </div>
                <div className={`p-3 rounded-full ${color}`}>
                    <Icon className="h-6 w-6 text-white" />
                </div>
            </div>
        </div>
    );
}