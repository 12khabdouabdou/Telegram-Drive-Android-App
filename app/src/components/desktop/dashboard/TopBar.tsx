import { useState, useRef, useEffect } from 'react';
import { HardDrive, LayoutGrid, Sun, Moon, Settings, Share2, Plus, FilePlus, FolderUp, ChevronDown } from 'lucide-react';
import { useTheme } from '../../../context/ThemeContext';

interface TopBarProps {
    currentFolderName: string;
    selectedIds: number[];
    onShowMoveModal: () => void;
    onBulkDownload: () => void;
    onBulkDelete: () => void;
    onBulkShare: () => void;
    onDownloadFolder: () => void;
    viewMode: 'grid' | 'list';
    setViewMode: (mode: 'grid' | 'list') => void;
    searchTerm: string;
    onSearchChange: (term: string) => void;
    onSettingsClick: () => void;
    onManualUpload?: () => void;
    onFolderUpload?: () => void;
}

export function TopBar({
    currentFolderName, selectedIds, onShowMoveModal, onBulkDownload, onBulkDelete, onBulkShare,
    onDownloadFolder, viewMode, setViewMode, searchTerm, onSearchChange, onSettingsClick,
    onManualUpload, onFolderUpload
}: TopBarProps) {
    const { theme, toggleTheme } = useTheme();
    const [showUploadMenu, setShowUploadMenu] = useState(false);
    const uploadMenuRef = useRef<HTMLDivElement>(null);

    useEffect(() => {
        function handleClickOutside(event: MouseEvent) {
            if (uploadMenuRef.current && !uploadMenuRef.current.contains(event.target as Node)) {
                setShowUploadMenu(false);
            }
        }
        document.addEventListener('mousedown', handleClickOutside);
        return () => document.removeEventListener('mousedown', handleClickOutside);
    }, []);

    return (
        <header className="h-14 border-b border-telegram-border flex items-center px-4 justify-between bg-telegram-surface/80 backdrop-blur-md sticky top-0 z-10" onClick={e => e.stopPropagation()}>
            <div className="flex items-center gap-4">
                <div className="flex items-center text-sm breadcrumbs text-telegram-subtext select-none">
                    <span className="hover:text-telegram-text cursor-pointer transition-colors">Start</span>
                    <span className="mx-2">/</span>
                    <span className="text-telegram-text font-medium">{currentFolderName}</span>
                </div>
            </div>

            <div className="flex-1 max-w-md mx-4">
                <input
                    type="text"
                    placeholder="Search files..."
                    className="w-full bg-telegram-hover border border-telegram-border rounded-lg px-3 py-1.5 text-sm text-telegram-text placeholder:text-telegram-subtext focus:outline-none focus:ring-2 focus:ring-telegram-primary/50 focus:border-transparent transition-all duration-300 shadow-sm"
                    value={searchTerm}
                    onChange={(e) => onSearchChange(e.target.value)}
                />
            </div>

            <div className="flex items-center gap-2">
                <div className="relative mr-2" ref={uploadMenuRef}>
                    <button 
                        onClick={() => setShowUploadMenu(!showUploadMenu)}
                        className="flex items-center gap-2 px-4 py-1.5 bg-telegram-primary text-white rounded-lg text-sm font-medium hover:bg-telegram-primary/90 transition-all shadow-sm active:scale-95"
                    >
                        <Plus className="w-4 h-4" />
                        Upload
                        <ChevronDown className="w-3.5 h-3.5 opacity-80" />
                    </button>
                    
                    {showUploadMenu && (
                        <div className="absolute top-full right-0 mt-2 w-48 bg-telegram-surface border border-telegram-border rounded-xl shadow-2xl py-1.5 z-50 animate-in fade-in slide-in-from-top-2">
                            <button 
                                onClick={() => { setShowUploadMenu(false); onManualUpload?.(); }}
                                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-telegram-text hover:bg-telegram-hover transition-colors"
                            >
                                <FilePlus className="w-4 h-4 text-telegram-primary" />
                                Upload Files...
                            </button>
                            <button 
                                onClick={() => { setShowUploadMenu(false); onFolderUpload?.(); }}
                                className="w-full flex items-center gap-3 px-4 py-2.5 text-sm text-telegram-text hover:bg-telegram-hover transition-colors"
                            >
                                <FolderUp className="w-4 h-4 text-telegram-primary" />
                                Upload Folder...
                            </button>
                        </div>
                    )}
                </div>

                {selectedIds.length > 0 && (
                    <div className="flex items-center gap-2 mr-4 animate-in fade-in slide-in-from-top-2">
                        <span className="text-xs text-telegram-subtext mr-2">{selectedIds.length} Selected</span>
                        <button onClick={onShowMoveModal} className="px-3 py-1.5 bg-telegram-primary/20 hover:bg-telegram-primary/30 text-telegram-primary rounded-md text-xs transition-all duration-200 font-medium hover:scale-105 active:scale-95">Move to...</button>
                        <button onClick={onBulkDownload} className="px-3 py-1.5 bg-telegram-hover hover:bg-telegram-border rounded-md text-xs text-telegram-text transition-all duration-200 hover:scale-105 active:scale-95">Download Selected</button>
                        <button onClick={onBulkShare} className="px-3 py-1.5 bg-telegram-primary/20 hover:bg-telegram-primary/30 text-telegram-primary rounded-md text-xs transition-all duration-200 font-medium hover:scale-105 active:scale-95 flex items-center gap-1"><Share2 className="w-3 h-3" />Share ({selectedIds.length})</button>
                        <button onClick={onBulkDelete} className="px-3 py-1.5 bg-red-500/10 hover:bg-red-500/20 text-red-400 rounded-md text-xs transition">Delete</button>
                    </div>
                )}

                <button onClick={onDownloadFolder} className="p-2 hover:bg-telegram-hover rounded-md text-telegram-subtext hover:text-telegram-text transition group relative" title="Download Folder">
                    <HardDrive className="w-5 h-5" />
                    <span className="absolute -bottom-8 left-1/2 -translate-x-1/2 text-[10px] bg-telegram-surface border border-telegram-border px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-lg">
                        Download All Files
                    </span>
                </button>

                <button
                    onClick={() => setViewMode(viewMode === 'grid' ? 'list' : 'grid')}
                    className="p-2 hover:bg-telegram-hover rounded-md text-telegram-subtext hover:text-telegram-text transition relative group"
                    title="Toggle Layout"
                >
                    <LayoutGrid className="w-5 h-5" />
                    <span className="absolute -bottom-8 left-1/2 -translate-x-1/2 text-[10px] bg-telegram-surface border border-telegram-border px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-lg">
                        {viewMode === 'grid' ? 'Switch to List' : 'Switch to Grid'}
                    </span>
                </button>

                <div className="w-px h-6 bg-telegram-border mx-1"></div>

                <button
                    onClick={onSettingsClick}
                    className="p-2 hover:bg-telegram-hover rounded-md text-telegram-subtext hover:text-telegram-text transition relative group"
                    title="Settings"
                >
                    <Settings className="w-5 h-5" />
                    <span className="absolute -bottom-8 left-1/2 -translate-x-1/2 text-[10px] bg-telegram-surface border border-telegram-border px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-lg">
                        Settings
                    </span>
                </button>

                <button
                    onClick={toggleTheme}
                    className="p-2 hover:bg-telegram-hover rounded-md text-telegram-subtext hover:text-telegram-text transition relative group"
                    title={theme === 'dark' ? 'Switch to Light Mode' : 'Switch to Dark Mode'}
                >
                    {theme === 'dark' ? <Sun className="w-5 h-5" /> : <Moon className="w-5 h-5" />}
                    <span className="absolute -bottom-8 left-1/2 -translate-x-1/2 text-[10px] bg-telegram-surface border border-telegram-border px-2 py-1 rounded opacity-0 group-hover:opacity-100 transition-opacity pointer-events-none whitespace-nowrap z-50 shadow-lg">
                        {theme === 'dark' ? 'Light Mode' : 'Dark Mode'}
                    </span>
                </button>
            </div>
        </header>
    )
}
