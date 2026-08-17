import { forwardRef, useEffect, useState, type InputHTMLAttributes } from 'react';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { fetchSettings, updateSettings as saveSettings } from '../../api/streams';
import { z } from 'zod';
import { StreamSettings } from '../../types/backend';
import { toast } from 'react-hot-toast';

const settingsSchema = z.object({
  slowModeEnabled: z.boolean(),
  slowModeSeconds: z.number().min(0).optional(),
  followersOnlyMode: z.boolean(),
  followersOnlyDurationMinutes: z.number().min(0).optional(),
  subscribersOnlyMode: z.boolean(),
  emoteOnlyMode: z.boolean(),
  profanityFilterEnabled: z.boolean(),
  linkProtectionEnabled: z.boolean(),
  maxMessageLength: z.number().min(1).max(2000).optional(),
});

type FormValues = z.infer<typeof settingsSchema>;

interface Props {
  streamKey: string;
}

const StreamSettingsForm = ({ streamKey }: Props) => {
  const queryClient = useQueryClient();
  const [apiError, setApiError] = useState<string | null>(null);

  const { data: settings, isLoading, error } = useQuery<StreamSettings>({
    queryKey: ['stream-settings', streamKey],
    queryFn: () => fetchSettings(streamKey),
    enabled: Boolean(streamKey),
  });

  const { register, handleSubmit, reset, watch, formState: { isSubmitting } } = useForm<FormValues>({
    resolver: zodResolver(settingsSchema),
  });

  const slowModeEnabled = watch('slowModeEnabled');
  const followersOnlyMode = watch('followersOnlyMode');

  useEffect(() => {
    if (settings) {
      reset({
        slowModeEnabled: settings.slowModeEnabled ?? false,
        slowModeSeconds: settings.slowModeSeconds ?? 10,
        followersOnlyMode: settings.followersOnlyMode ?? false,
        followersOnlyDurationMinutes: settings.followersOnlyDurationMinutes ?? 30,
        subscribersOnlyMode: settings.subscribersOnlyMode ?? false,
        emoteOnlyMode: settings.emoteOnlyMode ?? false,
        profanityFilterEnabled: settings.profanityFilterEnabled ?? true,
        linkProtectionEnabled: settings.linkProtectionEnabled ?? true,
        maxMessageLength: settings.maxMessageLength ?? 2000,
      });
    }
  }, [settings, reset]);

  const updateSettings = useMutation({
    mutationFn: async (payload: FormValues) => {
      await saveSettings(streamKey, payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['stream-settings', streamKey] });
      toast.success('Settings saved');
    },
    onError: (err: any) => {
      toast.error(err?.response?.data?.message || 'Failed to save settings');
    },
  });

  const onSubmit = async (values: FormValues) => {
    setApiError(null);
    await updateSettings.mutateAsync(values);
  };

  if (error) {
    setApiError(error instanceof Error ? error.message : 'Failed to load settings');
  }

  return (
    <aside className="h-full w-80 overflow-y-auto p-4 border-l border-gray-200 dark:border-slate-700 bg-white dark:bg-slate-900">
      <h3 className="mb-4 text-lg font-semibold text-gray-900 dark:text-slate-100">Stream Settings</h3>

      {apiError && (
        <div className="mb-3 rounded-lg bg-red-100 dark:bg-red-900/30 px-3 py-2 text-sm text-red-700 dark:text-red-300">
          {apiError}
        </div>
      )}

      {isLoading ? (
        <div className="space-y-3 text-slate-400 dark:text-slate-500">
          <div className="h-5 w-3/4 animate-pulse rounded bg-gray-200 dark:bg-slate-800"></div>
          <div className="h-5 w-1/2 animate-pulse rounded bg-gray-200 dark:bg-slate-800"></div>
          <div className="h-5 w-full animate-pulse rounded bg-gray-200 dark:bg-slate-800"></div>
        </div>
      ) : (
        <form onSubmit={handleSubmit(onSubmit)} className="space-y-4">
          <ToggleSwitch
            label="Slow Mode"
            description="Limit messages per user"
            {...register('slowModeEnabled')}
          />

          {slowModeEnabled && (
            <div className="flex flex-col gap-1">
              <label htmlFor="slowModeSeconds" className="text-sm font-medium text-gray-900 dark:text-slate-200">Slow mode (seconds)</label>
              <input
                id="slowModeSeconds"
                type="number"
                min={0}
                max={3600}
                className="rounded-lg border border-gray-300 dark:border-slate-600 bg-gray-50 dark:bg-slate-800 px-3 py-2 text-sm text-gray-900 dark:text-slate-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
                {...register('slowModeSeconds', { valueAsNumber: true })}
              />
            </div>
          )}

          <ToggleSwitch
            label="Followers Only"
            description="Followers-only chat"
            {...register('followersOnlyMode')}
          />

          {followersOnlyMode && (
            <div className="flex flex-col gap-1">
              <label htmlFor="followersOnlyDurationMinutes" className="text-sm font-medium text-gray-900 dark:text-slate-200">Followers-only duration (minutes)</label>
              <input
                id="followersOnlyDurationMinutes"
                type="number"
                min={0}
                max={43200}
                className="rounded-lg border border-gray-300 dark:border-slate-600 bg-gray-50 dark:bg-slate-800 px-3 py-2 text-sm text-gray-900 dark:text-slate-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
                {...register('followersOnlyDurationMinutes', { valueAsNumber: true })}
              />
            </div>
          )}

          <ToggleSwitch
            label="Subscribers Only"
            description="Subscribers-only chat"
            {...register('subscribersOnlyMode')}
          />

          <ToggleSwitch
            label="Emotes Only"
            description="Only emotes allowed"
            {...register('emoteOnlyMode')}
          />

          <ToggleSwitch
            label="Profanity Filter"
            description="Auto-hide profanity"
            {...register('profanityFilterEnabled')}
          />

          <ToggleSwitch
            label="Link Protection"
            description="Block links in chat"
            {...register('linkProtectionEnabled')}
          />

          <div className="flex flex-col gap-1">
            <label htmlFor="maxMessageLength" className="text-sm font-medium text-gray-900 dark:text-slate-200">Max message length</label>
            <input
              id="maxMessageLength"
              type="number"
              min={1}
              max={2000}
              className="rounded-lg border border-gray-300 dark:border-slate-600 bg-gray-50 dark:bg-slate-800 px-3 py-2 text-sm text-gray-900 dark:text-slate-100 placeholder-gray-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
              {...register('maxMessageLength', { valueAsNumber: true })}
            />
          </div>

          <button
            type="submit"
            disabled={isSubmitting || isLoading}
            className="w-full rounded-xl bg-sky-500 px-5 py-3 text-sm font-semibold text-slate-950 shadow transition hover:bg-sky-400 disabled:cursor-not-allowed disabled:opacity-50"
          >
            {isSubmitting ? 'Saving...' : 'Save Settings'}
          </button>
        </form>
      )}
    </aside>
  );
};

const ToggleSwitch = forwardRef<
  HTMLInputElement,
  { label: string; description?: string } & InputHTMLAttributes<HTMLInputElement>
>(({ label, description, ...props }, ref) => (
  <label className="flex items-center justify-between">
    <div className="flex flex-col">
      <span className="text-sm font-medium text-gray-900 dark:text-slate-200">{label}</span>
      {description && <span className="text-xs text-gray-500 dark:text-slate-400">{description}</span>}
    </div>
    <input
      type="checkbox"
      ref={ref}
      className="h-5 w-5 cursor-pointer rounded border-gray-300 dark:border-slate-600 text-sky-500"
      {...props}
    />
  </label>
));

ToggleSwitch.displayName = 'ToggleSwitch';

export default StreamSettingsForm;
