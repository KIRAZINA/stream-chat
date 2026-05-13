import { create } from 'zustand';
import { ChatMessageDTO } from '../types/backend';
import { ChatUIState, ContextMenuState } from '../types/frontend';

interface ChatState {
  messages: ChatMessageDTO[];
  selectedReply: {
    messageId: number;
    username: string;
    content: string;
  } | null;
  ui: ChatUIState;
  contextMenu: ContextMenuState;
  addMessage: (message: ChatMessageDTO) => void;
  updateMessage: (messageId: number, updates: Partial<ChatMessageDTO>) => void;
  clearMessages: () => void;
  setReply: (reply: { messageId: number; username: string; content: string } | null) => void;
  setUI: (ui: Partial<ChatUIState>) => void;
  setContextMenu: (menu: ContextMenuState) => void;
  closeContextMenu: () => void;
}

export const useChatStore = create<ChatState>((set) => ({
  messages: [],
  selectedReply: null,
  ui: {
    sidebarOpen: false,
    modPanelOpen: false,
    showEmotePicker: false,
    replyTo: null,
    scrolledUp: false,
    unreadCount: 0
  },
  contextMenu: {
    isOpen: false,
    x: 0,
    y: 0
  },
  addMessage: (message) => set((state) => ({ messages: [...state.messages, message] })),
  updateMessage: (messageId, updates) =>
    set((state) => ({
      messages: state.messages.map((m) => (m.id === messageId ? { ...m, ...updates } : m))
    })),
  clearMessages: () => set({ messages: [], selectedReply: null }),
  setReply: (reply) => set({ selectedReply: reply }),
  setUI: (ui) => set((state) => ({ ui: { ...state.ui, ...ui } })),
  setContextMenu: (menu) => set({ contextMenu: menu }),
  closeContextMenu: () =>
    set((state) => ({ contextMenu: { ...state.contextMenu, isOpen: false } }))
}));
