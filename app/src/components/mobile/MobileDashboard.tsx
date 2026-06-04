import { motion, AnimatePresence } from 'framer-motion';
import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { Folder, Download, Menu, RefreshCw, UploadCloud, Trash2, Pencil, Globe, FilePlus, FolderUp, X, Search, Share2, ArrowUpDown, ArrowUp, ArrowDown, Plus, CheckCircle2, AlertCircle, Clock, RotateCcw } from 'lucide-react';
import { invoke } from '@tauri-apps/api/core';
import { listen } from '@tauri-apps/api/event';
import { useInfiniteQuery, useQuery } from '@tanstack/react-query';
import { toast } from 'sonner';
import { BottomNavBar } from './BottomNavBar';
import { TouchFileList } from './TouchFileList';
import { ThemeToggle } from '../shared/ThemeToggle';
import AdsterraBanner from '../shared/AdsterraBanner';
import { ActionPopover, ActionItem } from './ActionPopover';
import { PromptModal } from './PromptModal';
import { BulkActionBar } from './BulkActionBar';
import { MobileSidebar } from './MobileSidebar';
import { MobileSettings } from './MobileSettings';
import { usePlatform } from '../../hooks/usePlatform';
import { useTelegramConnection } from '../../hooks/useTelegramConnection';
import { useFileUpload } from '../../hooks/useFileUpload';
import { useFileDownload } from '../../hooks/useFileDownload';
import { useFileOperations } from '../../hooks/useFileOperations';
import { formatBytes, isMediaFile, isPdfFile, isImageFile, nativeShareOrCopy } from '../../utils';
import { hapticLight, hapticMedium } from '../../utils/haptic';
import { MediaPlayer } from '../desktop/dashboard/MediaPlayer';
import { PdfViewer } from '../desktop/dashboard/PdfViewer';
import { PreviewModal } from '../desktop/dashboard/PreviewModal';
import { useTheme } from '../../context/ThemeContext';
import { TelegramFile, TelegramFolder, BandwidthStats, QueueItem, DownloadItem } from '../../types';
import { useSettings } from '../../context/SettingsContext';
import { version as appVersion } from '../../../package.json';

type FileTypeFilter = 'all' | 'images' | 'videos' | 'audio' | 'docs';

