import { useRef, useState, useCallback, useEffect } from 'react';
import { DownloadCloud, Trash2, Pencil, CheckSquare, X, Check, MoreVertical, Eye, Share2 } from 'lucide-react';
import { toast } from 'sonner';
import { useVirtualizer } from '@tanstack/react-virtual';
import { FileTypeIcon } from '../shared/FileTypeIcon';
import { ActionPopover, ActionItem } from './ActionPopover';
import { TelegramFile } from '../../types';

interface TouchFileListProps {
  files: TelegramFile[];
  isLoading: boolean;
  onDownload: (file: TelegramFile) => void;
  onDelete: (file: TelegramFile) => void;
  onPreview: (file: TelegramFile) => void;
  onRename: (file: TelegramFile) => void;
  onShare?: (file: TelegramFile) => void;
  selectedIds: number[];
  onToggleSelection: (id: number) => void;
  onSelectAll: () => void;
  onClearSelection: () => void;
  fetchNextPage?: () => void;
  hasNextPage?: boolean;
  isFetchingNextPage?: boolean;
}

export function TouchFileList({ files, isLoading, onDownload, onDelete, onPreview, onRename, onShare, selectedIds, onToggleSelection, onSelectAll, onClearSelection }: TouchFileListProps) {
  const [selectionMode, setSelectionMode] = useState(false);
  const [actionMenuFile, setActionMenuFile] = useState<TelegramFile | null>(null);
  const isSelectionActive = selectionMode || selectedIds.length > 0;

  // Swipe and Long-press state/refs
  const longPressTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const longPressPosRef = useRef<{ x: number; y: number } | null>(null);
  const swipingRowIdRef = useRef<number | null>(null);
  const LONG_PRESS_DURATION = 500;

  const [swipeOffsets, setSwipeOffsets] = useState<Map<number, number>>(new Map());
  const [isSwipingMap, setIsSwipingMap] = useState<Map<number, boolean>>(new Map());

  // Handlers
  const handlePointerDown = useCallback((e: React.PointerEvent, file: TelegramFile) => {
    if (isSelectionActive) return;
    longPressPosRef.current = { x: e.clientX, y: e.clientY };
    swipingRowIdRef.current = file.id;
    e.currentTarget.setPointerCapture(e.pointerId);

    setIsSwipingMap(prev => {
      const next = new Map(prev);
      next.set(file.id, true);
      return next;
    });

    longPressTimerRef.current = setTimeout(() => {
      setSelectionMode(true);
      onToggleSelection(file.id);
      toast.info('Selection mode — tap files to select more');
      swipingRowIdRef.current = null;
    }, LONG_PRESS_DURATION);
  }, [isSelectionActive, onToggleSelection]);

  const handlePointerMove = useCallback((e: React.PointerEvent, file: TelegramFile) => {
    if (!longPressPosRef.current) return;
    
    const dx = e.clientX - longPressPosRef.current.x;
    const dy = e.clientY - longPressPosRef.current.y;
    const absDx = Math.abs(dx);
    const absDy = Math.abs(dy);

    if (absDx > 10 || absDy > 10) {
      if (longPressTimerRef.current) {
        clearTimeout(longPressTimerRef.current);
        longPressTimerRef.current = null;
      }
    }

    if (swipingRowIdRef.current === file.id) {
      if (absDx > absDy && absDx > 10) {
        let newOffset = dx;
        if (newOffset > 0 && !onShare) newOffset = 0; // block right swipe if no share
        
        setSwipeOffsets(prev => {
          const next = new Map(prev);
          next.set(file.id, Math.max(-100, Math.min(100, newOffset)));
          return next;
        });
      }
    }
  }, [onShare]);

  const handlePointerUp = useCallback((e: React.PointerEvent, file: TelegramFile) => {
    if (longPressTimerRef.current) {
      clearTimeout(longPressTimerRef.current);
      longPressTimerRef.current = null;
    }
    
    e.currentTarget.releasePointerCapture(e.pointerId);

    if (swipingRowIdRef.current === file.id && longPressPosRef.current) {
      const dx = e.clientX - longPressPosRef.current.x;
      
      if (dx < -60) {
        onDelete(file);
      } else if (dx > 60 && onShare) {
        onShare(file);
      }
      
      setSwipeOffsets(prev => {
        const next = new Map(prev);
        next.set(file.id, 0);
        return next;
      });
    }

    setIsSwipingMap(prev => {
      const next = new Map(prev);
      next.set(file.id, false);
      return next;
    });
    
    longPressPosRef.current = null;
    swipingRowIdRef.current = null;
  }, [onDelete, onShare]);

  // Build action items for a file's popover menu
  const buildFileActions = useCallback((file: TelegramFile): ActionItem[] => {
    const actions: ActionItem[] = [
      {
        label: 'Preview',
        icon: <Eye className="w-4 h-4" />,
        onClick: () => onPreview(file),
      },
      {
        label: 'Download',
        icon: <DownloadCloud className="w-4 h-4" />,
        onClick: () => onDownload(file),
      },
      {
        label: 'Rename',
        icon: <Pencil className="w-4 h-4" />,
        onClick: () => onRename(file),
      },
    ];

    if (onShare && file.type !== 'folder') {
      actions.push({
        label: 'Share',
        icon: <Share2 className="w-4 h-4" />,
        onClick: () => onShare(file),
      });
    }

    actions.push({
      label: 'Delete',
      icon: <Trash2 className="w-4 h-4" />,
      onClick: () => onDelete(file),
      destructive: true,
    });

    return actions;
  }, [onPreview, onDownload, onRename, onDelete, onShare]);

  const [scrollElement, setScrollElement] = useState<HTMLElement | null>(null);
  
  useEffect(() => {
    // We get the scroll container from MobileDashboard
    const element = document.getElementById('mobile-scroll-container');
    if (element) {
      setScrollElement(element);
    }
  }, []);

  const virtualizer = useVirtualizer({
    count: files.length,
    getScrollElement: () => scrollElement,
    estimateSize: () => 76, // Height of file row + gap
    overscan: 5,
  });

  return (
    <>
      {isLoading && (
        <div className="flex flex-col items-center justify-center py-16 space-y-3 text-center">
          <div className="animate-spin rounded-full h-7 w-7 border-t-2 border-b-2 border-telegram-primary"></div>
          <p className="text-xs text-telegram-subtext font-semibold">Retrieving your files...</p>
        </div>
      )}

      {!isLoading && files.length === 0 && (
        <div className="flex flex-col items-center justify-center py-16 space-y-3 text-center px-4">
          <div className="p-4 rounded-2xl bg-telegram-hover/10 text-telegram-subtext border border-telegram-border/10">
            📁
          </div>
          <h4 className="text-sm font-bold text-telegram-text">This folder is empty</h4>
          <p className="text-xs text-telegram-subtext max-w-xs leading-relaxed">
            Upload files or synchronise folders to begin managing content.
          </p>
        </div>
      )}

      {!isLoading && files.length > 0 && (
        <>
          {/* Selection mode toggle & batch action bar */}
          <div className="flex items-center gap-2 mb-3">
            <button
              onClick={() => {
                if (isSelectionActive) {
                  onClearSelection();
                }
                setSelectionMode(!selectionMode);
              }}
              className={`flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold transition-all duration-200 active:scale-95 ${
                isSelectionActive
                  ? 'bg-telegram-primary/20 text-telegram-primary border border-telegram-primary/30'
                  : 'bg-telegram-hover/20 text-telegram-subtext border border-telegram-border/30'
              }`}
            >
              <CheckSquare className="w-3.5 h-3.5" />
              {isSelectionActive ? `${selectedIds.length} selected` : 'Select'}
            </button>
            {isSelectionActive && (
              <>
                <button
                  onClick={onSelectAll}
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-[10px] font-semibold bg-telegram-hover/20 text-telegram-subtext border border-telegram-border/30 active:scale-95 transition-all duration-200"
                >
                  <Check className="w-3 h-3" />
                  All
                </button>
                <button
                  onClick={onClearSelection}
                  className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl text-[10px] font-semibold bg-telegram-hover/20 text-telegram-subtext border border-telegram-border/30 active:scale-95 transition-all duration-200"
                >
                  <X className="w-3 h-3" />
                  Clear
                </button>
              </>
            )}
          </div>

          {/* File list — virtualized for extreme performance on large folders */}
          <div 
            className="relative w-full pb-20"
            style={{ height: `${virtualizer.getTotalSize()}px` }}
          >
            {virtualizer.getVirtualItems().map((virtualItem) => {
              const file = files[virtualItem.index];
              const isSelected = selectedIds.includes(file.id);
              const swipeOffset = swipeOffsets.get(file.id) || 0;
              const isSwiping = isSwipingMap.get(file.id) || false;

              return (
                <div
                  key={virtualItem.key}
                  style={{
                    position: 'absolute',
                    top: 0,
                    left: 0,
                    width: '100%',
                    height: `${virtualItem.size}px`,
                    transform: `translateY(${virtualItem.start}px)`,
                    paddingBottom: '10px' // for spacing equivalent to space-y-2.5
                  }}
                >
                  <div className="relative w-full h-full overflow-hidden rounded-2xl">
                    {/* Background swipe actions */}
                    {swipeOffset !== 0 && (
                      <div className={`absolute inset-0 flex justify-between ${swipeOffset > 0 ? 'bg-green-500' : 'bg-red-500'} ${Math.abs(swipeOffset) > 60 ? 'saturate-150' : 'opacity-80'}`}>
                        {swipeOffset > 0 && onShare && (
                          <div className="flex items-center justify-start px-5 w-1/2 text-white">
                            <Share2 className="w-5 h-5 mr-2" />
                            <span className="text-sm font-semibold">Share</span>
                          </div>
                        )}
                        {swipeOffset < 0 && (
                          <div className="flex items-center justify-end px-5 w-1/2 ml-auto text-white">
                            <span className="text-sm font-semibold">Delete</span>
                            <Trash2 className="w-5 h-5 ml-2" />
                          </div>
                        )}
                      </div>
                    )}

                    {/* Main Row Content */}
                    <div
                      onPointerDown={(e) => handlePointerDown(e, file)}
                      onPointerMove={(e) => handlePointerMove(e, file)}
                      onPointerUp={(e) => handlePointerUp(e, file)}
                      onPointerCancel={(e) => handlePointerUp(e, file)}
                      onClick={() => {
                        if (isSelectionActive) {
                          onToggleSelection(file.id);
                        } else {
                          onPreview(file);
                        }
                      }}
                      style={{
                        transform: `translateX(${swipeOffset}px)`,
                        transition: isSwiping ? 'none' : 'transform 200ms ease-out',
                        touchAction: 'pan-y'
                      }}
                      className={`relative z-10 h-full flex items-center justify-between p-3.5 rounded-2xl bg-telegram-hover border cursor-pointer active:scale-95 hover:shadow-md ${
                        isSelected ? 'border-telegram-primary/50 bg-telegram-primary/10' : 'border-telegram-border/20 bg-telegram-bg'
                      }`}
                    >
                      <div className="flex items-center gap-3.5 min-w-0">
                        {/* Selection checkbox in selection mode */}
                        {isSelectionActive && (
                          <div className={`flex-shrink-0 w-5 h-5 rounded-md border-2 flex items-center justify-center transition-all duration-200 ${
                            isSelected
                              ? 'bg-telegram-primary border-telegram-primary text-black'
                              : 'border-telegram-border/50 bg-transparent'
                          }`}>
                            {isSelected && <Check className="w-3.5 h-3.5" />}
                          </div>
                        )}
                        <div className="flex-shrink-0">
                          <FileTypeIcon filename={file.name} />
                        </div>
                        <div className="min-w-0">
                          <p className="text-xs font-semibold text-telegram-text truncate max-w-[150px] leading-snug">{file.name}</p>
                          <div className="flex items-center gap-2 mt-1">
                            <span className="text-[10px] text-telegram-subtext/80 font-medium font-mono">{file.sizeStr}</span>
                            <span className="w-1 h-1 bg-telegram-border rounded-full" />
                            <span className="text-[10px] text-telegram-subtext/80 font-medium">{file.created_at || 'Sync'}</span>
                          </div>
                        </div>
                      </div>

                      {/* ⋮ menu button — replaces swipe gesture */}
                      {!isSelectionActive && (
                        <button
                          onPointerDown={(e) => e.stopPropagation()}
                          onClick={(e) => {
                            e.stopPropagation();
                            setActionMenuFile(file);
                          }}
                          className="flex-shrink-0 p-2 rounded-xl hover:bg-telegram-hover/40 active:bg-telegram-hover/60 text-telegram-subtext/60 hover:text-telegram-subtext transition-all duration-200"
                          aria-label="File actions"
                        >
                          <MoreVertical className="w-4 h-4" />
                        </button>
                      )}
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* Action popover for file operations */}
      {actionMenuFile && (
        <ActionPopover
          title={actionMenuFile.name}
          actions={buildFileActions(actionMenuFile)}
          onClose={() => setActionMenuFile(null)}
        />
      )}
    </>
  );
}
