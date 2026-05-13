import { useRef, useEffect, useState } from 'react';
import { ChatMessageDTO } from '../../types/backend';
import { MessageItem } from './MessageItem';

interface MessageListProps {
  messages: ChatMessageDTO[];
  currentUserId: number;
  onRequestContextMenu?: (message: ChatMessageDTO, x: number, y: number) => void;
}

export default function MessageList({ messages, currentUserId, onRequestContextMenu }: MessageListProps) {
  const listRef = useRef<HTMLDivElement>(null);
  const [isNearBottom, setIsNearBottom] = useState(true);
  const [unreadCount, setUnreadCount] = useState(0);

  // Scroll detection
  const handleScroll = () => {
    if (!listRef.current) return;
    const { scrollTop, scrollHeight, clientHeight } = listRef.current;
    const nearBottom = scrollHeight - scrollTop - clientHeight < 100;
    setIsNearBottom(nearBottom);
    if (nearBottom) setUnreadCount(0);
  };

  // Auto-scroll + unread tracking
  useEffect(() => {
    if (!listRef.current) return;
    if (isNearBottom) listRef.current.scrollTop = listRef.current.scrollHeight;
    else setUnreadCount(prev => prev + 1);
  }, [messages, isNearBottom]);

  const scrollToBottom = () => {
    listRef.current?.scrollTo({ top: listRef.current.scrollHeight, behavior: 'smooth' });
  };

  // Grouping helper
  const shouldShowAvatar = (idx: number) => {
    if (idx === 0) return true;
    const prev = messages[idx - 1];
    const curr = messages[idx];
    return prev.userId !== curr.userId || (Date.parse(curr.timestamp) - Date.parse(prev.timestamp)) > 60000;
  };

  return (
    <div ref={listRef} onScroll={handleScroll} className="flex-1 overflow-y-auto p-4 space-y-2">
      <div aria-live="polite" className="space-y-1">
        {messages.map((msg, i) => (
          <MessageItem 
            key={msg.id || i} 
            message={msg} 
            showAvatar={shouldShowAvatar(i)}
            onRequestContextMenu={onRequestContextMenu}
          />
        ))}
      </div>
      
      {!isNearBottom && unreadCount > 0 && (
        <button onClick={scrollToBottom} className="fixed bottom-24 left-1/2 -translate-x-1/2 bg-blue-600 text-white px-3 py-1 rounded-full shadow-lg text-sm z-50 hover:bg-blue-700 transition">
          ↓ {unreadCount} new message{unreadCount > 1 ? 's' : ''}
        </button>
      )}
    </div>
  );
}
