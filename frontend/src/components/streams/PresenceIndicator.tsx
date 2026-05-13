/**
 * PresenceIndicator component for displaying viewer count
 */

import { formatViewerCount } from '../../utils/time';

interface PresenceIndicatorProps {
  viewers: number;
}

export default function PresenceIndicator({ viewers }: PresenceIndicatorProps) {
  return (
    <div className="inline-flex items-center gap-2 rounded-full bg-emerald-600/10 px-3 py-1 text-sm text-emerald-100">
      <span className="h-2.5 w-2.5 rounded-full bg-emerald-400 animate-pulse" />
      {formatViewerCount(viewers)} watching
    </div>
  );
}
