import api from './client';
import {
  BanRequest,
  TimeoutRequest,
  PinMessageRequest,
  AddModeratorRequest,
  ModerationLog,
  AuditLog,
  UserStreamRole
} from '../types/backend';

// Helper to extract data from axios response
const extractData = async <T,>(promise: Promise<any>): Promise<T> => {
  const response = await promise;
  return response.data;
};

export const moderationApi = {
  // User management
  timeoutUser: (streamKey: string, data: TimeoutRequest) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/moderate/timeout`, data)),

  banUser: (streamKey: string, data: BanRequest) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/moderate/ban`, data)),

  unbanUser: (streamKey: string, userId: number) =>
    extractData<{ message: string }>(api.delete(`/streams/${streamKey}/moderate/ban/${userId}`)),

  shadowBan: {
    enable: (streamKey: string, userId: number) =>
      extractData<{ message: string }>(api.post(`/streams/${streamKey}/moderate/shadow-ban/${userId}`, {})),
    disable: (streamKey: string, userId: number) =>
      extractData<{ message: string }>(api.delete(`/streams/${streamKey}/moderate/shadow-ban/${userId}`)),
  },

  // Message management
  deleteMessage: (streamKey: string, messageId: number) =>
    extractData<{ message: string }>(api.delete(`/streams/${streamKey}/moderate/messages/${messageId}`)),

  deleteUserMessages: (streamKey: string, userId: number) =>
    extractData<Record<string, any>>(api.delete(`/streams/${streamKey}/moderate/messages/user/${userId}`)),

  pinMessage: (streamKey: string, data: PinMessageRequest) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/moderate/pin`, data)),

  // Moderators
  getModerators: (streamKey: string) =>
    extractData<UserStreamRole[]>(api.get(`/streams/${streamKey}/moderate/moderators`)),

  addModerator: (streamKey: string, data: AddModeratorRequest) =>
    extractData<{ message: string }>(api.post(`/streams/${streamKey}/moderate/moderators`, data)),

  removeModerator: (streamKey: string, userId: number) =>
    extractData<{ message: string }>(api.delete(`/streams/${streamKey}/moderate/moderators/${userId}`)),

  // Logs
  getModerationLogs: (streamKey: string) =>
    extractData<ModerationLog[]>(api.get(`/streams/${streamKey}/moderate/logs`)),

  getAuditLogs: (streamKey: string) =>
    extractData<AuditLog[]>(api.get(`/streams/${streamKey}/moderate/audit-logs`)),

  // AutoMod
  getTrustScore: (streamKey: string, userId: number) =>
    extractData<Record<string, any>>(api.get(`/streams/${streamKey}/moderate/trust-score/${userId}`)),
};

// Export aliases for backward compatibility
export const banUser = moderationApi.banUser;
export const deleteMessage = moderationApi.deleteMessage;
export const timeoutUser = moderationApi.timeoutUser;
export const pinMessage = moderationApi.pinMessage;
export const getModerators = moderationApi.getModerators;
