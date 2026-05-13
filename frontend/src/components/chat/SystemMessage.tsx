/**
 * SystemMessage component for displaying system notifications
 */

import React from 'react';

interface SystemMessageProps {
  type: 'info' | 'warning' | 'error' | 'success';
  message: string;
}

export default function SystemMessage({ type, message }: SystemMessageProps) {
  const styles = {
    info: 'border-blue-400/20 bg-blue-500/10 text-blue-100',
    warning: 'border-yellow-400/20 bg-yellow-500/10 text-yellow-100',
    error: 'border-red-400/20 bg-red-500/10 text-red-100',
    success: 'border-green-400/20 bg-green-500/10 text-green-100'
  };

  const icons = {
    info: 'ℹ️',
    warning: '⚠️',
    error: '❌',
    success: '✅'
  };

  return (
    <div className={`rounded-lg border px-4 py-3 text-sm ${styles[type]}`}>
      <span className="mr-2">{icons[type]}</span>
      {message}
    </div>
  );
}
