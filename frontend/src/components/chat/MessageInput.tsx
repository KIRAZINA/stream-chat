/**
 * MessageInput component for sending chat messages
 */

import { useState, useEffect, useRef } from 'react';
import { Textarea } from '../ui/Input';
import { Button } from '../ui/Button';
import { useChatStore } from '../../stores/chat-store';
import { useAuth } from '../../hooks/useAuth';

interface MessageInputProps {
  onSend: (content: string, replyTo?: number) => void;
  disabled?: boolean;
  maxLength?: number;
}

export default function MessageInput({ onSend, disabled = false, maxLength }: MessageInputProps) {
  const [value, setValue] = useState('');
  const { selectedReply, setReply } = useChatStore();
  const { isAuthenticated } = useAuth();
  const textareaRef = useRef<HTMLTextAreaElement>(null);

  useEffect(() => {
    if (textareaRef.current) {
      textareaRef.current.focus();
    }
  }, []);

  const handleSend = () => {
    if (!value.trim() || disabled) return;
    onSend(value.trim(), selectedReply?.messageId);
    setValue('');
    setReply(null);
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  const handleCancelReply = () => {
    setReply(null);
  };

  if (!isAuthenticated) {
    return (
      <div className="rounded-3xl border border-slate-700 bg-slate-900/90 p-4 text-center text-slate-400">
        Please log in to chat
      </div>
    );
  }

  return (
    <div className="space-y-2 rounded-3xl border border-slate-700 bg-slate-900/90 p-3">
      {/* Reply preview */}
      {selectedReply && (
        <div className="flex items-center justify-between rounded-lg bg-slate-800 px-3 py-2">
          <div className="text-sm text-slate-300">
            <span className="font-semibold">Replying to {selectedReply.username}:</span>
            <span className="ml-2 text-slate-400">{selectedReply.content}</span>
          </div>
          <button
            onClick={handleCancelReply}
            className="text-slate-400 hover:text-white"
            title="Cancel reply"
          >
            <svg className="w-5 h-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>
      )}

      {/* Input */}
      <div className="flex gap-3">
        <Textarea
          ref={textareaRef}
          value={value}
          onChange={(e) => setValue(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder="Type a message... (Enter to send, Shift+Enter for new line)"
          className="flex-1 min-h-[60px] max-h-[120px]"
          disabled={disabled}
          maxLength={maxLength}
          aria-label="Message input"
        />
        <Button 
          onClick={handleSend} 
          disabled={!value.trim() || disabled} 
          className="self-end focus:outline-none focus:ring-2 focus:ring-blue-400"
          aria-label="Send message"
        >
          Send
        </Button>
      </div>

      {maxLength && (
        <div className="text-right text-xs text-slate-500">
          {value.length} / {maxLength}
        </div>
      )}
    </div>
  );
}
