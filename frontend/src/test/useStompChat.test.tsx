import { act, render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useStompChat } from "../hooks/useStompChat";
import { useAuthStore } from "../stores/auth-store";
import { useChatStore } from "../stores/chat-store";
import { ChatMessageDTO } from "../types/backend";

const mocks = vi.hoisted(() => ({
  setAuthToken: vi.fn(),
  connect: vi.fn(),
  disconnect: vi.fn(),
  subscribe: vi.fn(),
  publish: vi.fn(),
  isConnected: vi.fn(),
}));

const streamsApiMocks = vi.hoisted(() => ({
  getReplayWindow: vi.fn(),
}));

vi.mock("../services/stomp-client", () => ({
  StreamStompClient: class {
    setAuthToken = mocks.setAuthToken;
    connect = mocks.connect;
    disconnect = mocks.disconnect;
    subscribe = mocks.subscribe;
    publish = mocks.publish;
    isConnected = mocks.isConnected;
  },
}));

vi.mock("../api/streams", () => ({
  streamsApi: {
    getReplayWindow: streamsApiMocks.getReplayWindow,
  },
}));

let api: ReturnType<typeof useStompChat>;

function Harness({ streamKey }: { streamKey: string }) {
  api = useStompChat(streamKey);
  return null;
}

function message(id: number, content: string): ChatMessageDTO {
  return {
    id,
    streamId: 1,
    userId: 1,
    username: "alice",
    content,
    messageType: "CHAT",
    timestamp: new Date().toISOString(),
  };
}

function sendCalls(): Array<[string, Record<string, unknown>]> {
  return mocks.publish.mock.calls.filter(
    (call: [string, Record<string, unknown>]) => call[0] === "/app/chat.send/stream-1",
  ) as Array<[string, Record<string, unknown>]>;
}

describe("useStompChat send payload contract", () => {
  beforeEach(() => {
    mocks.setAuthToken.mockReset();
    mocks.connect.mockReset();
    mocks.disconnect.mockReset();
    mocks.subscribe.mockReset();
    mocks.publish.mockReset();
    mocks.isConnected.mockReset();

    mocks.connect.mockResolvedValue(undefined);
    mocks.isConnected.mockReturnValue(true);
    mocks.subscribe.mockReturnValue(() => {});

    streamsApiMocks.getReplayWindow.mockReset();
    streamsApiMocks.getReplayWindow.mockResolvedValue({
      messages: [],
      totalCount: 0,
      hasMore: false,
    });

    useChatStore.getState().clearMessages();

    useAuthStore.setState({
      user: {
        id: 1,
        username: "alice",
        email: "alice@example.com",
        roles: [],
        streamRoles: [],
      },
      token: "test-token",
      refreshToken: "refresh-token",
      expiryTime: Date.now() + 60_000,
    });
  });

  it("publishes replyToMessageId on the send frame when replying", async () => {
    render(<Harness streamKey="stream-1" />);
    await waitFor(() =>
      expect(mocks.publish).toHaveBeenCalledWith("/app/chat.join/stream-1", {
        streamKey: "stream-1",
      }),
    );

    act(() => {
      api.sendMessage("replying", 42);
    });

    const send = sendCalls().find(([, payload]) => payload.content === "replying");
    expect(send).toBeDefined();
    expect(send?.[1].replyToMessageId).toBe(42);
    expect(send?.[1]).not.toHaveProperty("replyTo");
  });

  it("omits replyToMessageId from the send frame for a normal message", async () => {
    render(<Harness streamKey="stream-1" />);
    await waitFor(() =>
      expect(mocks.publish).toHaveBeenCalledWith("/app/chat.join/stream-1", {
        streamKey: "stream-1",
      }),
    );

    act(() => {
      api.sendMessage("plain");
    });

    const send = sendCalls().find(([, payload]) => payload.content === "plain");
    expect(send).toBeDefined();
    expect(send?.[1]).not.toHaveProperty("replyToMessageId");
    expect(send?.[1]).not.toHaveProperty("replyTo");
    expect(send?.[1].streamKey).toBe("stream-1");
    expect(send?.[1].content).toBe("plain");
  });
});

describe("useStompChat gap replay on reconnect", () => {
  it("calls getReplayWindow with after = lastSeenMessageId and merges backfill without duplicates", async () => {
    useChatStore.setState({ lastSeenMessageId: 5 });

    const backfill: ChatMessageDTO[] = [message(6, "missed-6"), message(7, "missed-7")];

    let resolveBackfill: (res: {
      messages: ChatMessageDTO[];
      totalCount: number;
      hasMore: boolean;
    }) => void = () => {};
    streamsApiMocks.getReplayWindow.mockImplementation(
      () =>
        new Promise((resolve) => {
          resolveBackfill = resolve;
        }),
    );

    let subscribeCallback: ((msg: ChatMessageDTO) => void) | undefined;
    mocks.subscribe.mockImplementation((_dest: string, cb: (msg: ChatMessageDTO) => void) => {
      subscribeCallback = cb;
      return () => {};
    });

    render(<Harness streamKey="stream-1" />);

    await waitFor(() =>
      expect(streamsApiMocks.getReplayWindow).toHaveBeenCalledWith("stream-1", 6, 100),
    );
    expect(subscribeCallback).toBeDefined();

    act(() => {
      subscribeCallback?.(message(6, "live-6"));
      subscribeCallback?.(message(7, "live-7"));
    });
    expect(api.messages.map((m) => m.id)).toEqual([6, 7]);

    await act(async () => {
      resolveBackfill({ messages: backfill, totalCount: 2, hasMore: false });
    });
    expect(api.messages.map((m) => m.id)).toEqual([6, 7]);

    act(() => {
      subscribeCallback?.(message(7, "live-7-again"));
    });
    expect(api.messages.map((m) => m.id)).toEqual([6, 7]);
    expect(api.messages.filter((m) => m.id === 7)).toHaveLength(1);
  });
});
