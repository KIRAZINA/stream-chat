import { create } from 'zustand';
import { StreamDTO, StreamSettings } from '../types/backend';
import { StreamUIState } from '../types/frontend';

interface StreamState {
  activeStream: StreamDTO | null;
  settings: StreamSettings | null;
  viewerCount: number;
  ui: StreamUIState;
  setActiveStream: (stream: StreamDTO | null) => void;
  setSettings: (settings: StreamSettings | null) => void;
  setViewerCount: (count: number) => void;
  setUI: (ui: Partial<StreamUIState>) => void;
}

export const useStreamStore = create<StreamState>((set) => ({
  activeStream: null,
  settings: null,
  viewerCount: 0,
  ui: {
    isFullscreen: false,
    volume: 1,
    isMuted: false,
    quality: 'auto'
  },
  setActiveStream: (stream) => set({ activeStream: stream }),
  setSettings: (settings) => set({ settings }),
  setViewerCount: (count) => set({ viewerCount: count }),
  setUI: (ui) => set((state) => ({ ui: { ...state.ui, ...ui } }))
}));
