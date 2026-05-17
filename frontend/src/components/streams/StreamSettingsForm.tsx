import { useEffect } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchSettings, updateSettings as saveSettings } from '../../api/streams';
import { z } from 'zod';

const settingsSchema = z.object({
  slowModeEnabled: z.boolean(),
  subscribersOnlyMode: z.boolean(),
  emoteOnlyMode: z.boolean()
});

type FormValues = z.infer<typeof settingsSchema>;

interface Props {
  streamKey: string;
}

const StreamSettingsForm = ({ streamKey }: Props) => {
  const queryClient = useQueryClient();
  const { data, isLoading } = useQuery({
    queryKey: ['stream-settings', streamKey],
    queryFn: () => fetchSettings(streamKey),
    enabled: Boolean(streamKey)
  });

  const { register, handleSubmit, reset } = useForm<FormValues>({
    resolver: zodResolver(settingsSchema),
    defaultValues: { slowModeEnabled: false, subscribersOnlyMode: false, emoteOnlyMode: false }
  });

  useEffect(() => {
    if (data) {
      reset(data as FormValues);
    }
  }, [data, reset]);

  const updateSettings = useMutation({
    mutationFn: async (payload: FormValues) => {
      await saveSettings(streamKey, payload);
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['stream-settings', streamKey] })
  });

  return (
    <form onSubmit={handleSubmit((values) => updateSettings.mutate(values))} className="grid gap-4 rounded-3xl border border-slate-700 bg-slate-900/90 p-5">
       <h3 className="text-xl font-semibold text-slate-100">Chat Settings</h3>
       {isLoading ? <div className="text-slate-400">Loading...</div> : null}
       <label className="flex items-center justify-between gap-3 rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3">
         <span>Slow Mode</span>
         <input type="checkbox" {...register('slowModeEnabled')} className="h-5 w-5 rounded border-slate-600 text-sky-500" />
       </label>
       <label className="flex items-center justify-between gap-3 rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3">
         <span>Subscribers Only</span>
         <input type="checkbox" {...register('subscribersOnlyMode')} className="h-5 w-5 rounded border-slate-600 text-sky-500" />
       </label>
       <label className="flex items-center justify-between gap-3 rounded-2xl border border-slate-700 bg-slate-950 px-4 py-3">
         <span>Emotes Only</span>
         <input type="checkbox" {...register('emoteOnlyMode')} className="h-5 w-5 rounded border-slate-600 text-sky-500" />
       </label>
       <button type="submit" className="w-full rounded-2xl bg-sky-500 px-5 py-3 text-sm font-semibold text-slate-950 transition hover:bg-sky-400">
         Save
       </button>
    </form>
  );
};

export default StreamSettingsForm;
