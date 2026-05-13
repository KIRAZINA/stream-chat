/**
 * Emote parsing and rendering utilities
 */

import React from 'react';
import { MessageFragmentDTO } from '../types/backend';

/**
 * Parse message content into text and emote fragments
 * This is a placeholder implementation - actual emote parsing would depend on
 * your emote provider (Twitch, 7TV, BTTV, etc.)
 */
export function parseMessageFragments(content: string): MessageFragmentDTO[] {
  const fragments: MessageFragmentDTO[] = [];
  const emoteRegex = /:(\w+):/g; // Example: :emoteCode:
  let lastIndex = 0;
  let match;

  while ((match = emoteRegex.exec(content)) !== null) {
    // Add text before emote
    if (match.index > lastIndex) {
      const text = content.slice(lastIndex, match.index);
      if (text) {
        fragments.push({ type: 'TEXT', text });
      }
    }

    // Add emote
    fragments.push({
      type: 'EMOTE',
      text: match[0],
      emoteCode: match[1],
      imageUrl: getEmoteUrl(match[1])
    });

    lastIndex = emoteRegex.lastIndex;
  }

  // Add remaining text
  if (lastIndex < content.length) {
    const text = content.slice(lastIndex);
    if (text) {
      fragments.push({ type: 'TEXT', text });
    }
  }

  // If no emotes found, return entire content as text
  if (fragments.length === 0) {
    fragments.push({ type: 'TEXT', text: content });
  }

  return fragments;
}

/**
 * Get emote image URL from emote code
 * This is a placeholder - replace with your actual emote CDN
 */
function getEmoteUrl(emoteCode: string): string {
  // Example: Replace with your emote CDN URL
  return `https://static-cdn.jtvnw.net/emoticons/v2/${emoteCode}/default/dark/1.0`;
}

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
