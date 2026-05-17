/**
 * ModerationLog — paginated table of moderation actions for a stream.
 */

import { useQuery } from '@tanstack/react-query';
import { moderationApi } from '../../api/moderation';
import { formatTimestamp } from '../../utils/time';
import type { ModerationLog } from '../../types/backend';

interface ModerationLogProps {
  streamKey: string;
}

export default function ModerationLog({ streamKey }: ModerationLogProps) {
  const { data: logs = [], isLoading } = useQuery({
    queryKey: ['moderation-logs', streamKey],
    queryFn: () => moderationApi.getModerationLogs(streamKey),
    refetchInterval: 30_000,
  });

  if (isLoading) {
    return (
      <div className="mt-4 space-y-2">
        {[...Array(5)].map((_, i) => (
          <div key={i} className="h-10 animate-pulse rounded-xl bg-slate-800" />
        ))}
      </div>
    );
  }

  if (logs.length === 0) {
    return (
      <div className="mt-4 rounded-2xl border border-dashed border-slate-700 py-10 text-center text-sm text-slate-500">
        No moderation actions recorded yet.
      </div>
    );
  }

  return (
    <div className="mt-4 overflow-auto max-h-72 rounded-2xl border border-slate-700">
      <table className="w-full text-sm text-left">
        <thead className="sticky top-0 bg-slate-900 text-slate-400 text-xs uppercase">
          <tr>
            <th className="px-4 py-2">Action</th>
            <th className="px-4 py-2">Target</th>
            <th className="px-4 py-2">By</th>
            <th className="px-4 py-2">When</th>
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-800">
          {logs.map((log: ModerationLog) => (
            <tr key={log.id} className="hover:bg-slate-800/50 transition-colors">
              <td className="px-4 py-2 font-medium text-slate-300">{log.action}</td>
              <td className="px-4 py-2 text-slate-400">{log.targetUsername ?? '—'}</td>
              <td className="px-4 py-2 text-slate-400">{log.moderatorUsername}</td>
              <td className="px-4 py-2 text-slate-500 whitespace-nowrap">
                {formatTimestamp(log.createdAt)}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
