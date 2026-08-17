/**
 * Backend type definitions based on OpenAPI specification
 * These types mirror the Spring Boot backend DTOs
 */

// Auth types
export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface AuthResponse {
  token: string;
  type: string;
  username: string;
  email: string;
  refresh_token: string;
  expires_in: number;
}

export interface User {
  id: number;
  username: string;
  email: string;
  roles?: UserRole[];
  streamRoles?: UserStreamRole[];
  createdAt?: string;
}

export interface UserProfileDTO {
  id: number;
  username: string;
  email: string;
  color?: string;
  roles: string[];
  streamRoles: UserStreamRoleDTO[];
}

export interface UserStreamRoleDTO {
  streamKey: string;
  role: string;
}

export interface UserRole {
  id: number;
  role: string;
}

export interface UserStreamRole {
  id: number;
  userId: number;
  streamId: number;
  role: string;
  grantedBy?: number;
  grantedAt?: string;
}

// Stream types
export interface StreamDTO {
  id: number;
  streamKey: string;
  title: string;
  description?: string;
  ownerId: number;
  ownerUsername: string;
  status: 'OFFLINE' | 'LIVE' | 'PREPARING';
  category?: string;
  thumbnailUrl?: string;
  viewerCount?: number;
  createdAt: string;
  startedAt?: string;
  settings?: StreamSettings;
}

export interface StreamRequest {
  streamKey: string;
  title: string;
  description?: string;
  category?: string;
}

export interface StreamSettings {
  id?: number;
  streamId?: number;
  slowModeEnabled: boolean;
  slowModeSeconds?: number;
  followersOnlyMode: boolean;
  followersOnlyDurationMinutes?: number;
  subscribersOnlyMode: boolean;
  emoteOnlyMode: boolean;
  maxMessageLength?: number;
  profanityFilterEnabled: boolean;
  linkProtectionEnabled: boolean;
}

export interface StreamSettingsUpdateRequest {
  slowModeEnabled?: boolean;
  slowModeSeconds?: number;
  followersOnlyMode?: boolean;
  followersOnlyDurationMinutes?: number;
  subscribersOnlyMode?: boolean;
  emoteOnlyMode?: boolean;
  maxMessageLength?: number;
  profanityFilterEnabled?: boolean;
  linkProtectionEnabled?: boolean;
}

export interface StreamPresenceResponse {
  streamKey: string;
  activeViewers: number;
  totalViewers: number;
}

// Chat types
export interface ChatMessageDTO {
  id: number;
  streamId: number;
  userId: number;
  username: string;
  content: string;
  replyToMessageId?: number;
  replyToUsername?: string;
  replyToContentPreview?: string;
  messageType: 'CHAT' | 'SYSTEM' | 'JOIN' | 'LEAVE' | 'ERROR' | 'MODERATION' | 'DELETED';
  color?: string;
  badges?: string[];
  fragments?: MessageFragmentDTO[];
  isDeleted?: boolean;
  deletedById?: number;
  deletedByUsername?: string;
  deletedAt?: string;
  isPinned?: boolean;
  pinnedAt?: string;
  pinnedByUsername?: string;
  idempotencyKey?: string;
  redisSequenceId?: number;
  timestamp: string;
}

export interface MessageFragmentDTO {
  type: 'TEXT' | 'EMOTE';
  text: string;
  emoteCode?: string;
  imageUrl?: string;
}

export interface ChatHistoryResponse {
  messages: ChatMessageDTO[];
  totalCount: number;
  hasMore: boolean;
}

// Moderation types
export interface TimeoutRequest {
  username: string;
  durationSeconds: number;
  reason?: string;
}

export interface BanRequest {
  username: string;
  permanent: boolean;
  reason?: string;
}

export interface PinMessageRequest {
  messageId: number;
  pin: boolean;
}

export interface AddModeratorRequest {
  username: string;
}
