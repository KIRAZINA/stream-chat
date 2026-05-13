/**
 * StreamCard — dashboard list item for a single stream.
 */

import { StreamDTO } from '../../types/backend';
import { useNavigate } from 'react-router-dom';
import { Button } from '../ui/Button';
import PresenceIndicator from './PresenceIndicator';

interface StreamCardProps {
  stream: StreamDTO;
}

export default function StreamCard({ stream }: StreamCardProps) {
  const navigate = useNavigate();

  const isLive = stream.status === 'LIVE';

  return (
    <div className="rounded-3xl border border-slate-700 bg-slate-900/80 p-5 shadow-glow transition hover:-translate-y-1 hover:border-sky-500">
      <div className="flex items-start justify-between gap-3">
        <div className="flex-1 min-w-0">
          <h3 className="text-xl font-semibold text-slate-100 truncate">{stream.title}</h3>
          <p className="text-sm text-slate-500 mt-1 truncate">
            {stream.ownerUsername} • {stream.category || 'Just Chatting'}
          </p>
          <div className="flex items-center gap-3 mt-2">
            <span
              className={`text-sm font-medium ${
                isLive ? 'text-red-400' : 'text-slate-500'
              }`}
            >
              {isLive ? '🔴 LIVE' : '⚫ OFFLINE'}
            </span>
            <PresenceIndicator viewers={stream.viewerCount ?? 0} />
          </div>
        </div>
      </div>

      <Button
        onClick={() => navigate(`/stream/${stream.streamKey}`)}
        className="mt-5 w-full"
        variant={isLive ? 'primary' : 'secondary'}
      >
        {isLive ? 'Watch Stream' : 'View Chat'}
      </Button>
    </div>
  );
}
