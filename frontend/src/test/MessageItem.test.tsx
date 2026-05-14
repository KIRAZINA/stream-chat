import { render, screen } from "@testing-library/react";
import { describe, it, expect, beforeAll, afterEach, afterAll } from "vitest";
import { MessageItem } from "../components/chat/MessageItem";
import { server } from "./server";
import { ChatMessageDTO } from "../types/backend";

beforeAll(() => server.listen());
afterEach(() => server.resetHandlers());
afterAll(() => server.close());

describe("MessageItem", () => {
  const mockMessage: ChatMessageDTO = {
    id: 1,
    streamId: 1,
    userId: 1,
    username: "testuser",
    content: "Hello world!",
    messageType: "CHAT",
    // Use a timestamp that is 5 minutes ago to ensure formatTimestamp returns a string ending with "ago"
    timestamp: new Date(Date.now() - 5 * 60 * 1000).toISOString(),
  };

  it("renders message content", () => {
    render(<MessageItem message={mockMessage} showAvatar={true} />);
    expect(screen.getByText("Hello world!")).toBeInTheDocument();
  });

  it("renders username", () => {
    render(<MessageItem message={mockMessage} showAvatar={true} />);
    expect(screen.getByText("testuser")).toBeInTheDocument();
  });

  it("renders timestamp", () => {
    render(<MessageItem message={mockMessage} showAvatar={true} />);
    expect(screen.getByText(/ago$/)).toBeInTheDocument();
  });
});
