import { useMutation, useQueryClient } from '@tanstack/react-query';
import { banUser, deleteMessage, timeoutUser, pinMessage } from '../api/moderation';

export const useModeration = (streamKey: string) => {
  const queryClient = useQueryClient();

  const timeoutMutation = useMutation({
    mutationFn: (payload: any) => timeoutUser(streamKey, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stream', streamKey] })
  });

  const banMutation = useMutation({
    mutationFn: (payload: any) => banUser(streamKey, payload),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stream', streamKey] })
  });

  const deleteMessageMutation = useMutation({
    mutationFn: (msgId: number) => deleteMessage(streamKey, msgId),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stream', streamKey] })
  });

  const pinMessageMutation = useMutation({
    mutationFn: (data: { messageId: number; pin: boolean }) => pinMessage(streamKey, data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stream', streamKey] })
  });

  return {
    timeoutUser: timeoutMutation.mutate,
    banUser: banMutation.mutate,
    deleteMessage: deleteMessageMutation.mutate,
    pinMessage: pinMessageMutation.mutate,
    isTimingOut: timeoutMutation.isPending,
    isBanning: banMutation.isPending,
    isDeletingMessage: deleteMessageMutation.isPending
  };
};
