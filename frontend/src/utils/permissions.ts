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

export function canBan(
  currentUser: User,
  targetUser: User,
  streamRoles: UserStreamRole[]
): boolean {
  // Admins can ban anyone
  if (hasGlobalRole(currentUser, 'ROLE_ADMIN')) return true;

  // Broadcasters can ban non-mods/non-admins
  if (hasStreamRole(streamRoles, 'ROLE_BROADCASTER')) {
    return (
      !hasGlobalRole(targetUser, 'ROLE_ADMIN') && !hasGlobalRole(targetUser, 'ROLE_MODERATOR')
    );
  }

  // Moderators cannot ban other mods or admins
  return false;
}

export function getRoleColor(role: string): string {
  const colors: Record<string, string> = {
    ROLE_ADMIN: 'text-red-600',
    ROLE_BROADCASTER: 'text-purple-600',
    ROLE_MODERATOR: 'text-green-600',
    ROLE_VIP: 'text-yellow-600',
    ROLE_SUBSCRIBER: 'text-blue-600',
  };
  return colors[role] || 'text-gray-600';
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
