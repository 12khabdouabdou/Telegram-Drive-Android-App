import React, { useRef, useCallback, useEffect } from 'react';
import { Folder, Plus, MoreVertical, X, Globe } from 'lucide-react';
import type { TelegramFolder, BandwidthStats } from '../../types';
import { formatBytes } from '../../utils';
import { hapticLight, hapticMedium } from '../../utils/haptic';

interface MobileSidebarProps {
  isOpen: boolean;
  folders: TelegramFolder[];
  activeFolderId: number | null;
  onSelectFolder: (id: number | null) => void;
  onClose: () => void;
  onCreateFolder: () => void;
  onFolderActions: (folder: TelegramFolder) => void;
  onFolderLongPress: (folder: TelegramFolder) => void;
  isConnected: boolean;
  bandwidth: BandwidthStats | undefined;
  children?: React.ReactNode;
}

export function MobileSidebar({
  isOpen,
  folders,
  activeFolderId,
  onSelectFolder,
  onClose,
  onCreateFolder,
  onFolderActions,
  onFolderLongPress,
  isConnected,
  bandwidth,
  children
}: MobileSidebarProps): React.JSX.Element {
  const longPressTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const startPos = useRef<{ x: number; y: number } | null>(null);

  const cancelLongPress = useCallback(() => {
    if (longPressTimer.current) {
      clearTimeout(longPressTimer.current);
      longPressTimer.current = null;
    }
    startPos.current = null;
  }, []);

  const handlePointerDown = (e: React.PointerEvent, folder: TelegramFolder) => {
    startPos.current = { x: e.clientX, y: e.clientY };
    longPressTimer.current = setTimeout(() => {
      hapticMedium();
      onFolderLongPress(folder);
      cancelLongPress();
    }, 500);
  };

  const handlePointerMove = (e: React.PointerEvent) => {
    if (!startPos.current) return;
    const dx = e.clientX - startPos.current.x;
    const dy = e.clientY - startPos.current.y;
    if (Math.sqrt(dx * dx + dy * dy) > 10) {
      cancelLongPress();
    }
  };

  useEffect(() => {
    return () => cancelLongPress();
  }, [cancelLongPress]);

  return (
    <div
      className={`fixed inset-y-0 left-0 z-50 flex flex-col bg-telegram-surface border-r border-telegram-border transition-transform duration-300 ease-in-out w-[85vw] max-w-[320px] ${
        isOpen ? 'translate-x-0' : '-translate-x-full'
      }`}
      onClick={(e) => e.stopPropagation()}
    >
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-telegram-border">
        <h2 className="text-lg font-semibold text-telegram-text">Folders</h2>
        <button
          onClick={() => {
            hapticLight();
            onClose();
          }}
          className="p-2 rounded-full hover:bg-telegram-hover text-telegram-subtext"
        >
          <X className="w-5 h-5" />
        </button>
      </div>

      {/* Folder List */}
      <div className="flex-1 overflow-y-auto py-2">
        <button
          className={`w-full flex items-center px-4 py-3 text-left ${
            activeFolderId === null
              ? 'bg-telegram-primary/15 border-r-2 border-telegram-primary'
              : 'hover:bg-telegram-hover'
          }`}
          onClick={() => {
            hapticLight();
            onSelectFolder(null);
            onClose();
          }}
        >
          <Folder className={`w-5 h-5 mr-3 ${activeFolderId === null ? 'text-telegram-primary' : 'text-telegram-subtext'}`} />
          <span className={`flex-1 truncate ${activeFolderId === null ? 'text-telegram-primary font-medium' : 'text-telegram-text'}`}>
            All Files
          </span>
        </button>

        {folders.map((folder) => {
          const isActive = activeFolderId === folder.id;
          return (
            <div
              key={folder.id}
              className={`w-full flex items-center px-4 py-3 text-left select-none ${
                isActive
                  ? 'bg-telegram-primary/15 border-r-2 border-telegram-primary'
                  : 'hover:bg-telegram-hover'
              }`}
              onPointerDown={(e) => handlePointerDown(e, folder)}
              onPointerMove={handlePointerMove}
              onPointerUp={cancelLongPress}
              onPointerCancel={cancelLongPress}
              style={{ touchAction: 'pan-y' }}
            >
              <button
                className="flex-1 flex items-center min-w-0"
                onClick={() => {
                  hapticLight();
                  onSelectFolder(folder.id);
                  onClose();
                }}
              >
                <Folder className={`w-5 h-5 mr-3 shrink-0 ${isActive ? 'text-telegram-primary' : 'text-telegram-subtext'}`} />
                <span className={`truncate mr-2 ${isActive ? 'text-telegram-primary font-medium' : 'text-telegram-text'}`}>
                  {folder.name}
                </span>
                {folder.is_public && (
                  <Globe className="w-3.5 h-3.5 text-telegram-subtext shrink-0" />
                )}
              </button>
              <button
                className="p-2 -mr-2 rounded-full hover:bg-black/5 dark:hover:bg-white/10 text-telegram-subtext shrink-0"
                onClick={(e) => {
                  e.stopPropagation();
                  hapticLight();
                  onFolderActions(folder);
                }}
              >
                <MoreVertical className="w-4 h-4" />
              </button>
            </div>
          );
        })}
      </div>

      {/* Footer */}
      <div className="p-4 border-t border-telegram-border space-y-4 bg-telegram-surface shrink-0">
        <button
          onClick={() => {
            hapticLight();
            onCreateFolder();
          }}
          className="w-full flex items-center justify-center py-2 border border-dashed border-telegram-subtext rounded-lg text-telegram-text hover:bg-telegram-hover transition-colors"
        >
          <Plus className="w-4 h-4 mr-2" />
          <span>Create Folder</span>
        </button>

        <div className="flex flex-col space-y-2 text-xs">
          <div className="flex items-center text-telegram-text">
            <span className={`w-2 h-2 rounded-full mr-2 ${isConnected ? 'bg-green-500 animate-pulse' : 'bg-red-500'}`} />
            {isConnected ? 'Connected' : 'Offline'}
          </div>
          {bandwidth && (
            <div className="flex items-center space-x-4 text-telegram-subtext font-mono">
              <span className="flex items-center text-green-500">
                <span className="mr-1">↓</span>
                {formatBytes(bandwidth.down_bytes)}/s
              </span>
              <span className="flex items-center text-blue-500">
                <span className="mr-1">↑</span>
                {formatBytes(bandwidth.up_bytes)}/s
              </span>
            </div>
          )}
        </div>
        
        {children}
      </div>
    </div>
  );
}
