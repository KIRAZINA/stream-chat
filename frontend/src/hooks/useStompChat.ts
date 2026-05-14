import { useEffect, useState, useCallback } from "react";
import { useAuthStore } from "../stores/auth-store";
import { StreamStompClient } from "../services/stomp-client";
import { streamsApi } from "../api/streams";
import { ChatMessageDTO } from "../types/backend";

const getStompClient = (): StreamStompClient => {
  const wsUrl = import.meta.env.VITE_WS_URL;
  if (!wsUrl) {
    throw new Error("VITE_WS_URL environment variable is not defined");
  }
  return new StreamStompClient(wsUrl);
};

export function useStompChat(streamKey: string) {
  const { token } = useAuthStore();
  const [messages, setMessages] = useState<ChatMessageDTO[]>([]);
  const [connectionState, setConnectionState] = useState<
    "disconnected" | "connecting" | "connected"
  >("disconnected");
  const [lastSequenceId, setLastSequenceId] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token || !streamKey) {
      setError(!token ? "No authentication token" : "No stream key");
      return;
    }

    let stompClient: StreamStompClient | null = null;

    const connectAndSubscribe = async () => {
      try {
        stompClient = getStompClient();
        stompClient.setAuthToken(token);
        setConnectionState("connecting");
        setError(null);

        await stompClient.connect();
        setConnectionState("connected");

        // Join the stream
        stompClient.publish(`/app/chat.join/${streamKey}`, { streamKey });

        // Subscribe to messages
        const subscription = stompClient.subscribe<ChatMessageDTO>(
          `/topic/stream/${streamKey}`,
          (msg) => {
            setMessages((prev) => [...prev, msg]);
            if (msg.redisSequenceId) setLastSequenceId(msg.redisSequenceId);
          },
        );

        // Cleanup subscription on unmount or streamKey change
        return () => {
          subscription.unsubscribe();
          if (stompClient) {
            stompClient.disconnect();
          }
        };
      } catch (err) {
        setConnectionState("disconnected");
        setError(err instanceof Error ? err.message : String(err));
        if (stompClient) {
          stompClient.disconnect();
        }
      }
    };

    const unsubscribe = connectAndSubscribe();

    // eslint-disable-next-line react-hooks/exhaustive-deps
    return unsubscribe;
  }, [token, streamKey]);

  // Replay on reconnect
  useEffect(() => {
    if (connectionState === "connected" && lastSequenceId !== null) {
      streamsApi
        .getReplayWindow(streamKey, lastSequenceId + 1, 100)
        .then((res) => {
          const missed = res.data.messages;
          if (missed.length)
            setMessages((prev) => [...prev, ...deduplicate(prev, missed)]);
        })
        .catch(console.error);
    }
  }, [connectionState, streamKey, lastSequenceId]);

  const sendMessage = useCallback(
    (content: string, replyTo?: number) => {
      try {
        const stompClient = getStompClient();
        const tempId = `temp-${Date.now()}`;
        const optimistic: ChatMessageDTO = {
          id: 0,
          tempId,
          idempotencyKey: crypto.randomUUID(),
          streamId: 0,
          userId: 0,
          username: "You",
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
          prev.filter((m) => !(m as ChatMessageDTO).tempId === tempId),
        );
      }
    },
    [streamKey],
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
