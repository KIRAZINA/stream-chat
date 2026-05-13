import { useState } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import { fetchStreams, createStream } from '../api/streams';
import StreamCard from '../components/streams/StreamCard';
import { useAuthStore } from '../stores/auth-store';

const DashboardPage = () => {
  const navigate = useNavigate();
  const [title, setTitle] = useState('');
  const [category, setCategory] = useState('Music');
  const { logout } = useAuthStore();

  const { data: streams = [] } = useQuery({
    queryKey: ['streams'],
    queryFn: fetchStreams
  });

  const createStreamMutation = useMutation({
    mutationFn: (payload: { title: string; category: string }) => createStream(payload),
    onSuccess: (stream) => {
      navigate(`/stream/${stream.streamKey}`);
    }
  });

  return (
    <div className="mx-auto flex min-h-screen w-full max-w-6xl flex-col gap-6 px-4 py-6">
      <header className="flex flex-col gap-4 rounded-3xl border border-slate-700 bg-slate-900/90 p-6 text-slate-100 shadow-glow sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h1 className="text-3xl font-semibold">Панель управления</h1>
          <p className="mt-2 text-slate-400">Управляйте стримами, переходите в чат и следите за зрителями.</p>
        </div>
        <button onClick={() => logout()} className="rounded-2xl bg-rose-500 px-5 py-3 text-sm font-semibold text-slate-950 transition hover:bg-rose-400">
          Выйти
        </button>
      </header>

      <section className="grid gap-4 rounded-3xl border border-slate-700 bg-slate-900/90 p-6 shadow-glow sm:grid-cols-[1fr_320px]">
        <div>
          <h2 className="text-xl font-semibold text-slate-100">Существующие стримы</h2>
          <div className="mt-4 grid gap-4">
            {streams.length === 0 ? (
              <div className="rounded-3xl border border-slate-700 bg-slate-950/80 p-6 text-slate-400">Пока нет активных стримов.</div>
            ) : (
              streams.map((stream) => <StreamCard key={stream.streamKey} stream={stream} />)
            )}
          </div>
        </div>

        <div className="rounded-3xl border border-slate-700 bg-slate-950/80 p-6">
          <h3 className="text-lg font-semibold text-slate-100">Создать новый стрим</h3>
          <div className="mt-4 space-y-4">
            <label className="block text-sm text-slate-300">
              Название
              <input value={title} onChange={(event) => setTitle(event.target.value)} className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 outline-none focus:border-sky-500" />
            </label>
            <label className="block text-sm text-slate-300">
              Категория
              <input value={category} onChange={(event) => setCategory(event.target.value)} className="mt-2 w-full rounded-2xl border border-slate-700 bg-slate-900 px-4 py-3 text-slate-100 outline-none focus:border-sky-500" />
            </label>
            <button onClick={() => createStreamMutation.mutate({ title, category })} disabled={!title.trim()} className="w-full rounded-2xl bg-sky-500 px-4 py-3 text-sm font-semibold text-slate-950 transition hover:bg-sky-400 disabled:cursor-not-allowed disabled:opacity-50">
              Создать стрим
            </button>
          </div>
        </div>
      </section>
    </div>
  );
};

export default DashboardPage;
