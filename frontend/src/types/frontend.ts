/**
 * Frontend-specific derived types and UI state types
 */

import { ChatMessageDTO, StreamDTO, User } from './backend';

// UI Connection States
export type ConnectionState = 'connecting' | 'connected' | 'disconnected' | 'error';

// UI-specific message types
export interface UIMessage extends ChatMessageDTO {
  isOptimistic?: boolean;
  tempId?: string;
}

// Modal states
export interface ModalState {
  type: 'timeout' | 'ban' | 'delete_message' | 'pin_message' | 'create_stream' | 'settings' | null;
  isOpen: boolean;
  data?: any;
}

// Context menu state
export interface ContextMenuState {
  isOpen: boolean;
  x: number;
  y: number;
  targetUserId?: number;
  targetUsername?: string;
  targetMessageId?: number;
}

// Chat UI state
export interface ChatUIState {
  sidebarOpen: boolean;
  modPanelOpen: boolean;
  showEmotePicker: boolean;
  replyTo: {
    messageId: number;
    username: string;
    content: string;
  } | null;
  scrolledUp: boolean;
  unreadCount: number;
}

// Stream UI state
export interface StreamUIState {
  isFullscreen: boolean;
  volume: number;
  isMuted: boolean;
  quality: 'auto' | '1080p' | '720p' | '480p' | '360p';
}

// Form states
export interface FormState {
  isSubmitting: boolean;
  errors: Record<string, string>;
}

// Toast notification types
export type ToastType = 'success' | 'error' | 'info' | 'warning';

export interface ToastNotification {
  id: string;
  type: ToastType;
  message: string;
  duration?: number;
}

// User timeout state
export interface UserTimeoutState {
  isTimedOut: boolean;
  expiresAt?: string;
  reason?: string;
}

// Theme
export type Theme = 'dark' | 'light';

// Pagination
export interface PaginationState {
  page: number;
  pageSize: number;
  totalCount: number;
  hasMore: boolean;
}
