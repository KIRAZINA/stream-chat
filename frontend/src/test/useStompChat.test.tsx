import { act, render, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { useStompChat } from "../hooks/useStompChat";
import { useAuthStore } from "../stores/auth-store";

const mocks = vi.hoisted(() => ({
  setAuthToken: vi.fn(),
  connect: vi.fn(),
  disconnect: vi.fn(),
  subscribe: vi.fn(),
  publish: vi.fn(),
  isConnected: vi.fn(),
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

let api: ReturnType<typeof useStompChat>;

function Harness({ streamKey }: { streamKey: string }) {
  api = useStompChat(streamKey);
  return null;
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
