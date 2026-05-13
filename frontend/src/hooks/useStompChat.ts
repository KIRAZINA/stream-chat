import { useEffect, useState, useCallback } from 'react';
import { useAuthStore } from '../stores/auth-store';
import { StreamStompClient } from '../services/stomp-client';
import { streamsApi } from '../api/streams';
import { ChatMessageDTO } from '../types/backend';

const stomp = new StreamStompClient(import.meta.env.VITE_WS_URL);

function deduplicate(existing: ChatMessageDTO[], incoming: ChatMessageDTO[]): ChatMessageDTO[] {
  const existingIds = new Set(existing.map(m => m.id));
  const existingTempKeys = new Set(existing.filter(m => !m.id).map(m => m.idempotencyKey));
  return incoming.filter(m => {
    if (m.id && existingIds.has(m.id)) return false;
    if (m.idempotencyKey && existingTempKeys.has(m.idempotencyKey)) return false;
    return true;
  });
}

export function useStompChat(streamKey: string) {
  const { token } = useAuthStore();
  const [messages, setMessages] = useState<ChatMessageDTO[]>([]);
  const [connectionState, setConnectionState] = useState<'disconnected' | 'connecting' | 'connected'>('disconnected');
  const [lastSequenceId, setLastSequenceId] = useState<number | null>(null);

  useEffect(() => {
    if (!token || !streamKey) return;
    stomp.setAuthToken(token);
    setConnectionState('connecting');

    stomp.connect()
      .then(() => {
        setConnectionState('connected');
        stomp.publish(`/app/chat.join/${streamKey}`, { streamKey });

        stomp.subscribe<ChatMessageDTO>(`/topic/stream/${streamKey}`, (msg) => {
          setMessages(prev => [...prev, msg]);
          if (msg.redisSequenceId) setLastSequenceId(msg.redisSequenceId);
        });
      })
      .catch(() => setConnectionState('disconnected'));

    return () => stomp.disconnect();
  }, [token, streamKey]);

  // Replay on reconnect
  useEffect(() => {
    if (connectionState === 'connected' && lastSequenceId !== null) {
      streamsApi.getReplayWindow(streamKey, lastSequenceId + 1, 100)
        .then(res => {
          const missed = res.data.messages;
          if (missed.length) setMessages(prev => [...prev, ...deduplicate(prev, missed)]);
        })
        .catch(console.error);
    }
  }, [connectionState, streamKey, lastSequenceId]);

  const sendMessage = useCallback((content: string, replyTo?: number) => {
    const tempId = `temp-${Date.now()}`;
    const optimistic: ChatMessageDTO = {
      id: 0, tempId, idempotencyKey: crypto.randomUUID(),
      streamId: 0, userId: 0, username: 'You', content,
      messageType: 'CHAT', timestamp: new Date().toISOString()
    } as any;
    setMessages(prev => [...prev, optimistic]);
    stomp.publish(`/app/chat.send/${streamKey}`, { streamKey, content, replyTo, idempotencyKey: optimistic.idempotencyKey });
  }, [streamKey]);

  return { messages, connectionState, sendMessage };
}
