/**
 * TrustScoreBadge — displays AutoMod trust score indicator for a user.
 */

interface TrustScoreBadgeProps {
  score: number; // 0–100
  username?: string;
}

export function TrustScoreBadge({ score, username }: TrustScoreBadgeProps) {
  const getColor = (s: number) => {
    if (s >= 80) return 'text-green-400 border-green-500/40 bg-green-500/10';
    if (s >= 50) return 'text-yellow-400 border-yellow-500/40 bg-yellow-500/10';
    return 'text-red-400 border-red-500/40 bg-red-500/10';
  };

  const label = score >= 80 ? 'Trusted' : score >= 50 ? 'Neutral' : 'Flagged';

  return (
    <span
      className={`inline-flex items-center gap-1.5 rounded-full border px-2.5 py-0.5 text-xs font-medium ${getColor(score)}`}
      title={username ? `${username} trust score: ${score}/100` : `Trust score: ${score}/100`}
    >
      🛡️ {label} ({score})
    </span>
  );
}
