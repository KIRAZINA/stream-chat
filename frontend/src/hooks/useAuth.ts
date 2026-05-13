/**
 * Auth hook for authentication helpers and guards
 */

import { useAuthStore } from '../stores/auth-store';
import { useCallback } from 'react';

export function useAuth() {
  const {
    user,
    token,
    refreshToken: refreshTokenValue,
    expiryTime,
    login,
    logout,
    refresh,
    isAuthenticated,
    hasRole,
    hasStreamRole
  } = useAuthStore();

  const logoutAndRedirect = useCallback(() => {
    logout();
    window.location.href = '/login';
  }, [logout]);

  const isTokenExpiringSoon = useCallback(() => {
    if (!expiryTime) return false;
    // Consider token expiring if less than 5 minutes remaining
    return expiryTime - Date.now() < 5 * 60 * 1000;
  }, [expiryTime]);

  return {
    user,
    token,
    refreshToken: refreshTokenValue,
    expiryTime,
    login,
    logout,
    logoutAndRedirect,
    refresh,
    isAuthenticated: isAuthenticated(),
    hasRole,
    hasStreamRole,
    isTokenExpiringSoon
  };
}
