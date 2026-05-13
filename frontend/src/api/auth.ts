import api from './client';
import type { AuthResponse, LoginRequest, RegisterRequest } from '../types/backend';

export const login = async (payload: LoginRequest): Promise<AuthResponse> => {
  const { data } = await api.post('/auth/login', payload);
  return data;
};

export const register = async (payload: RegisterRequest): Promise<AuthResponse> => {
  const { data } = await api.post('/auth/register', payload);
  return data;
};

export const refreshToken = async (): Promise<AuthResponse> => {
  const { data } = await api.post('/auth/refresh');
  return data;
};
