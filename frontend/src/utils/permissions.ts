/**
 * Permission helper functions for role-based access control
 */

import { User, UserStreamRole } from '../types/backend';

export function hasGlobalRole(user: User, role: string): boolean {
  return user.roles?.some((r) => r.role === role) ?? false;
}

export function hasStreamRole(streamRoles: UserStreamRole[], role: string): boolean {
  return streamRoles.some((r) => r.role === role);
}

export function canModerate(user: User, streamRoles: UserStreamRole[]): boolean {
  return (
    hasGlobalRole(user, 'ROLE_ADMIN') ||
    hasGlobalRole(user, 'ROLE_MODERATOR') ||
    hasStreamRole(streamRoles, 'ROLE_MODERATOR') ||
    hasStreamRole(streamRoles, 'ROLE_BROADCASTER')
  );
}

export function getRoleBadge(role: string): string {
  const badges: Record<string, string> = {
    ROLE_ADMIN: '👑 Admin',
    ROLE_BROADCASTER: '🎥 Broadcaster',
    ROLE_MODERATOR: '🛡️ Moderator',
    ROLE_VIP: '⭐ VIP',
    ROLE_SUBSCRIBER: '💜 Subscriber',
  };
  return badges[role] || '';
}