export default function MobileDashboard({ onLogout }: { onLogout?: () => void }) {
  const [activeTab, setActiveTab] = useState<'files' | 'downloads' | 'settings'>('files');
  const [isSidebarOpen, setIsSidebarOpen] = useState(false);
  const { isAndroid } = usePlatform();
  const { theme } = useTheme();
  const { settings } = useSettings();

  // Settings: proxy effect stays in dashboard (applies on mount + when proxy changes)
  useEffect(() => {
    const applyProxy = async () => {
      try {
        await invoke('cmd_apply_proxy_settings', {
          enabled: settings.proxyEnabled,
          proxyType: settings.proxyType,
          host: settings.proxyHost,
          port: settings.proxyPort,
          username: settings.proxyUsername,
          password: settings.proxyPassword,
          secret: settings.proxySecret,
        });
      } catch {
        // best-effort sync
      }
    };
    applyProxy();
  }, [
    settings.proxyEnabled, settings.proxyType, settings.proxyHost,
    settings.proxyPort, settings.proxyUsername, settings.proxyPassword,
    settings.proxySecret,
  ]);

  const logoutHandler = useMemo(() => onLogout || (() => {}), [onLogout]);

  const {
    store, folders, activeFolderId, setActiveFolderId, isSyncing, isConnected,
    handleLogout, handleSyncFolders, handleCreateFolder, handleFolderDelete,
    handleFolderRename, handleFolderToggleVisibility, handleExportFolderInvite
  } = useTelegramConnection(logoutHandler);

  const { uploadQueue, handleManualUpload, handleFolderUpload, handleDropUpload, cancelItem: cancelUpload, retryItem: retryUpload, cancelAll: cancelAllUploads } = useFileUpload(activeFolderId, store);
  const { downloadQueue, queueDownload, cancelItem: cancelDownload, retryItem: retryDownload, cancelAll: cancelAllDownloads } = useFileDownload(store);

  // Sort state
  const [sortField, setSortField] = useState<'name' | 'size' | 'date'>('name');
  const [sortDirection, setSortDirection] = useState<'asc' | 'desc'>('asc');
  const [showSortMenu, setShowSortMenu] = useState(false);

  // File-type filter (Tier 5.1)
  const [fileTypeFilter, setFileTypeFilter] = useState<FileTypeFilter>('all');

  // Search state
  const [searchTerm, setSearchTerm] = useState('');
  const [searchResults, setSearchResults] = useState<TelegramFile[]>([]);
  const [isSearching, setIsSearching] = useState(false);

  // Pull-to-refresh state (Tier 2.2)
  const [pullDistance, setPullDistance] = useState(0);
  const [isPullRefreshing, setIsPullRefreshing] = useState(false);
  const pullStartY = useRef<number | null>(null);
  const PULL_THRESHOLD = 80;

  // PromptModal state (Tier 1.1: replace window.prompt)
  const [renamePrompt, setRenamePrompt] = useState<{ file: TelegramFile; currentName: string } | null>(null);
  const [createFolderPrompt, setCreateFolderPrompt] = useState(false);

  // Listen for shared files from Android Intent
  useEffect(() => {
    // Pull any cached files that were shared while the app was closed
    invoke<string[]>('cmd_get_pending_shared_files').then(files => {
        if (files && files.length > 0) {
            toast.success(`Received ${files.length} shared file(s). Uploading to current folder!`);
            handleDropUpload(files);
            setActiveTab('downloads');
        }
    }).catch(console.error);

    let unlisten: (() => void) | undefined;
    listen<string[]>('incoming-shared-files', (event) => {
        if (event.payload && event.payload.length > 0) {
            toast.success(`Received ${event.payload.length} shared file(s). Uploading to current folder!`);
            handleDropUpload(event.payload);
            setActiveTab('downloads');
        }
    }).then(f => { unlisten = f; });

    let unlistenAutoBackup: (() => void) | undefined;
    listen<string[]>('auto-backup-discovered', (event) => {
        if (event.payload && event.payload.length > 0) {
            handleDropUpload(event.payload);
        }
    }).then(f => { unlistenAutoBackup = f; });

    return () => {
      if (unlisten) unlisten();
      if (unlistenAutoBackup) unlistenAutoBackup();
    };
  }, [handleDropUpload]);

  const [playingFile, setPlayingFile] = useState<TelegramFile | null>(null);
  const [pdfFile, setPdfFile] = useState<TelegramFile | null>(null);
  const [previewFile, setPreviewFile] = useState<TelegramFile | null>(null);
  const [showMovePicker, setShowMovePicker] = useState(false);

  const adVisible = !playingFile && !pdfFile && !previewFile;

  // Real files loader
  const { data, isLoading, fetchNextPage, hasNextPage, isFetchingNextPage, refetch } = useInfiniteQuery({
    queryKey: ['files', activeFolderId],
    queryFn: ({ pageParam }) => invoke<any[]>('cmd_get_files', { folderId: activeFolderId, offsetId: pageParam || null, limit: 100 }).then(res => res.map(f => ({
      ...f,
      sizeStr: formatBytes(f.size),
      type: f.icon_type || (f.name.endsWith('/') ? 'folder' : 'file')
    }))),
    getNextPageParam: (lastPage) => lastPage.length === 100 ? lastPage[lastPage.length - 1].id : undefined,
    enabled: !!store,
    initialPageParam: null,
  });
  const allFiles = useMemo(() => data ? data.pages.flat() : [], [data]);

  const [selectedIds, setSelectedIds] = useState<number[]>([]);
  const [fileRenames, setFileRenames] = useState<Map<number, string>>(new Map());

  // File-type filter helper (Tier 5.1)
  const matchesFileType = useCallback((name: string, type: string | undefined, filter: FileTypeFilter): boolean => {
    if (filter === 'all') return true;
    if (type === 'folder') return false;
    const lower = name.toLowerCase();
    if (filter === 'images') return isImageFile(lower);
    if (filter === 'videos') return /\.(mp4|webm|ogg|mov|mkv|avi)$/.test(lower);
    if (filter === 'audio') return /\.(mp3|wav|aac|flac|m4a|opus)$/.test(lower);
    if (filter === 'docs') return !isImageFile(lower) && !/\.(mp4|webm|ogg|mov|mkv|avi|mp3|wav|aac|flac|m4a|opus)$/.test(lower);
    return true;
  }, []);

  const displayFilesRaw = useMemo<TelegramFile[]>(() => {
      let files = searchTerm.length > 2
          ? searchResults
          : allFiles.filter((f: TelegramFile) => f.name.toLowerCase().includes(searchTerm.toLowerCase()));
      // Apply file-type filter
      if (fileTypeFilter !== 'all') {
        files = files.filter(f => matchesFileType(f.name, f.type, fileTypeFilter));
      }
      return [...files].sort((a, b) => {
          let cmp = 0;
          if (sortField === 'name') cmp = a.name.localeCompare(b.name);
          else if (sortField === 'size') cmp = a.size - b.size;
          else if (sortField === 'date') {
            const dateA = a.created_at ? new Date(a.created_at).getTime() : a.id;
            const dateB = b.created_at ? new Date(b.created_at).getTime() : b.id;
            cmp = dateA - dateB;
          }
          return sortDirection === 'asc' ? cmp : -cmp;
      });
  }, [searchTerm, searchResults, allFiles, sortField, sortDirection, fileTypeFilter, matchesFileType]);

  const { handleDelete: handleDeleteOp, handleBulkDelete, handleBulkDownload, handleBulkMove, handleGlobalSearch } = useFileOperations(activeFolderId, selectedIds, setSelectedIds, displayFilesRaw);

  const handleBulkShare = useCallback(async () => {
    const shareFiles = displayFilesRaw.filter(f => selectedIds.includes(f.id) && f.type !== 'folder');
    if (shareFiles.length === 0) {
      toast.info('No shareable files selected (folders cannot be shared)');
      return;
    }
    const toastId = toast.loading(`Generating ${shareFiles.length} links...`);
    try {
      const results = await Promise.all(
        shareFiles.map(async (file) => {
          try {
            const info = await invoke<any>('cmd_create_share', {
              folderId: null,
              messageId: file.id,
              fileName: file.name,
              fileSize: file.size,
              password: null,
              expiryHours: 24,
              shareHost: settings.shareHost,
            });
            return { file, link: info.link };
          } catch (e) {
            return null;
          }
        })
      );
      const valid = results.filter(r => r !== null);
      if (valid.length > 0) {
        const text = valid.map(v => `${v!.file.name}: ${v!.link}`).join('\n');
        await nativeShareOrCopy('Shared Files', '', text);
        toast.success(`Copied ${valid.length} links!`, { id: toastId });
      } else {
        toast.error('Failed to generate links', { id: toastId });
      }
    } catch (e) {
      toast.error('Share failed', { id: toastId });
    }
  }, [displayFilesRaw, selectedIds, settings.shareHost]);

  useEffect(() => {
      if (searchTerm.length <= 2) {
          setSearchResults([]);
          return;
      }

      const timer = setTimeout(async () => {
          setIsSearching(true);
          const results = await handleGlobalSearch(searchTerm);
          setSearchResults(results);
          setIsSearching(false);
      }, 500);

      return () => clearTimeout(timer);
  }, [searchTerm, handleGlobalSearch]);

  const { data: bandwidth } = useQuery({
    queryKey: ['bandwidth'],
    queryFn: () => invoke<BandwidthStats>('cmd_get_bandwidth'),
    refetchInterval: 5000,
  });

  const activeFolder = activeFolderId === null
    ? 'Saved Messages'
    : folders.find(f => f.id === activeFolderId)?.name || 'Unknown Channel';

  // Folder action menu state (replaces swipe-to-reveal)
  const [folderActionMenu, setFolderActionMenu] = useState<TelegramFolder | null>(null);
  const [showUploadMenu, setShowUploadMenu] = useState(false);

  const buildFolderActions = useCallback((folder: TelegramFolder): ActionItem[] => [
    {
      label: folder.is_public ? 'Make Private' : 'Make Public',
      icon: <Globe className="w-4 h-4" />,
      onClick: () => handleFolderToggleVisibility?.(folder.id, !folder.is_public),
    },
    {
      label: 'Copy Invite Link',
      icon: <Share2 className="w-4 h-4" />,
      onClick: async () => {
        try {
          const info = await handleExportFolderInvite?.(folder.id);
          if (info && info.link) {
            await nativeShareOrCopy(folder.name, 'Folder Invite', info.link);
          }
        } catch (e) {
          toast.error('Failed to get invite link');
        }
      },
    },
    {
      label: 'Rename',
      icon: <Pencil className="w-4 h-4" />,
      onClick: () => handleFolderRename(folder.id, folder.name),
    },
    {
      label: 'Delete',
      icon: <Trash2 className="w-4 h-4" />,
      onClick: () => handleFolderDelete(folder.id, folder.name),
      destructive: true,
    },
  ], [handleFolderRename, handleFolderDelete]);

  const handleSelectAll = useCallback(() => {
    hapticLight();
    if (selectedIds.length === allFiles.length) {
      setSelectedIds([]);
    } else {
      setSelectedIds(allFiles.map(f => f.id));
    }
  }, [selectedIds.length, allFiles]);

  const handleClearSelection = useCallback(() => {
    hapticLight();
    setSelectedIds([]);
  }, []);

  const handleToggleSelection = useCallback((id: number) => {
    hapticLight();
    setSelectedIds(prev => prev.includes(id) ? prev.filter(i => i !== id) : [...prev, id]);
  }, []);

  const handleDownload = useCallback((file: TelegramFile) => {
    hapticLight();
    queueDownload(file.id, file.name, activeFolderId);
    setActiveTab('downloads');
    toast.success(`Queued "${file.name}" for download`);
  }, [queueDownload, activeFolderId]);

  const handleDeleteFile = useCallback((file: TelegramFile) => {
    hapticMedium();
    handleDeleteOp(file.id);
  }, [handleDeleteOp]);

  const handlePreview = useCallback((file: TelegramFile) => {
    if (isMediaFile(file.name)) {
      setPlayingFile(file);
    } else if (isPdfFile(file.name)) {
      setPdfFile(file);
    } else if (isImageFile(file.name)) {
      setPreviewFile(file);
    } else {
      toast.info(`Preview not supported for ${file.name}`);
    }
  }, []);

  // Tier 1.1: Replace window.prompt() with PromptModal
  const handleRenameFile = useCallback((file: TelegramFile) => {
    const currentName = fileRenames.get(file.id) || file.name;
    setRenamePrompt({ file, currentName });
  }, [fileRenames]);

  const handleConfirmRename = useCallback((newName: string) => {
    if (!renamePrompt) return;
    const { file } = renamePrompt;
    setFileRenames(prev => {
      const next = new Map(prev);
      next.set(file.id, newName);
      return next;
    });
    toast.success(`Renamed to "${newName}"`);
    setRenamePrompt(null);
  }, [renamePrompt]);

  const displayFiles = useMemo(() => {
    if (fileRenames.size === 0) return displayFilesRaw;
    return displayFilesRaw.map(f =>
      fileRenames.has(f.id) ? { ...f, name: fileRenames.get(f.id)! } : f
    );
  }, [displayFilesRaw, fileRenames]);

  const handleShareFile = useCallback(async (file: TelegramFile) => {
    try {
      const info = await invoke<any>('cmd_create_share', {
        folderId: null,
        messageId: file.id,
        fileName: file.name,
        fileSize: file.size,
        password: null,
        expiryHours: 24,
        shareHost: settings.shareHost,
      });
      nativeShareOrCopy(file.name, file.sizeStr, info.link);
    } catch (e) {
      toast.error(`Failed to share ${file.name}: ${e}`);
    }
  }, [settings.shareHost]);

  // Tier 1.1: Replace prompt() for folder creation
  const handleConfirmCreateFolder = useCallback(async (name: string) => {
    setCreateFolderPrompt(false);
    await handleCreateFolder(name);
  }, [handleCreateFolder]);

  // Tier 2.2: Pull-to-refresh
  const handleTouchStart = useCallback((e: React.TouchEvent) => {
    if (activeTab !== 'files') return;
    const target = e.currentTarget;
    if (target.scrollTop > 0) {
      pullStartY.current = null;
      return;
    }
    pullStartY.current = e.touches[0].clientY;
  }, [activeTab]);

  const handleTouchMove = useCallback((e: React.TouchEvent) => {
    if (pullStartY.current === null || activeTab !== 'files' || isPullRefreshing) return;
    const dy = e.touches[0].clientY - pullStartY.current;
    if (dy > 0 && e.currentTarget.scrollTop === 0) {
      setPullDistance(Math.min(dy * 0.4, 120));
    }
  }, [activeTab, isPullRefreshing]);

  const handleTouchEnd = useCallback(async () => {
    if (pullStartY.current === null) return;
    if (pullDistance >= PULL_THRESHOLD) {
      setIsPullRefreshing(true);
      hapticMedium();
      try {
        await refetch();
        await handleSyncFolders(true);
        toast.success('Refreshed');
      } catch (e) {
        toast.error('Refresh failed');
      }
      setIsPullRefreshing(false);
    }
    setPullDistance(0);
    pullStartY.current = null;
  }, [pullDistance, refetch, handleSyncFolders]);

  // Tier 5.2: Recent files — most recently completed uploads/downloads
  const recentFiles = useMemo(() => {
    type RecentItem = { name: string; ts: number; type: 'upload' | 'download' };
    const items: RecentItem[] = [];
    uploadQueue.forEach((i: QueueItem) => {
      if (i.status === 'success') {
        const name = i.path.split('/').pop() || i.path;
        items.push({ name, ts: i.totalBytes || 0, type: 'upload' });
      }
    });
    downloadQueue.forEach((i: DownloadItem) => {
      if (i.status === 'success') {
        items.push({ name: i.filename, ts: i.totalBytes || 0, type: 'download' });
      }
    });
    return items.slice(0, 5);
  }, [uploadQueue, downloadQueue]);

  return (
    <div className="absolute inset-0 flex flex-col bg-telegram-bg text-telegram-text overflow-hidden select-none font-sans">
      {/* Premium Gradient Top Header */}
      <header className="flex flex-col gap-2.5 px-4 pb-3 pt-[calc(1rem+env(safe-area-inset-top,24px))] bg-gradient-to-r from-telegram-hover/40 to-telegram-bg border-b border-telegram-border/60 shadow-lg backdrop-blur-md sticky top-0 z-40">
        {selectedIds.length > 0 ? (
          <BulkActionBar
            selectedCount={selectedIds.length}
            totalCount={allFiles.length}
            onClearSelection={handleClearSelection}
            onSelectAll={handleSelectAll}
            onDownload={handleBulkDownload}
            onMove={() => setShowMovePicker(true)}
            onShare={handleBulkShare}
            onDelete={handleBulkDelete}
          />
        ) : (
          <div className="flex items-center justify-between w-full h-8">
            <div className="flex items-center gap-3">
              <img src="/logo.svg" className="w-8 h-8 drop-shadow-lg" alt="Logo" />
              <div>
                <h1 className={`text-base font-bold tracking-tight ${theme === 'light' ? 'text-[#1c1c1e]' : 'bg-gradient-to-r from-white to-telegram-subtext bg-clip-text text-transparent'}`}>Telegram Drive</h1>
              </div>
            </div>
            <div className="flex items-center gap-2">
              <button
                onClick={() => { hapticLight(); setIsSidebarOpen(true); }}
                className="p-1.5 rounded-xl bg-telegram-hover/30 hover:bg-telegram-hover/60 border border-telegram-border/40 text-telegram-subtext transition-all duration-300"
                aria-label="Open menu"
              >
                <Menu className="w-5 h-5" />
              </button>
            </div>
          </div>
        )}

        {/* Search + Sort row (Tier 1.2 clear button, Tier 3.4 sort popover, Tier 4.2 bandwidth line) */}
        <div className="w-full flex items-center gap-2">
          <div className="relative flex-1">
            <Search className="w-4 h-4 absolute left-3 top-1/2 -translate-y-1/2 text-telegram-subtext pointer-events-none" />
            <input
              type="text"
              placeholder="Search files..."
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              className="w-full bg-telegram-hover/30 border border-telegram-border/40 rounded-xl pl-9 pr-9 py-2 text-xs focus:outline-none focus:border-telegram-primary/50 transition-colors text-telegram-text placeholder:text-telegram-subtext/60"
            />
            {searchTerm.length > 0 && !isSearching && (
              <button
                onClick={() => { hapticLight(); setSearchTerm(''); }}
                className="absolute right-2.5 top-1/2 -translate-y-1/2 p-1 rounded-full hover:bg-telegram-hover/60 text-telegram-subtext"
                aria-label="Clear search"
              >
                <X className="w-3.5 h-3.5" />
              </button>
            )}
            {isSearching && <div className="absolute right-3 top-1/2 -translate-y-1/2 w-3 h-3 border-2 border-telegram-primary border-t-transparent rounded-full animate-spin" />}
          </div>

          {/* Tier 3.4: Compact sort popover trigger */}
          <div className="relative">
            <button
              onClick={() => { hapticLight(); setShowSortMenu(v => !v); }}
              className="flex items-center gap-1 px-2.5 py-2 rounded-xl bg-telegram-hover/30 border border-telegram-border/40 text-telegram-subtext hover:text-telegram-text transition-colors"
              aria-label="Sort"
            >
              <ArrowUpDown className="w-3.5 h-3.5" />
              <span className="text-[10px] font-semibold uppercase">{sortField[0]}</span>
            </button>
            <AnimatePresence>
              {showSortMenu && (
                <motion.div
                  initial={{ opacity: 0, y: -5 }}
                  animate={{ opacity: 1, y: 0 }}
                  exit={{ opacity: 0, y: -5 }}
                  className="absolute right-0 top-full mt-1 z-50 w-44 bg-[#1c1c1c] border border-white/10 rounded-xl shadow-2xl p-1.5"
                >
                  {(['name', 'size', 'date'] as const).map(field => (
                    <button
                      key={field}
                      onClick={() => {
                        hapticLight();
                        if (sortField === field) setSortDirection(d => d === 'asc' ? 'desc' : 'asc');
                        else { setSortField(field); setSortDirection('asc'); }
                        setShowSortMenu(false);
                      }}
                      className={`w-full flex items-center justify-between px-3 py-2 rounded-lg text-xs font-medium transition-colors ${
                        sortField === field ? 'bg-telegram-primary/15 text-telegram-primary' : 'text-telegram-text hover:bg-white/5'
                      }`}
                    >
                      <span className="capitalize">By {field}</span>
                      {sortField === field && (sortDirection === 'asc' ? <ArrowUp className="w-3.5 h-3.5" /> : <ArrowDown className="w-3.5 h-3.5" />)}
                    </button>
                  ))}
                </motion.div>
              )}
            </AnimatePresence>
          </div>

          <button
            onClick={() => { hapticLight(); handleSyncFolders(); }}
            disabled={isSyncing}
            className="p-2 rounded-xl bg-telegram-hover/30 border border-telegram-border/40 text-telegram-subtext hover:text-telegram-text transition-colors disabled:opacity-50"
            aria-label="Sync"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isSyncing ? 'animate-spin' : ''}`} />
          </button>
        </div>

        {/* Tier 4.2: Live bandwidth line + Tier 5.1 file-type filter chips */}
        {activeTab === 'files' && (
          <div className="flex items-center justify-between gap-2 px-0.5">
            <div className="flex items-center gap-1 overflow-x-auto no-scrollbar">
              {(['all', 'images', 'videos', 'audio', 'docs'] as FileTypeFilter[]).map(filter => (
                <button
                  key={filter}
                  onClick={() => { hapticLight(); setFileTypeFilter(filter); }}
                  className={`flex-shrink-0 px-2.5 py-1 rounded-lg text-[10px] font-semibold uppercase tracking-wide transition-colors ${
                    fileTypeFilter === filter
                      ? 'bg-telegram-primary/20 text-telegram-primary border border-telegram-primary/30'
                      : 'bg-telegram-hover/30 text-telegram-subtext border border-telegram-border/30'
                  }`}
                >
                  {filter}
                </button>
              ))}
            </div>
            {bandwidth && (
              <div className="flex items-center gap-2 text-[10px] font-mono flex-shrink-0">
                <span className="text-green-500">↓ {formatBytes(bandwidth.down_bytes)}/s</span>
                <span className="text-blue-500">↑ {formatBytes(bandwidth.up_bytes)}/s</span>
              </div>
            )}
          </div>
        )}
      </header>

      {/* Main Viewport Container */}
      <main
        id="mobile-scroll-container"
        className="flex-1 overflow-y-auto px-4 py-3 space-y-4 pb-40 scroll-smooth"
        onTouchStart={handleTouchStart}
        onTouchMove={handleTouchMove}
        onTouchEnd={handleTouchEnd}
        onScroll={(e) => {
          if (!hasNextPage || isFetchingNextPage || !fetchNextPage) return;
          const { scrollTop, scrollHeight, clientHeight } = e.currentTarget;
          if (scrollHeight - scrollTop - clientHeight < 400) {
            fetchNextPage();
          }
        }}
      >
        {/* Tier 2.2: Pull-to-refresh indicator */}
        {pullDistance > 0 && (
          <div
            className="flex items-center justify-center transition-opacity"
            style={{ height: `${pullDistance}px` }}
          >
            <RefreshCw className={`w-5 h-5 text-telegram-primary ${pullDistance >= PULL_THRESHOLD || isPullRefreshing ? 'animate-spin' : ''}`} />
          </div>
        )}

        <AnimatePresence mode="wait">
        {activeTab === 'files' && (
          <motion.div key="files" initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -15 }} transition={{ duration: 0.25, ease: 'easeOut' }} className="space-y-4">
            {/* Folder Header Breadcrumb */}
            <div className="flex items-center justify-between bg-telegram-hover/20 p-3 rounded-2xl border border-telegram-border/30">
              <div className="flex items-center gap-2.5 min-w-0">
                <Folder className="w-5 h-5 text-telegram-primary flex-shrink-0" />
                <span className="text-sm font-semibold truncate max-w-[150px]">{activeFolder}</span>
              </div>
              <div className="flex items-center gap-2 flex-shrink-0">
                <button
                  onClick={() => { hapticLight(); setShowUploadMenu(true); }}
                  className="flex items-center gap-1.5 px-3 py-1.5 rounded-xl text-xs font-semibold bg-telegram-primary text-black hover:bg-telegram-primary/95 border border-telegram-primary/10 active:scale-95 transition-all duration-200"
                >
                  <UploadCloud className="w-3.5 h-3.5" />
                  Upload
                </button>
              </div>
            </div>

            {/* Tier 5.2: Recent files (only shown when there are recent transfers) */}
            {recentFiles.length > 0 && (
              <div className="p-3 rounded-2xl bg-telegram-hover/15 border border-telegram-border/20">
                <div className="flex items-center justify-between mb-2">
                  <span className="text-[10px] font-bold text-telegram-subtext uppercase tracking-wider">Recent</span>
                  <Clock className="w-3 h-3 text-telegram-subtext" />
                </div>
                <div className="flex flex-wrap gap-1.5">
                  {recentFiles.map((item, i) => (
                    <div
                      key={i}
                      className="flex items-center gap-1 px-2 py-1 rounded-lg bg-telegram-bg/60 border border-telegram-border/30 text-[10px] font-medium text-telegram-text max-w-[160px]"
                    >
                      <CheckCircle2 className="w-3 h-3 text-green-500 flex-shrink-0" />
                      <span className="truncate">{item.name}</span>
                      <span className="text-telegram-subtext uppercase">{item.type === 'upload' ? '↑' : '↓'}</span>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Dynamic Real File List */}
            <TouchFileList
              files={displayFiles}
              isLoading={isLoading}
              fetchNextPage={fetchNextPage}
              hasNextPage={hasNextPage}
              isFetchingNextPage={isFetchingNextPage}
              onDownload={handleDownload}
              onDelete={handleDeleteFile}
              onPreview={handlePreview}
              onRename={handleRenameFile}
              onShare={handleShareFile}
              selectedIds={selectedIds}
              onToggleSelection={handleToggleSelection}
              onSelectAll={handleSelectAll}
              onClearSelection={handleClearSelection}
            />
          </motion.div>
        )}

        {activeTab === 'downloads' && (
          <motion.div key="downloads" initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -15 }} transition={{ duration: 0.25, ease: 'easeOut' }} className="space-y-4">
            <div className="flex items-center justify-between">
              <h2 className="text-base font-bold flex items-center gap-2">
                <Download className="w-4 h-4 text-telegram-primary" />
                Transfers
              </h2>
              {(uploadQueue.length > 0 || downloadQueue.length > 0) && (
                <button
                  onClick={() => {
                    hapticLight();
                    cancelAllUploads();
                    cancelAllDownloads();
                  }}
                  className="text-[10px] font-semibold text-red-400 hover:text-red-300 transition-colors"
                >
                  Cancel all
                </button>
              )}
            </div>

            {/* Uploads section */}
            {uploadQueue.length > 0 && (
              <div className="p-3 rounded-2xl bg-telegram-hover/15 border border-telegram-border/20 space-y-2">
                <div className="flex items-center gap-1.5 text-[10px] font-bold text-telegram-subtext uppercase tracking-wider">
                  <UploadCloud className="w-3 h-3" />
                  Uploads ({uploadQueue.filter((i: QueueItem) => i.status === 'uploading' || i.status === 'pending').length} active)
                </div>
                {uploadQueue.map((item: QueueItem) => (
                  <TransferRow
                    key={item.id}
                    name={item.path.split('/').pop() || item.path}
                    status={item.status}
                    progress={item.progress ?? 0}
                    speed={item.speedBytesPerSec}
                    onCancel={() => cancelUpload(item.id)}
                    onRetry={() => retryUpload(item.id)}
                    onRemove={() => {/* removal handled by clearing finished */}}
                  />
                ))}
              </div>
            )}

            {/* Downloads section */}
            {downloadQueue.length > 0 && (
              <div className="p-3 rounded-2xl bg-telegram-hover/15 border border-telegram-border/20 space-y-2">
                <div className="flex items-center gap-1.5 text-[10px] font-bold text-telegram-subtext uppercase tracking-wider">
                  <Download className="w-3 h-3" />
                  Downloads ({downloadQueue.filter((i: DownloadItem) => i.status === 'downloading' || i.status === 'pending').length} active)
                </div>
                {downloadQueue.map((item: DownloadItem) => (
                  <TransferRow
                    key={item.id}
                    name={item.filename}
                    status={item.status}
                    progress={item.progress ?? 0}
                    speed={item.speedBytesPerSec}
                    onCancel={() => cancelDownload(item.id)}
                    onRetry={() => retryDownload(item.id)}
                    onRemove={() => {/* removal handled by clearing finished */}}
                  />
                ))}
              </div>
            )}

            {uploadQueue.length === 0 && downloadQueue.length === 0 && (
              <div className="flex flex-col items-center justify-center h-[50vh] space-y-3 text-center px-6">
                <div className="p-4 rounded-full bg-telegram-primary/10 text-telegram-primary border border-telegram-primary/20">
                  <Download className="w-8 h-8" />
                </div>
                <h3 className="text-base font-bold">No active transfers</h3>
                <p className="text-xs text-telegram-subtext max-w-xs leading-relaxed">
                  Uploads and downloads will appear here. They're managed in the background.
                </p>
              </div>
            )}
          </motion.div>
        )}

        {activeTab === 'settings' && (
          <motion.div key="settings" initial={{ opacity: 0, y: 15 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -15 }} transition={{ duration: 0.25, ease: 'easeOut' }} className="space-y-4">
            <MobileSettings onLogout={handleLogout} appVersion={appVersion} />
          </motion.div>
        )}
        </AnimatePresence>
      </main>

      {/* Tier 2.1: Floating Action Button (FAB) — primary upload action, kept alongside header button */}
      {activeTab === 'files' && selectedIds.length === 0 && (
        <button
          onClick={() => { hapticMedium(); setShowUploadMenu(true); }}
          className="fixed right-5 z-30 w-14 h-14 rounded-full bg-telegram-primary text-black shadow-2xl shadow-telegram-primary/40 hover:scale-105 active:scale-95 transition-transform duration-200 flex items-center justify-center"
          style={{ bottom: isAndroid ? '170px' : '110px' }}
          aria-label="Quick upload"
        >
          <Plus className="w-7 h-7" strokeWidth={2.5} />
        </button>
      )}

      {/* Slide-out Sidebar Drawer Overlay (Tier 3.2: MobileSidebar) */}
      {isSidebarOpen && (
        <div
          className="fixed inset-0 bg-black/60 z-[100] backdrop-blur-sm transition-opacity duration-300"
          onClick={() => setIsSidebarOpen(false)}
        />
      )}

      <MobileSidebar
        isOpen={isSidebarOpen}
        folders={folders}
        activeFolderId={activeFolderId}
        onSelectFolder={(id) => { setActiveFolderId(id); }}
        onClose={() => setIsSidebarOpen(false)}
        onCreateFolder={() => setCreateFolderPrompt(true)}
        onFolderActions={(folder) => setFolderActionMenu(folder)}
        onFolderLongPress={(folder) => setFolderActionMenu(folder)}
        isConnected={isConnected}
        bandwidth={bandwidth}
      >
        {/* Tier 2.4: ThemeToggle moved from top header to sidebar footer */}
        <div className="pt-2 border-t border-telegram-border/30">
          <div className="flex items-center justify-between text-xs text-telegram-subtext">
            <span>Theme</span>
            <ThemeToggle />
          </div>
        </div>
      </MobileSidebar>

      {/* Folder action popover (replaces swipe-to-reveal) */}
      {folderActionMenu && (
        <ActionPopover
          title={folderActionMenu.name}
          actions={buildFolderActions(folderActionMenu)}
          onClose={() => setFolderActionMenu(null)}
        />
      )}

      {/* Upload Action Sheet */}
      {showUploadMenu && (
        <ActionPopover
          title="Upload Options"
          actions={[
            {
              label: 'Upload Files',
              icon: <FilePlus className="w-4 h-4" />,
              onClick: () => {
                setShowUploadMenu(false);
                handleManualUpload();
              }
            },
            {
              label: 'Upload Folder',
              icon: <FolderUp className="w-4 h-4" />,
              onClick: () => {
                setShowUploadMenu(false);
                handleFolderUpload();
              }
            }
          ]}
          onClose={() => setShowUploadMenu(false)}
        />
      )}

      {/* Move-to-folder picker modal */}
      {showMovePicker && (
        <div
          className="fixed inset-0 z-[150] flex items-center justify-center bg-black/50 backdrop-blur-sm"
          onClick={() => setShowMovePicker(false)}
        >
          <div
            className="bg-[#1c1c1c] border border-white/10 rounded-2xl p-5 w-[300px] max-h-[60vh] flex flex-col shadow-2xl"
            onClick={e => e.stopPropagation()}
          >
            <div className="flex items-center justify-between mb-4">
              <h3 className="text-sm font-bold text-white">Move {selectedIds.length} file{selectedIds.length !== 1 ? 's' : ''} to...</h3>
              <button
                onClick={() => setShowMovePicker(false)}
                className="p-1.5 rounded-lg hover:bg-white/10 text-telegram-subtext"
              >
                <X className="w-4 h-4" />
              </button>
            </div>
            <div className="flex-1 overflow-y-auto space-y-1 min-h-0">
              <button
                onClick={() => { handleBulkMove(null); setShowMovePicker(false); }}
                className={`w-full text-left px-3.5 py-2.5 rounded-xl text-xs font-semibold transition-all duration-200 ${
                  activeFolderId === null
                    ? 'bg-telegram-primary/10 text-telegram-primary'
                    : 'text-telegram-subtext hover:bg-white/5'
                }`}
              >
                📁 Saved Messages
              </button>
              {folders
                .filter(f => f.id !== activeFolderId)
                .map(folder => (
                  <button
                    key={folder.id}
                    onClick={() => { handleBulkMove(folder.id); setShowMovePicker(false); }}
                    className="w-full text-left px-3.5 py-2.5 rounded-xl text-xs font-semibold text-telegram-subtext hover:bg-white/5 transition-all duration-200"
                  >
                    📁 {folder.name}
                  </button>
                ))}
              {folders.filter(f => f.id !== activeFolderId).length === 0 && (
                <p className="text-xs text-telegram-subtext/60 text-center py-4">No other folders available</p>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Tier 1.1: PromptModal for rename — replaces window.prompt() */}
      <PromptModal
        isOpen={renamePrompt !== null}
        title="Rename file"
        placeholder="New name"
        defaultValue={renamePrompt?.currentName ?? ''}
        confirmText="Rename"
        onConfirm={handleConfirmRename}
        onCancel={() => setRenamePrompt(null)}
      />

      {/* Tier 1.1: PromptModal for create folder — replaces window.prompt() */}
      <PromptModal
        isOpen={createFolderPrompt}
        title="Create new folder"
        placeholder="Folder name"
        confirmText="Create"
        onConfirm={handleConfirmCreateFolder}
        onCancel={() => setCreateFolderPrompt(false)}
      />

      {/* Floating Bottom Nav Bar */}
      <BottomNavBar activeTab={activeTab} setActiveTab={setActiveTab} isAndroid={isAndroid} />

      {/* Adsterra Banner (Android only) — z-[60] keeps it above the BottomNavBar (z-50).
           Positioned at bottom-[144px] to sit cleanly above the nav bar (~60px tall, at bottom-20=80px). */}
      <div className="fixed bottom-[144px] left-0 right-0 z-[60]">
        <AdsterraBanner visible={adVisible} />
      </div>

      {/* Previews Overlays (Media, PDF & Images) */}
      {playingFile && (
        <div className="fixed inset-0 z-[100] bg-black/90">
          <MediaPlayer
            file={playingFile}
            onClose={() => setPlayingFile(null)}
            activeFolderId={activeFolderId}
          />
        </div>
      )}
      {pdfFile && (
        <div className="fixed inset-0 z-[100] bg-telegram-bg">
          <PdfViewer
            file={pdfFile}
            onClose={() => setPdfFile(null)}
            activeFolderId={activeFolderId}
          />
        </div>
      )}
      {previewFile && (
        <PreviewModal
          file={previewFile}
          activeFolderId={activeFolderId}
          onClose={() => setPreviewFile(null)}
        />
      )}
    </div>
  );
}

// ── TransferRow: Reusable transfer card (used in Transfers tab) ──────────
function TransferRow({
  name,
  status,
  progress,
  speed,
  onCancel,
  onRetry,
  onRemove,
}: {
  name: string;
  status: 'pending' | 'uploading' | 'downloading' | 'success' | 'error' | 'cancelled';
  progress: number;
  speed?: number;
  onCancel: () => void;
  onRetry: () => void;
  onRemove: () => void;
}) {
  const isActive = status === 'uploading' || status === 'downloading' || status === 'pending';
  const isError = status === 'error' || status === 'cancelled';
  const isDone = status === 'success';

  return (
    <div className="p-2.5 rounded-xl bg-telegram-bg/50 border border-telegram-border/20">
      <div className="flex items-center justify-between gap-2 mb-1.5">
        <div className="flex items-center gap-1.5 min-w-0 flex-1">
          {isDone && <CheckCircle2 className="w-3.5 h-3.5 text-green-500 flex-shrink-0" />}
          {isError && <AlertCircle className="w-3.5 h-3.5 text-red-500 flex-shrink-0" />}
          {isActive && status === 'pending' && <Clock className="w-3.5 h-3.5 text-telegram-subtext flex-shrink-0" />}
          {isActive && (status === 'uploading' || status === 'downloading') && (
            <RefreshCw className="w-3.5 h-3.5 text-telegram-primary animate-spin flex-shrink-0" />
          )}
          <span className="text-xs font-medium truncate">{name}</span>
        </div>
        <div className="flex items-center gap-1.5 flex-shrink-0">
          {isActive && status !== 'pending' && (
            <span className="text-[10px] font-mono text-telegram-subtext">
              {speed ? `${formatBytes(speed)}/s` : ''}
            </span>
          )}
          {isError && (
            <button
              onClick={onRetry}
              className="p-1 rounded-md hover:bg-telegram-hover text-telegram-primary"
              aria-label="Retry"
            >
              <RotateCcw className="w-3.5 h-3.5" />
            </button>
          )}
          {(isActive || isError) && (
            <button
              onClick={isError ? onRemove : onCancel}
              className="p-1 rounded-md hover:bg-red-500/20 text-red-400"
              aria-label="Cancel"
            >
              <X className="w-3.5 h-3.5" />
            </button>
          )}
        </div>
      </div>
      <div className="h-1.5 bg-telegram-border/30 rounded-full overflow-hidden">
        <div
          className={`h-full transition-all duration-300 ${
            isError ? 'bg-red-500' : isDone ? 'bg-green-500' : 'bg-telegram-primary'
          }`}
          style={{ width: `${Math.min(100, Math.max(0, progress))}%` }}
        />
      </div>
      <div className="flex items-center justify-between mt-1">
        <span className="text-[10px] text-telegram-subtext capitalize">{status}</span>
        <span className="text-[10px] font-mono text-telegram-subtext">{Math.round(progress)}%</span>
      </div>
    </div>
  );
}
