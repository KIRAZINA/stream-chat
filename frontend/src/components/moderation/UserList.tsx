import { useEffect, useState } from 'react';
import { moderationApi } from '../../api/moderation';

interface StreamModerator {
  id: number;
  userId: number;
  user: { username: string };
  role: string;
}

export default function UserList({ streamKey }: { streamKey: string }) {
  const [users, setUsers] = useState<StreamModerator[]>([]);
  useEffect(() => {
    moderationApi.getModerators(streamKey).then((roles) => setUsers(roles as unknown as StreamModerator[]));
  }, [streamKey]);

  return (
    <div className="p-3 space-y-2 max-h-64 overflow-y-auto">
      {users.length === 0 ? (
        <div className="text-slate-400 text-sm">No moderators found</div>
      ) : (
        users.map((user) => (
          <div key={user.id} className="flex items-center justify-between p-2 bg-slate-800 rounded-lg">
            <div className="flex items-center gap-2">
              <div className="w-6 h-6 rounded-full bg-slate-600 flex items-center justify-center text-xs text-white">
                {user.user?.username?.[0]?.toUpperCase() || '?'}
              </div>
              <span className="text-slate-200 font-medium">{user.user?.username}</span>
            </div>
            <span className="text-xs bg-slate-700 px-2 py-0.5 rounded text-slate-300">
              {user.role?.replace('ROLE_', '') || 'MOD'}
            </span>
          </div>
        ))
      )}
    </div>
  );
}
