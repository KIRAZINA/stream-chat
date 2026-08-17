import { Client, type IPublishParams } from "@stomp/stompjs";
import { describe, expect, it, vi, type MockInstance } from "vitest";
import {
  buildSockJsUrl,
  buildStreamAffinityUrl,
  StreamStompClient,
} from "../services/stomp-client";

function connectedClient(): {
  sut: StreamStompClient;
  publish: MockInstance<[params: IPublishParams], void>;
} {
  const sut = new StreamStompClient("ws://localhost:8080/ws-chat");
  const client = (sut as unknown as { client: Client }).client;
  // client.connected is a getter backed by _stompHandler.connected — fake a live session.
  (client as unknown as { _stompHandler: { connected: boolean } })._stompHandler = {
    connected: true,
  };
  const publish = vi.spyOn(client, "publish").mockImplementation(() => {});
  return { sut, publish };
}

describe("StreamStompClient.publish wire serialization", () => {
  it("drops undefined replyToMessageId (key absent, never null)", () => {
    const { sut, publish } = connectedClient();

    sut.publish("/app/chat.send/stream-1", {
      streamKey: "stream-1",
      content: "hello",
      replyToMessageId: undefined,
      idempotencyKey: "k-1",
    });

    const frame = publish.mock.calls[0][0];
    const body = JSON.parse(frame.body as string);
    expect(body).not.toHaveProperty("replyToMessageId");
    expect(body).not.toHaveProperty("replyToMessageId", null);
    expect(body.content).toBe("hello");
  });

  it("emits replyToMessageId on the wire when replying", () => {
    const { sut, publish } = connectedClient();

    sut.publish("/app/chat.send/stream-1", {
      streamKey: "stream-1",
      content: "replying",
      replyToMessageId: 42,
      idempotencyKey: "k-2",
    });

    const body = JSON.parse(publish.mock.calls[0][0].body as string);
    expect(body.replyToMessageId).toBe(42);
  });
});

describe("stream affinity URL builder (Track A)", () => {
  it("builds the stream-keyed native WebSocket URL when affinity is enabled", () => {
    expect(buildStreamAffinityUrl("ws://localhost:8080/ws-chat", "abc123"))
      .toBe("ws://localhost:8080/ws-chat/stream/abc123");
    expect(buildStreamAffinityUrl("https://api.example.com", "abc123"))
      .toBe("wss://api.example.com/ws-chat/stream/abc123");
  });

  it("keeps the legacy SockJS URL when affinity is disabled", () => {
    expect(buildSockJsUrl("ws://localhost:8080/ws-chat")).toBe("http://localhost:8080/ws-chat");
    expect(buildSockJsUrl("https://api.example.com")).toBe("https://api.example.com/ws-chat");
  });

  it("throws when affinity is enabled without a streamKey", () => {
    expect(() => buildStreamAffinityUrl("ws://localhost:8080/ws-chat")).toThrow(/streamKey/);
  });
});
