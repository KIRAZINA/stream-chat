/**
 * EmotePicker — simple emoji/emote selector popover.
 */

import { useState } from 'react';

const EMOTES = ['😂', '❤️', '🔥', '👏', '😮', '😢', '😡', '🎉', '💯', '👍',
                '👎', '🤣', '😭', '🙏', '😍', '🤔', '🤝', '👀', '🏆', '✨'];

interface EmotePickerProps {
  onSelect: (emote: string) => void;
  onClose: () => void;
}

export function EmotePicker({ onSelect, onClose }: EmotePickerProps) {
  const [search, setSearch] = useState('');

  const filtered = EMOTES.filter((e) =>
    search === '' || e.includes(search)
  );

  return (
    <div className="absolute bottom-full mb-2 right-0 z-40 w-64 rounded-2xl border border-slate-700 bg-slate-900 p-3 shadow-xl">
      <input
        type="text"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
        placeholder="Search emotes…"
        className="w-full mb-2 px-3 py-1.5 bg-slate-800 border border-slate-700 rounded-lg text-sm text-white placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-sky-500"
        autoFocus
      />

      <div className="grid grid-cols-5 gap-1 max-h-40 overflow-y-auto">
        {filtered.map((emote) => (
          <button
            key={emote}
            onClick={() => { onSelect(emote); onClose(); }}
            className="flex items-center justify-center h-9 w-9 rounded-xl text-xl hover:bg-slate-700 transition-colors"
            title={emote}
          >
            {emote}
          </button>
        ))}
        {filtered.length === 0 && (
          <p className="col-span-5 text-center text-xs text-slate-500 py-4">No emotes found</p>
        )}
      </div>
    </div>
  );
}
