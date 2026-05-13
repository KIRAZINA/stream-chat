/**
 * MessageItem — renders a single chat message with all visual states:
 * normal, deleted, pinned, reply-preview, badges, and context-menu trigger.
 */

import React from 'react';
import { ChatMessageDTO } from '../../types/backend';
import { formatTimestamp } from '../../utils/time';
import { getRoleBadge } from '../../utils/permissions';
import { renderFragments, linkifyText } from '../../utils/emotes';

interface MessageItemProps {
  message: ChatMessageDTO;
  isOwnMessage?: boolean;
  onReply?: (messageId: number, username: string, content: string) => void;
  onRequestContextMenu?: (message: ChatMessageDTO, x: number, y: number) => void;
  showAvatar?: boolean;
}

export function MessageItem({
  message,
  isOwnMessage = false,
  onReply,
  onRequestContextMenu,
  showAvatar = true,
}: MessageItemProps) {
  const handleContextMenu = (e: React.MouseEvent) => {
    e.preventDefault();
    if (onRequestContextMenu) onRequestContextMenu(message, e.clientX, e.clientY);
  };

  return (
    <article
      className={`group flex gap-3 px-4 py-2 transition-colors hover:bg-slate-800/50 ${
        message.isPinned ? 'border-l-2 border-yellow-400 bg-yellow-400/5' : ''
      } ${message.isDeleted ? 'opacity-50' : ''}`}
      onContextMenu={handleContextMenu}
    >
      {/* Avatar */}
      <div className="flex-shrink-0 pt-0.5">
        <div
          className="h-9 w-9 rounded-full bg-gradient-to-br from-blue-500 to-purple-600 flex items-center justify-center text-white text-sm font-semibold select-none"
          title={message.username}
        >
          {message.username.charAt(0).toUpperCase()}
        </div>
      </div>

      {/* Message content */}
      <div className="flex-1 min-w-0">
        {/* Header row */}
        <div className="flex flex-wrap items-baseline gap-x-2 mb-0.5">
          <span
            className={`font-semibold text-sm ${
              isOwnMessage ? 'text-blue-400' : 'text-slate-200'
            }`}
            style={message.color ? { color: message.color } : undefined}
          >
            {message.username}
          </span>

          {/* Role badges */}
          {message.badges && message.badges.length > 0 && (
            <span className="text-xs">
              {message.badges.map((badge) => (
                <span key={badge} className="ml-1">
                  {getRoleBadge(badge)}
                </span>
              ))}
            </span>
          )}

          <span className="text-xs text-slate-500">{formatTimestamp(message.timestamp)}</span>

          {message.isPinned && (
            <span className="text-xs text-yellow-400" title="Pinned message">
              📌
            </span>
          )}
        </div>

        {/* Reply preview */}
        {message.replyToUsername && (
          <div className="mb-1 text-xs text-slate-400 border-l-2 border-slate-600 pl-2">
            <span className="font-semibold">@{message.replyToUsername}</span>
            {message.replyToContentPreview && (
              <span className="ml-1 truncate">{message.replyToContentPreview}</span>
            )}
          </div>
        )}

        {/* Message body */}
        {message.isDeleted ? (
          <span className="text-gray-500 italic line-through text-sm">💀 Message deleted</span>
        ) : message.fragments?.length ? (
          <p className="break-words text-gray-100">{renderFragments(message.fragments)}</p>
        ) : (
          <p className="break-words text-gray-100">{linkifyText(message.content)}</p>
        )}

        {/* Pinned-by attribution */}
        {message.isPinned && message.pinnedByUsername && (
          <div className="mt-0.5 text-xs text-yellow-500/70">
            Pinned by {message.pinnedByUsername}
          </div>
        )}
      </div>

      {/* Hover reply button */}
      {!message.isDeleted && onReply && (
        <button
          onClick={() => onReply(message.id, message.username, message.content)}
          className="opacity-0 group-hover:opacity-100 transition-opacity flex-shrink-0 self-start mt-1 text-slate-400 hover:text-blue-400"
          title="Reply"
          aria-label="Reply to message"
        >
          <svg className="w-4 h-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2}
              d="M3 10h10a8 8 0 018 8v2M3 10l6 6m-6-6l6-6" />
          </svg>
        </button>
      )}
    </article>
  );
}
