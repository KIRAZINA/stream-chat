/**
 * ModPanel — collapsible moderation sidebar, shown only to MODERATOR/BROADCASTER/ADMIN.
 */

import { useState } from 'react';
import { useAuth } from '../../hooks/useAuth';
import { useModeration } from '../../hooks/useModeration';
import ModerationLog from './ModerationLog';
import UserList from './UserList';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../ui/Tabs';

interface ModPanelProps {
  streamKey: string;
}

type Tab = 'users' | 'actions' | 'logs';

export function ModPanel({ streamKey }: ModPanelProps) {
  const { user, hasRole } = useAuth();
  const { timeoutUser, banUser, isTimingOut, isBanning } = useModeration(streamKey);
  const [activeTab, setActiveTab] = useState<Tab>('actions');
  const [targetUsername, setTargetUsername] = useState('');
  const [timeoutSecs, setTimeoutSecs] = useState(300);
  const [banReason, setBanReason] = useState('');

  const canModerate =
    hasRole('ROLE_ADMIN') ||
    hasRole('ROLE_MODERATOR') ||
    hasRole('ROLE_BROADCASTER');

  if (!user || !canModerate) return null;

  const handleTimeout = () => {
    if (!targetUsername.trim()) return;
    timeoutUser({ username: targetUsername.trim(), durationSeconds: timeoutSecs });
    setTargetUsername('');
  };

  const handleBan = () => {
    if (!targetUsername.trim()) return;
    banUser({ username: targetUsername.trim(), permanent: false, reason: banReason || undefined });
    setTargetUsername('');
    setBanReason('');
  };

  return (
    <div className="rounded-3xl border border-slate-700 bg-slate-900/90 p-5 shadow-glow">
      <h2 className="text-lg font-semibold text-slate-100">🛡️ Mod Panel</h2>

      {/* Tab bar */}
      <Tabs defaultValue="actions">
        <TabsList className="grid grid-cols-3 mt-4">
          <TabsTrigger value="users">👥 Users</TabsTrigger>
          <TabsTrigger value="actions">⚡ Actions</TabsTrigger>
          <TabsTrigger value="logs">📋 Logs</TabsTrigger>
        </TabsList>
        
        <TabsContent value="users">
          <UserList streamKey={streamKey} />
        </TabsContent>
        
        <TabsContent value="actions">
          <div className="mt-4 space-y-4">
          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">Target username</label>
            <input
              type="text"
              value={targetUsername}
              onChange={(e) => setTargetUsername(e.target.value)}
              placeholder="Username"
              className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
            />
          </div>

          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">
              Timeout duration (seconds)
            </label>
            <input
              type="number"
              value={timeoutSecs}
              onChange={(e) => setTimeoutSecs(Number(e.target.value))}
              min={1}
              className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white focus:outline-none focus:ring-2 focus:ring-yellow-500"
            />
          </div>

          <button
            onClick={handleTimeout}
            disabled={!targetUsername.trim() || isTimingOut}
            className="w-full px-4 py-2 rounded-xl bg-yellow-500/20 border border-yellow-500/40 text-yellow-400 text-sm font-medium hover:bg-yellow-500/30 transition disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isTimingOut ? 'Timing out…' : '⏱ Timeout User'}
          </button>

          <div>
            <label className="block text-sm font-medium text-slate-300 mb-1">
              Ban reason (optional)
            </label>
            <input
              type="text"
              value={banReason}
              onChange={(e) => setBanReason(e.target.value)}
              placeholder="Reason"
              className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-lg text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-red-500"
            />
          </div>

          <button
            onClick={handleBan}
            disabled={!targetUsername.trim() || isBanning}
            className="w-full px-4 py-2 rounded-xl bg-red-500/20 border border-red-500/40 text-red-400 text-sm font-medium hover:bg-red-500/30 transition disabled:opacity-40 disabled:cursor-not-allowed"
          >
            {isBanning ? 'Banning…' : '🔨 Ban User'}
          </button>
          </div>
        </TabsContent>
        
        <TabsContent value="logs">
          <ModerationLog streamKey={streamKey} />
        </TabsContent>
      </Tabs>
    </div>
  );
}
