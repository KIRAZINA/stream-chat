import { create } from 'zustand';

interface ChatState {
  selectedReply: {
    messageId: number;
    username: string;
    content: string;
  } | null;
  lastSeenMessageId: number | null;
  setReply: (reply: { messageId: number; username: string; content: string } | null) => void;
  setLastSeenMessageId: (id: number | null) => void;
  clearMessages: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  selectedReply: null,
  lastSeenMessageId: null,
  setReply: (reply) => set({ selectedReply: reply }),
  setLastSeenMessageId: (id) => set({ lastSeenMessageId: id }),
  clearMessages: () => set({ selectedReply: null, lastSeenMessageId: null }),
}));
