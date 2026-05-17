import { create, type StateCreator, type StoreApi, type UseBoundStore } from "zustand";
import { persist } from "zustand/middleware";
import { AuthResponse, UserProfileDTO } from "../types/backend";
import api from "../api/client";

interface AuthState {
  user: UserProfileDTO | null;
  token: string | null;
  refreshToken: string | null;
  expiryTime: number | null;
  login: (resp: AuthResponse) => Promise<void>;
  logout: () => void;
  refresh: () => Promise<boolean>;
  isAuthenticated: () => boolean;
  hasRole: (role: string) => boolean;
  hasStreamRole: (streamKey: string, role: string) => boolean;
  canModerate: (streamKey: string) => boolean;
}

const createAuthState: StateCreator<AuthState> = (set, get) => ({
  user: null,
  token: null,
  refreshToken: null,
  expiryTime: null,

  login: async (resp: AuthResponse) => {
    set({
      token: resp.token,
      refreshToken: resp.refresh_token,
      expiryTime: Date.now() + resp.expires_in * 1000,
    });
    try {
      const { data } = await api.get<UserProfileDTO>("/users/me");
      set({ user: data });
    } catch (err) {
      console.error("Failed to fetch user profile after login", err);
    }
  },

  logout: () =>
    set({ token: null, refreshToken: null, expiryTime: null, user: null }),

  refresh: async () => {
    try {
      const { data } = await api.post<AuthResponse>("/auth/refresh", {
        refreshToken: get().refreshToken,
      });
      await get().login(data); // re-fetch profile
      return true;
    } catch {
      get().logout();
      return false;
    }
  },

  isAuthenticated: () => {
    const { token, expiryTime } = get();
    return !!token && (expiryTime ?? 0) > Date.now();
  },

  hasRole: (role: string) => get().user?.roles?.includes(role) ?? false,
  hasStreamRole: (streamKey: string, role: string) =>
    get().user?.streamRoles?.some(
      (r) => r.streamKey === streamKey && r.role === role,
    ) ?? false,
  canModerate: (streamKey: string) => {
    const { hasRole, hasStreamRole } = get();
    return (
      hasRole("ROLE_ADMIN") ||
      hasStreamRole(streamKey, "ROLE_MODERATOR") ||
      hasStreamRole(streamKey, "ROLE_BROADCASTER")
    );
  },
});

let useAuthStore: UseBoundStore<StoreApi<AuthState>>;

try {
  useAuthStore = create<AuthState>()(
    persist(createAuthState, {
      name: "stream-chat-auth",
    }),
  );
} catch (error) {
  console.error(
    "Failed to initialize auth store with persist, falling back to non-persisted store",
    error,
  );
  if (typeof window !== "undefined") {
    window.localStorage.removeItem("stream-chat-auth");
  }
  useAuthStore = create<AuthState>()(createAuthState);
}

export { useAuthStore };
