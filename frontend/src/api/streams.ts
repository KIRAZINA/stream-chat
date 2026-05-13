import api from './client';
import {
  StreamDTO,
  StreamRequest,
  StreamSettings,
  StreamSettingsUpdateRequest,
  StreamPresenceResponse,
  ChatHistoryResponse
} from '../types/backend';

// Helper to extract data from axios response
const extractData = async <T,>(promise: Promise<any>): Promise<T> => {
  const response = await promise;
  return response.data;
};

export const streamsApi = {
  getAll: () => extractData<StreamDTO[]>(api.get('/streams')),

  /** Alias for getAll — used by DashboardPage */
  fetchAll: () => extractData<StreamDTO[]>(api.get('/streams')),

  getByKey: (streamKey: string) =>
    extractData<StreamDTO>(api.get(`/streams/${streamKey}`)),

  /** Alias for getByKey — used by StreamPage */
  getDetails: (streamKey: string) =>
    extractData<StreamDTO>(api.get(`/streams/${streamKey}`)),

  create: (data: Omit<StreamRequest, 'streamKey'>) =>
    extractData<StreamDTO>(api.post('/streams', data)),

  /** Alias for create — used by DashboardPage */
  createStream: (data: Omit<StreamRequest, 'streamKey'>) =>
    extractData<StreamDTO>(api.post('/streams', data)),

  update: (streamKey: string, data: StreamRequest) =>
    extractData<StreamDTO>(api.put(`/streams/${streamKey}`, data)),

  delete: (streamKey: string) =>
    extractData<{ message: string }>(api.delete(`/streams/${streamKey}`)),

  start: (streamKey: string) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/start`, {})),

  stop: (streamKey: string) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/stop`, {})),

  // Settings
  getSettings: (streamKey: string) =>
    extractData<StreamSettings>(api.get(`/streams/${streamKey}/settings`)),

  updateSettings: (streamKey: string, data: StreamSettingsUpdateRequest) =>
    extractData<StreamSettings>(api.put(`/streams/${streamKey}/settings`, data)),

  // Chat history
  getChatHistory: (streamKey: string, params?: {
    before?: number;
    limit?: number;
    includeDeleted?: boolean;
  }) =>
    extractData<ChatHistoryResponse>(api.get(`/streams/${streamKey}/messages`, { params })),

  // Replay window for reconnect recovery
  getReplayWindow: (streamKey: string, afterSequenceId?: number, limit?: number) =>
    extractData<ChatHistoryResponse>(api.get(`/streams/${streamKey}/messages/replay`, {
      params: { afterSequenceId, limit }
    })),

  // Presence
  getPresence: (streamKey: string) =>
    extractData<StreamPresenceResponse>(api.get(`/streams/${streamKey}/presence`)),
};

// Export aliases for backward compatibility
export const fetchStreams = streamsApi.fetchAll;
export const createStream = streamsApi.createStream;
export const fetchStreamDetails = streamsApi.getDetails;
export const fetchPresence = streamsApi.getPresence;
export const fetchSettings = streamsApi.getSettings;
export const updateSettings = streamsApi.updateSettings;
