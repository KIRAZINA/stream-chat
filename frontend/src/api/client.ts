import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { useAuthStore } from '../stores/auth-store';
import toast from 'react-hot-toast';

interface RetriableRequestConfig extends InternalAxiosRequestConfig {
  _retry?: boolean;
}

const api = axios.create({
   baseURL: import.meta.env.VITE_API_URL ?? '/api',
   headers: { 'Content-Type': 'application/json' },
   withCredentials: true
});

api.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token && config.headers) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

api.interceptors.response.use(
  (response) => response,
  async (error: AxiosError) => {
    const originalRequest = error.config as RetriableRequestConfig | undefined;

    if (error.response?.status === 401 && originalRequest && !originalRequest._retry) {
      originalRequest._retry = true;
      const { refresh } = useAuthStore.getState();
      const refreshed = await refresh();
      const token = useAuthStore.getState().token;

      if (refreshed && token) {
        originalRequest.headers.Authorization = `Bearer ${token}`;
        return api(originalRequest);
      }

      toast.error('Session expired. Please sign in again.');
    }

    return Promise.reject(error);
  }
);

export default api;
