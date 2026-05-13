/**
 * Stream settings hook with React Query integration
 */

import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { streamsApi } from '../api/streams';
import { StreamSettings, StreamSettingsUpdateRequest } from '../types/backend';
import toast from 'react-hot-toast';

export function useStreamSettings(streamKey: string) {
  const queryClient = useQueryClient();

  const { data: settings, isLoading, error } = useQuery({
    queryKey: ['stream', streamKey, 'settings'],
    queryFn: () => streamsApi.getSettings(streamKey).then((res) => res.data),
    enabled: !!streamKey
  });

  const updateMutation = useMutation({
    mutationFn: (data: StreamSettingsUpdateRequest) =>
      streamsApi.updateSettings(streamKey, data),
    onSuccess: () => {
      toast.success('Settings updated');
      queryClient.invalidateQueries({ queryKey: ['stream', streamKey, 'settings'] });
    },
    onError: (error: any) => {
      toast.error(error.response?.data?.message || 'Failed to update settings');
    }
  });

  return {
    settings: settings as StreamSettings | undefined,
    isLoading,
    error,
    updateSettings: updateMutation.mutateAsync,
    isUpdating: updateMutation.isPending
  };
}
