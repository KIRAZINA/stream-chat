/**
 * UserContextMenu — right-click context menu with moderation actions.
 */

import { useEffect, useRef } from 'react';
import { ChatMessageDTO } from '../../types/backend';
import { useModeration } from '../../hooks/useModeration';

interface UserContextMenuProps {
  message: ChatMessageDTO | null;
  x: number;
  y: number;
  isOpen: boolean;
  onClose: () => void;
  streamKey: string;
  canModerate: boolean;
}

export function UserContextMenu({
  message,
  x,
  y,
  isOpen,
  onClose,
  streamKey,
  canModerate,
}: UserContextMenuProps) {
  const menuRef = useRef<HTMLDivElement>(null);
  const { deleteMessage, timeoutUser, pinMessage } = useModeration(streamKey);

  useEffect(() => {
    const handleClickOutside = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        onClose();
      }
    };
    if (isOpen) document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, [isOpen, onClose]);

  if (!isOpen || !message) return null;

  return (
    <div
      ref={menuRef}
      style={{ top: y, left: x }}
      className="fixed z-50 min-w-[180px] rounded-2xl border border-slate-700 bg-slate-900 shadow-xl py-1"
    >
      <button
        className="w-full text-left px-4 py-2 text-sm text-slate-300 hover:bg-slate-800 transition-colors"
        onClick={() => {
          navigator.clipboard.writeText(message.content);
          onClose();
        }}
      >
        📋 Copy message
      </button>

      {canModerate && (
        <>
          <div className="my-1 border-t border-slate-700" />

          <button
            className="w-full text-left px-4 py-2 text-sm text-yellow-400 hover:bg-slate-800 transition-colors"
            onClick={() => {
              pinMessage({ messageId: message.id, pin: !message.isPinned });
              onClose();
            }}
          >
            {message.isPinned ? '📌 Unpin message' : '📌 Pin message'}
          </button>

          <button
            className="w-full text-left px-4 py-2 text-sm text-orange-400 hover:bg-slate-800 transition-colors"
            onClick={() => {
              timeoutUser({ username: message.username, durationSeconds: 300 });
              onClose();
            }}
          >
            ⏱ Timeout 5 min
          </button>

          <button
            className="w-full text-left px-4 py-2 text-sm text-red-400 hover:bg-slate-800 transition-colors"
            onClick={() => {
              deleteMessage(message.id);
              onClose();
            }}
          >
            🗑️ Delete message
          </button>
        </>
      )}
    </div>
  );
}
