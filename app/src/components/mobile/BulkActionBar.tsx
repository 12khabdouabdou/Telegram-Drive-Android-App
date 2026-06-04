import { ReactElement } from 'react';
import { Download, FolderUp, Share2, Trash2, X, CheckSquare, Square } from 'lucide-react';
import { hapticLight } from '../../utils/haptic';

interface BulkActionBarProps {
  selectedCount: number;
  totalCount: number;
  onClearSelection: () => void;
  onSelectAll: () => void;
  onDownload: () => void;
  onMove: () => void;
  onShare: () => void;
  onDelete: () => void;
}

export function BulkActionBar({
  selectedCount,
  totalCount,
  onClearSelection,
  onSelectAll,
  onDownload,
  onMove,
  onShare,
  onDelete,
}: BulkActionBarProps): ReactElement {
  const allSelected = selectedCount === totalCount && totalCount > 0;

  const handleClear = () => {
    hapticLight();
    onClearSelection();
  };

  const handleSelectAll = () => {
    hapticLight();
    if (allSelected) {
      onClearSelection();
    } else {
      onSelectAll();
    }
  };

  const handleDownload = () => {
    hapticLight();
    onDownload();
  };

  const handleMove = () => {
    hapticLight();
    onMove();
  };

  const handleShare = () => {
    hapticLight();
    onShare();
  };

  const handleDelete = () => {
    hapticLight();
    onDelete();
  };

  const btnClass = "flex items-center justify-center p-2 rounded-lg bg-telegram-hover/30 hover:bg-telegram-hover/50 border border-telegram-border/40 transition-colors";

  return (
    <div className="flex items-center justify-between w-full h-10 px-2 bg-telegram-bg">
      <div className="flex items-center gap-2">
        <button
          onClick={handleClear}
          className="flex items-center justify-center p-2 text-telegram-text hover:text-telegram-primary transition-colors"
          aria-label="Clear selection"
        >
          <X size={20} />
        </button>
        <span className="text-sm font-medium text-telegram-text">
          {selectedCount} selected
        </span>
        <button
          onClick={handleSelectAll}
          className="flex items-center justify-center p-2 text-telegram-text hover:text-telegram-primary transition-colors ml-1"
          aria-label={allSelected ? "Select none" : "Select all"}
        >
          {allSelected ? <Square size={20} /> : <CheckSquare size={20} />}
        </button>
      </div>

      <div className="flex items-center gap-2">
        <button
          onClick={handleDownload}
          className={`${btnClass} text-telegram-primary`}
          aria-label="Download"
        >
          <Download size={18} />
        </button>
        <button
          onClick={handleMove}
          className={`${btnClass} text-yellow-500`}
          aria-label="Move"
        >
          <FolderUp size={18} />
        </button>
        <button
          onClick={handleShare}
          className={`${btnClass} text-green-500`}
          aria-label="Share"
        >
          <Share2 size={18} />
        </button>
        <button
          onClick={handleDelete}
          className={`${btnClass} text-red-500`}
          aria-label="Delete"
        >
          <Trash2 size={18} />
        </button>
      </div>
    </div>
  );
}
