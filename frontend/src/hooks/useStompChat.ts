import { useEffect, useRef, useState, useCallback } from "react";
import { useAuthStore } from "../stores/auth-store";
import { useChatStore } from "../stores/chat-store";
import { StreamStompClient } from "../services/stomp-client";
import { streamsApi } from "../api/streams";
import { ChatMessageDTO } from "../types/backend";

const getStompClient = (): StreamStompClient => {
  let wsUrl = import.meta.env.VITE_WS_URL;
  // Derive from window.location if not set, to allow development without .env
  if (!wsUrl) {
    const protocol = window.location.protocol.replace("http", "ws");
    const host = window.location.host;
    if (protocol && host) {
      wsUrl = `${protocol}//${host}`;
    }
  }
  if (!wsUrl) {
    throw new Error("VITE_WS_URL environment variable is not defined and cannot be derived from window.location");
  }
  return new StreamStompClient(wsUrl);
};

export function useStompChat(streamKey: string) {
  const { token, user } = useAuthStore();
  const stompClientRef = useRef<StreamStompClient | null>(null);
  const [messages, setMessages] = useState<ChatMessageDTO[]>([]);
  const [connectionState, setConnectionState] = useState<
    "disconnected" | "connecting" | "connected"
  >("disconnected");
  const [error, setError] = useState<string | null>(null);

  // Ref to track the highest redisSequenceId seen (fallback if store access fails)
  const lastSeenMessageIdRef = useRef<number | null>(null);

  useEffect(() => {
    if (!token || !streamKey) {
      setError(!token ? "No authentication token" : "No stream key");
      return;
    }

    let isDisposed = false;
    let unsubscribe: (() => void) | undefined;
    let client: StreamStompClient | null = null;

    const connectAndSubscribe = async (): Promise<void> => {
      try {
        client = getStompClient();
        stompClientRef.current = client;
        client.setAuthToken(token);
        setConnectionState("connecting");
        setError(null);

        await client.connect();
        if (isDisposed) {
          client.disconnect();
          return;
        }

        setConnectionState("connected");

        // Join the stream
        client.publish(`/app/chat.join/${streamKey}`, { streamKey });

        // Subscribe to messages
        unsubscribe = client.subscribe<ChatMessageDTO>(
          `/topic/stream/${streamKey}`,
          (msg) => {
            setMessages((prev) => mergeIncomingMessage(prev, msg));
            // Track highest redisSequenceId in ref and update store
            if (msg.redisSequenceId) {
              const newId = msg.redisSequenceId;
              lastSeenMessageIdRef.current = Math.max(
                lastSeenMessageIdRef.current ?? 0,
                newId
              );
              // Update store for other consumers
              useChatStore.getState().setLastSeenMessageId(newId);
            }
          },
        );
      } catch (err) {
        if (isDisposed) {
          return;
        }
        setConnectionState("disconnected");
        setError(err instanceof Error ? err.message : String(err));
        if (client) {
          client.disconnect();
        }
      }
    };

    void connectAndSubscribe();

    // eslint-disable-next-line react-hooks/exhaustive-deps
    return () => {
      isDisposed = true;
      unsubscribe?.();
      client?.disconnect();
      if (stompClientRef.current === client) {
        stompClientRef.current = null;
      }
    };
  }, [token, streamKey]);

  // Replay on reconnect / new stream join
  useEffect(() => {
    if (connectionState === "connected") {
      // Use store value first, fallback to ref
      const lastSeen = useChatStore.getState().lastSeenMessageId ?? lastSeenMessageIdRef.current;
      if (lastSeen !== null) {
        streamsApi
          .getReplayWindow(streamKey, lastSeen + 1, 100)
          .then((res) => {
            const missed = res.messages;
            if (missed.length)
              setMessages((prev) => [...prev, ...deduplicate(prev, missed)]);
          })
          .catch(console.error);
      }
    }
  }, [connectionState, streamKey]);

  const sendMessage = useCallback(
    (content: string, replyTo?: number) => {
      const tempId = `temp-${Date.now()}`;
      try {
        const stompClient = stompClientRef.current;
        if (!stompClient?.isConnected()) {
          throw new Error("Chat is not connected");
        }
        const optimistic: ChatMessageDTO = {
          id: 0,
          tempId,
          idempotencyKey: crypto.randomUUID(),
          streamId: 0,
          userId: user?.id ?? 0,
          username: user?.username ?? "You",
          content,
          messageType: "CHAT",
          timestamp: new Date().toISOString(),
        } as any;
        setMessages((prev) => [...prev, optimistic]);
        stompClient.publish(`/app/chat.send/${streamKey}`, {
          streamKey,
          content,
          replyTo,
          idempotencyKey: optimistic.idempotencyKey,
        });
      } catch (err) {
        console.error("Failed to send message", err);
        // Remove optimistic message on error
        setMessages((prev) =>
          prev.filter((m) => (m as ChatMessageDTO & { tempId?: string }).tempId !== tempId),
        );
      }
    },
    [streamKey, user?.username],
  );

  return { messages, connectionState, sendMessage, error };
}

function deduplicate(
  existing: ChatMessageDTO[],
  incoming: ChatMessageDTO[],
): ChatMessageDTO[] {
  const existingIds = new Set(existing.map((m) => m.id));
  const existingTempKeys = new Set(
    existing.filter((m) => !m.id).map((m) => m.idempotencyKey),
  );
  return incoming.filter((m) => {
    if (m.id && existingIds.has(m.id)) return false;
    if (m.idempotencyKey && existingTempKeys.has(m.idempotencyKey))
      return false;
    return true;
  });
}

function mergeIncomingMessage(
  existing: ChatMessageDTO[],
  incoming: ChatMessageDTO,
): ChatMessageDTO[] {
  if (incoming.id && existing.some((message) => message.id === incoming.id)) {
    return existing;
  }

  if (incoming.redisSequenceId && existing.some((message) => message.redisSequenceId === incoming.redisSequenceId)) {
    return existing;
  }

  if (incoming.idempotencyKey) {
    const optimisticIndex = existing.findIndex(
      (message) => !message.id && message.idempotencyKey === incoming.idempotencyKey,
    );
    if (optimisticIndex >= 0) {
      const next = existing.slice();
      next[optimisticIndex] = incoming;
      return next;
    }
  }

  return [...existing, incoming];
}