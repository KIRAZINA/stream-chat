/**
 * Emote parsing and rendering utilities
 */

import React from 'react';
import { MessageFragmentDTO } from '../types/backend';

export function renderFragments(fragments: MessageFragmentDTO[]): React.ReactNode[] {
  return fragments.map((frag, i) => {
    if (frag.type === 'EMOTE' && frag.imageUrl) {
      return (
        <img key={i} src={frag.imageUrl} alt={frag.emoteCode || 'emote'} className="inline-block h-6 w-6 align-middle object-contain" />
      );
    }
    return <span key={i}>{linkifyText(frag.text)}</span>;
  });
}

export function linkifyText(text: string): React.ReactNode {
  const urlRegex = /(https?:\/\/[^\s]+)/g;
  const parts = text.split(urlRegex);
  return parts.map((part, i) => {
    if (urlRegex.test(part)) return <a key={i} href={part} target="_blank" rel="noopener noreferrer" className="text-blue-400 hover:underline">{part}</a>;
    return <span key={i}>{part}</span>;
  });
}
