import { useState, useEffect } from 'react';
import { useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { fetchStreamDetails, fetchPresence } from '../api/streams';
import ChatWindow from '../components/chat/ChatWindow';
import StreamSettingsForm from '../components/streams/StreamSettingsForm';
import PresenceIndicator from '../components/streams/PresenceIndicator';
import DarkModeToggle from '../components/ui/DarkModeToggle';

const StreamPage = () => {
  const { streamKey } = useParams<{ streamKey: string }>();
  const [showModPanel, setShowModPanel] = useState(false);

  const streamQuery = useQuery({
    queryKey: ['stream', streamKey],
    queryFn: () => fetchStreamDetails(streamKey ?? ''),
    enabled: Boolean(streamKey)
  });
  const presenceQuery = useQuery({
    queryKey: ['presence', streamKey],
    queryFn: () => fetchPresence(streamKey ?? ''),
    enabled: Boolean(streamKey),
    refetchInterval: 15000
  });

  useEffect(() => {
    if (streamQuery.data) {
      document.title = `${streamQuery.data.title} • Stream Chat`;
    }
  }, [streamQuery.data]);

   if (!streamKey) {
     return <div className="p-6 text-slate-200">Invalid stream key.</div>;
   }

  return (
    <div className="h-screen flex flex-col bg-gray-50 dark:bg-gray-900 text-gray-900 dark:text-gray-100 transition-colors">
      {/* Header */}
      <header className="flex items-center justify-between p-4 border-b border-gray-200 dark:border-gray-700">
        <h1 className="text-xl font-semibold">{streamQuery.data?.title || 'Stream Chat'}</h1>
        <div className="flex items-center gap-4">
          <PresenceIndicator viewers={presenceQuery.data?.activeViewers ?? 0} />
          <DarkModeToggle />
          {/* Mobile hamburger */}
          <button 
            onClick={() => setShowModPanel(!showModPanel)}
            className="md:hidden p-2 rounded hover:bg-gray-200 dark:hover:bg-gray-700"
          >
            ☰
          </button>
        </div>
      </header>

      {/* Main content */}
      <div className="flex-1 flex overflow-hidden">
        <div className="flex-1">
          <ChatWindow streamKey={streamKey} />
        </div>
        
        {/* Desktop sidebar */}
        <div className="hidden md:block w-80 border-l border-gray-200 dark:border-gray-700">
          <StreamSettingsForm streamKey={streamKey} />
        </div>

        {/* Mobile mod panel overlay */}
        {showModPanel && (
          <div className="md:hidden fixed inset-0 z-50 bg-black bg-opacity-50" onClick={() => setShowModPanel(false)}>
            <div className="absolute right-0 top-0 h-full w-80 bg-white dark:bg-gray-800" onClick={e => e.stopPropagation()}>
              <StreamSettingsForm streamKey={streamKey} />
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

export default StreamPage;
