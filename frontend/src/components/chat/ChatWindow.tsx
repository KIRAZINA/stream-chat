/**
 * ChatWindow — main chat layout container.
 * Manages STOMP connection state and orchestrates MessageList + MessageInput.
 */

import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import MessageList from './MessageList';
import MessageInput from './MessageInput';
import SystemMessage from './SystemMessage';
import { UserContextMenu } from '../moderation/UserContextMenu';
import { useStompChat } from '../../hooks/useStompChat';
import { useAuthStore } from '../../stores/auth-store';
import { fetchSettings } from '../../api/streams';
import { ChatMessageDTO, StreamSettings } from '../../types/backend';

interface ChatWindowProps {
  streamKey: string;
}

export default function ChatWindow({ streamKey }: ChatWindowProps) {
  const [connectionStatus, setConnectionStatus] = useState<
    'connecting' | 'connected' | 'disconnected' | 'error'
  >('connecting');
  const [contextMenu, setContextMenu] = useState<{ message: ChatMessageDTO; x: number; y: number } | null>(null);

  const { connectionState, sendMessage, messages } = useStompChat(streamKey);
  const { user, hasRole } = useAuthStore();

  // Shares the react-query cache with the settings sidebar so the char limit
  // and counter appear in the input without a second request. Non-broadcasters
  // get the same 403 the settings form already surfaces; the server still
  // enforces the limit and rejection feedback arrives via the STOMP error queue.
  const { data: settings } = useQuery<StreamSettings>({
    queryKey: ['stream-settings', streamKey],
    queryFn: () => fetchSettings(streamKey),
    enabled: Boolean(streamKey),
  });

  const canModerate = hasRole('ROLE_MODERATOR') || hasRole('ROLE_ADMIN') || hasRole('ROLE_BROADCASTER');

  useEffect(() => {
    setConnectionStatus(connectionState);
  }, [connectionState]);

  const handleSend = (content: string, replyTo?: number) => {
    sendMessage(content, replyTo);
  };

  const statusLabel: Record<typeof connectionStatus, string> = {
    connecting: 'Connecting to chat...',
    connected: 'Connected',
    disconnected: 'Disconnected',
    error: 'Connection error',
  };

  const statusColor: Record<typeof connectionStatus, string> = {
    connecting: 'text-yellow-400',
    connected: 'text-green-400',
    disconnected: 'text-slate-500',
    error: 'text-red-400',
  };

  return (
    <div className="grid h-full gap-4 rounded-3xl border border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-900/90 p-4"
         style={{ gridTemplateRows: '56px 1fr auto auto' }}>
      {/* Header */}
      <div className="flex items-center justify-between rounded-2xl border border-gray-200 dark:border-slate-700 bg-gray-100 dark:bg-slate-950/80 px-4 py-3">
        <div>
          <h2 className="text-lg font-semibold text-slate-900 dark:text-slate-100">Stream Chat</h2>
          <p className={`text-sm ${statusColor[connectionStatus]}`}>
            {statusLabel[connectionStatus]}
          </p>
        </div>
        <span
          className={`h-2.5 w-2.5 rounded-full ${
            connectionStatus === 'connected'
              ? 'bg-green-400 animate-pulse'
              : connectionStatus === 'connecting'
              ? 'bg-yellow-400 animate-pulse'
              : connectionStatus === 'error'
              ? 'bg-red-400'
              : 'bg-slate-500'
          }`}
        />
      </div>

      {/* Message list */}
      <div className="flex min-h-[380px] flex-col overflow-hidden rounded-2xl border border-gray-200 dark:border-slate-700 bg-gray-50 dark:bg-slate-950/80">
        <MessageList 
          messages={messages}
          currentUserId={user?.id || 0}
          onRequestContextMenu={(msg, x, y) => setContextMenu({ message: msg, x, y })}
        />
      </div>

      {/* System notice */}
      {connectionStatus === 'connected' && (
        <SystemMessage
          type="info"
          message="Messages are sent in real-time via STOMP WebSocket"
        />
      )}
      {connectionStatus === 'error' && (
        <SystemMessage
          type="error"
          message="Connection failed. Refresh the page to reconnect."
        />
      )}

      {/* Message input */}
      <MessageInput
        onSend={handleSend}
        disabled={connectionStatus !== 'connected'}
        maxLength={settings?.maxMessageLength}
      />

      {/* Context menu */}
      {contextMenu && (
        <UserContextMenu 
          message={contextMenu.message}
          x={contextMenu.x} y={contextMenu.y}
          isOpen={true}
          onClose={() => setContextMenu(null)}
          streamKey={streamKey}
          canModerate={canModerate}
        />
      )}
    </div>
  );
}
