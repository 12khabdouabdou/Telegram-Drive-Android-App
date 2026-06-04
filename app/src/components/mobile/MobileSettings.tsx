import { useState } from 'react';
import { useSettings } from '../../context/SettingsContext';
import { usePlatform } from '../../hooks/usePlatform';
import { invoke } from '@tauri-apps/api/core';
import { open as openShell } from '@tauri-apps/plugin-shell';
import { open as openDialog } from '@tauri-apps/plugin-dialog';
import { toast } from 'sonner';
import { Cloud, Shield, Globe, ChevronDown, FolderUp, X, LogOut } from 'lucide-react';
import { hapticLight } from '../../utils/haptic';

interface MobileSettingsProps {
  onLogout: () => void;
  appVersion: string;
}

function SettingRow({ label, description, children, divider }: { 
  label: string; 
  description?: string; 
  children: React.ReactNode; 
  divider?: boolean 
}): React.ReactElement {
  return (
    <div className={`flex items-center justify-between py-2 ${divider ? 'border-b border-telegram-border/20' : ''}`}>
      <div>
        <p className="text-xs font-medium">{label}</p>
        {description && <p className="text-[10px] text-telegram-subtext">{description}</p>}
      </div>
      {children}
    </div>
  );
}

function Toggle({ checked, onChange }: { checked: boolean; onChange: () => void }): React.ReactElement {
  return (
    <button
      onClick={() => {
        hapticLight();
        onChange();
      }}
      className={`relative w-11 h-6 rounded-full transition-colors duration-200 flex-shrink-0 ${checked ? 'bg-telegram-primary' : 'bg-telegram-border'}`}
    >
      <span className={`absolute top-0.5 left-0.5 w-5 h-5 rounded-full bg-white shadow transition-transform duration-200 ${checked ? 'translate-x-5' : 'translate-x-0'}`} />
    </button>
  );
}

export function MobileSettings({ onLogout, appVersion }: MobileSettingsProps): React.ReactElement {
  const { settings, updateSetting } = useSettings();
  const { isAndroid } = usePlatform();
  const [showFolderInput, setShowFolderInput] = useState(false);
  const [newFolderInput, setNewFolderInput] = useState("");

  return (
    <div className="space-y-4">
      {/* Preferences */}
      <div className="p-4 rounded-2xl bg-telegram-hover/20 border border-telegram-border/30 space-y-2">
        <h3 className="text-sm font-bold text-telegram-primary tracking-wide uppercase text-[10px] flex items-center gap-1.5">
          Preferences
        </h3>
        
        <SettingRow label="Zip Folders Before Upload" description="Compress folders into .zip before uploading" divider>
          <Toggle checked={settings.zipFolders} onChange={() => updateSetting('zipFolders', !settings.zipFolders)} />
        </SettingRow>

        <SettingRow label="Concurrent Uploads" divider>
          <input
            type="number" min="1" max="10"
            value={settings.maxConcurrentUploads}
            onChange={e => updateSetting('maxConcurrentUploads', parseInt(e.target.value) || 1)}
            className="w-16 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>

        <SettingRow label="Concurrent Downloads" divider>
          <input
            type="number" min="1" max="10"
            value={settings.maxConcurrentDownloads}
            onChange={e => updateSetting('maxConcurrentDownloads', parseInt(e.target.value) || 1)}
            className="w-16 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>

        <SettingRow label="Chunk Size (KB)" divider>
          <div className="relative">
            <select
              value={settings.chunkSizeKb}
              onChange={e => updateSetting('chunkSizeKb', parseInt(e.target.value))}
              className="bg-telegram-bg border border-telegram-border rounded-lg pl-2 pr-7 py-1.5 text-xs appearance-none focus:outline-none focus:border-telegram-primary/50"
            >
              <option value={128}>128 KB</option>
              <option value={256}>256 KB</option>
              <option value={512}>512 KB</option>
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-telegram-subtext absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </SettingRow>

        <SettingRow label="Bandwidth Up (KB/s)" description="0 for unlimited" divider>
          <input
            type="number" min="0"
            value={settings.bandwidthLimitUpKBs}
            onChange={e => updateSetting('bandwidthLimitUpKBs', parseInt(e.target.value) || 0)}
            className="w-20 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>

        <SettingRow label="Bandwidth Down (KB/s)" description="0 for unlimited" divider>
          <input
            type="number" min="0"
            value={settings.bandwidthLimitDownKBs}
            onChange={e => updateSetting('bandwidthLimitDownKBs', parseInt(e.target.value) || 0)}
            className="w-20 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>

        <SettingRow label="VPN Mode" description="Optimize for VPNs" divider>
          <Toggle checked={settings.vpnMode} onChange={() => updateSetting('vpnMode', !settings.vpnMode)} />
        </SettingRow>

        <SettingRow label="Retry Attempts" divider>
          <input
            type="number" min="1" max="10"
            value={settings.retryAttempts}
            onChange={e => updateSetting('retryAttempts', parseInt(e.target.value) || 3)}
            className="w-16 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>

        <SettingRow label="Preferred DC" description="0 = auto" divider>
          <div className="relative">
            <select
              value={settings.preferredDC}
              onChange={e => updateSetting('preferredDC', e.target.value as any)}
              className="bg-telegram-bg border border-telegram-border rounded-lg pl-2 pr-7 py-1.5 text-xs appearance-none focus:outline-none focus:border-telegram-primary/50"
            >
              <option value="auto">Auto</option>
              <option value="dc1">DC1</option>
              <option value="dc2">DC2</option>
              <option value="dc3">DC3</option>
              <option value="dc4">DC4</option>
              <option value="dc5">DC5</option>
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-telegram-subtext absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </SettingRow>

        <SettingRow label="Timeout Multiplier" divider={false}>
          <input
            type="number" step="0.1" min="0.5" max="5.0"
            value={settings.timeoutMultiplier}
            onChange={e => updateSetting('timeoutMultiplier', parseFloat(e.target.value) || 1.0)}
            className="w-16 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-center focus:outline-none focus:border-telegram-primary/50"
          />
        </SettingRow>
      </div>

      {/* Auto-Backup Configuration */}
      <div className="p-4 rounded-2xl bg-telegram-hover/20 border border-telegram-border/30 space-y-2">
        <h3 className="text-sm font-bold text-telegram-primary tracking-wide uppercase text-[10px] flex items-center gap-1.5">
          <Cloud className="w-3 h-3" />
          Auto-Backup
        </h3>
        
        <SettingRow label="Enable Auto-Backup" description="Automatically backup photos and videos" divider={false}>
          <Toggle 
            checked={settings.autoBackupEnabled} 
            onChange={() => {
              const newEnabled = !settings.autoBackupEnabled;
              updateSetting('autoBackupEnabled', newEnabled);
              const config = {
                enabled: newEnabled,
                wifiOnly: settings.autoBackupWifiOnly,
                batterySafe: settings.autoBackupBatterySafe,
                nightMode: settings.autoBackupNightMode,
                destination: settings.autoBackupDestination,
                mode: settings.autoBackupMode,
                folders: settings.autoBackupFolders || []
              };
              invoke('cmd_toggle_auto_backup', { config })
                .then(() => toast.success(newEnabled ? "Auto-Backup enabled!" : "Auto-Backup disabled!"))
                .catch((e) => {
                  console.error(e);
                  toast.error(`Failed to toggle auto-backup: ${e}`);
                  updateSetting('autoBackupEnabled', !newEnabled);
                });
            }} 
          />
        </SettingRow>

        {settings.autoBackupEnabled && (
          <div className="space-y-4 pt-2 border-t border-telegram-border/30 mt-2">
            <div className="space-y-1">
              <label className="text-xs font-medium">Destination Folder</label>
              <input
                type="text"
                value={settings.autoBackupDestination}
                onChange={(e) => updateSetting('autoBackupDestination', e.target.value)}
                className="w-full bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-telegram-primary/50"
              />
            </div>

            <div className="space-y-1">
              <label className="text-xs font-medium">Backup Mode</label>
              <div className="relative">
                <select
                  value={settings.autoBackupMode}
                  onChange={(e) => updateSetting('autoBackupMode', e.target.value as any)}
                  className="w-full bg-telegram-bg border border-telegram-border rounded-lg pl-2 pr-7 py-1.5 text-xs appearance-none focus:outline-none focus:border-telegram-primary/50"
                >
                  <option value="new">Only backup new files</option>
                  <option value="all">Backup existing and new files</option>
                </select>
                <ChevronDown className="w-3.5 h-3.5 text-telegram-subtext absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
              </div>
            </div>

            <div className="space-y-2">
              <label className="text-xs font-medium">Folders to Monitor</label>
              <div className="flex flex-wrap gap-2">
                {(!settings.autoBackupFolders || settings.autoBackupFolders.length === 0) ? (
                  <div className="text-[10px] text-telegram-subtext px-2 py-1 bg-telegram-bg border border-telegram-border rounded-lg">
                    All folders (default)
                  </div>
                ) : (
                  settings.autoBackupFolders.map((folder, idx) => (
                    <div key={idx} className="flex items-center gap-1.5 px-2.5 py-1 bg-telegram-primary/10 text-telegram-primary border border-telegram-primary/20 rounded-lg text-[10px] font-medium">
                      <span className="truncate max-w-[120px]">{folder}</span>
                      <button
                        onClick={() => {
                          const newFolders = [...settings.autoBackupFolders];
                          newFolders.splice(idx, 1);
                          updateSetting('autoBackupFolders', newFolders);
                        }}
                        className="hover:bg-telegram-primary/20 rounded-full p-0.5 transition-colors"
                      >
                        <X className="w-3 h-3" />
                      </button>
                    </div>
                  ))
                )}
                {showFolderInput ? (
                  <div className="flex items-center gap-2 w-full mt-2">
                    <input 
                      type="text" 
                      autoFocus
                      placeholder="e.g. Camera, WhatsApp" 
                      value={newFolderInput}
                      onChange={(e) => setNewFolderInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' && newFolderInput.trim()) {
                          const newFolders = settings.autoBackupFolders ? [...settings.autoBackupFolders] : [];
                          if (!newFolders.includes(newFolderInput.trim())) {
                            newFolders.push(newFolderInput.trim());
                            updateSetting('autoBackupFolders', newFolders);
                          }
                          setNewFolderInput("");
                          setShowFolderInput(false);
                        }
                      }}
                      className="flex-1 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs focus:outline-none focus:border-telegram-primary/50"
                    />
                    <button 
                      onClick={() => {
                        if (newFolderInput.trim()) {
                          const newFolders = settings.autoBackupFolders ? [...settings.autoBackupFolders] : [];
                          if (!newFolders.includes(newFolderInput.trim())) {
                            newFolders.push(newFolderInput.trim());
                            updateSetting('autoBackupFolders', newFolders);
                          }
                        }
                        setNewFolderInput("");
                        setShowFolderInput(false);
                      }}
                      className="bg-telegram-primary text-black px-2 py-1 rounded-lg text-xs font-semibold"
                    >
                      Add
                    </button>
                    <button onClick={() => setShowFolderInput(false)} className="text-telegram-subtext hover:text-telegram-text p-1">
                      <X className="w-4 h-4" />
                    </button>
                  </div>
                ) : (
                  <button
                    onClick={async () => {
                      if (isAndroid) {
                        invoke('cmd_open_android_folder_picker').catch(console.error);
                        return;
                      }
                      try {
                        const selected = await openDialog({ directory: true, multiple: false });
                        if (selected) {
                          const folderPath = Array.isArray(selected) ? selected[0] : selected;
                          const folderName = folderPath.split(/[/\\]/).pop() || folderPath;
                          const newFolders = settings.autoBackupFolders ? [...settings.autoBackupFolders] : [];
                          if (!newFolders.includes(folderName)) {
                            newFolders.push(folderName);
                            updateSetting('autoBackupFolders', newFolders);
                          }
                        } else {
                          setShowFolderInput(true);
                        }
                      } catch (e) {
                        setShowFolderInput(true);
                      }
                    }}
                    className="flex items-center gap-1 px-2.5 py-1 bg-telegram-hover/30 text-telegram-text border border-telegram-border rounded-lg text-[10px] font-medium hover:bg-telegram-hover/50 transition-colors"
                  >
                    <FolderUp className="w-3 h-3" /> Add Folder
                  </button>
                )}
              </div>
              <p className="text-[10px] text-telegram-subtext mt-1">Select specific folders to monitor, or leave empty for all.</p>
            </div>

            <SettingRow label="Wi-Fi Only" description="Pause backup on cellular data" divider={false}>
              <Toggle checked={settings.autoBackupWifiOnly} onChange={() => updateSetting('autoBackupWifiOnly', !settings.autoBackupWifiOnly)} />
            </SettingRow>

            <SettingRow label="Pause on Low Battery" description="Stop when battery is under 15%" divider={false}>
              <Toggle checked={settings.autoBackupBatterySafe} onChange={() => updateSetting('autoBackupBatterySafe', !settings.autoBackupBatterySafe)} />
            </SettingRow>

            <SettingRow label="Night-Time Only" description="Only backup between 2 AM and 6 AM" divider={false}>
              <Toggle checked={settings.autoBackupNightMode} onChange={() => updateSetting('autoBackupNightMode', !settings.autoBackupNightMode)} />
            </SettingRow>
          </div>
        )}
      </div>

      {/* Proxy Configuration */}
      <div className="p-4 rounded-2xl bg-telegram-hover/20 border border-telegram-border/30 space-y-2">
        <h3 className="text-sm font-bold text-telegram-primary tracking-wide uppercase text-[10px] flex items-center gap-1.5">
          <Shield className="w-3 h-3" />
          Proxy
        </h3>

        <SettingRow label="Enable Proxy" description="Route traffic through a proxy server" divider>
          <Toggle checked={settings.proxyEnabled} onChange={() => updateSetting('proxyEnabled', !settings.proxyEnabled)} />
        </SettingRow>

        <SettingRow label="Proxy Type" description="SOCKS5 or MTProto" divider>
          <div className="relative">
            <select
              value={settings.proxyType}
              onChange={e => updateSetting('proxyType', e.target.value as 'socks5' | 'mtproto')}
              className="appearance-none bg-telegram-bg border border-telegram-border rounded-lg pl-2.5 pr-7 py-1.5 text-xs text-telegram-text focus:outline-none focus:border-telegram-primary/50 transition cursor-pointer"
            >
              <option value="socks5">SOCKS5</option>
              <option value="mtproto">MTProto</option>
            </select>
            <ChevronDown className="w-3.5 h-3.5 text-telegram-subtext absolute right-2 top-1/2 -translate-y-1/2 pointer-events-none" />
          </div>
        </SettingRow>

        <SettingRow label="Host" description="Proxy server address" divider>
          <input
            type="text"
            placeholder="127.0.0.1"
            value={settings.proxyHost}
            onChange={e => updateSetting('proxyHost', e.target.value)}
            className="w-32 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-telegram-text text-right focus:outline-none focus:border-telegram-primary/50 transition placeholder:text-telegram-subtext/40"
          />
        </SettingRow>

        <SettingRow label="Port" description="1–65535" divider={settings.proxyType === 'socks5' || settings.proxyType === 'mtproto'}>
          <input
            type="number"
            min="1"
            max="65535"
            value={settings.proxyPort}
            onChange={e => updateSetting('proxyPort', Math.max(1, Math.min(65535, parseInt(e.target.value) || 1080)))}
            className="w-20 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-telegram-text text-center focus:outline-none focus:border-telegram-primary/50 transition"
          />
        </SettingRow>

        {settings.proxyType === 'socks5' && (
          <>
            <SettingRow label="Username" description="Optional" divider>
              <input
                type="text"
                placeholder="Optional"
                value={settings.proxyUsername}
                onChange={e => updateSetting('proxyUsername', e.target.value)}
                className="w-32 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-telegram-text text-right focus:outline-none focus:border-telegram-primary/50 transition placeholder:text-telegram-subtext/40"
              />
            </SettingRow>
            <SettingRow label="Password" description="Optional" divider={false}>
              <input
                type="password"
                placeholder="Optional"
                value={settings.proxyPassword}
                onChange={e => updateSetting('proxyPassword', e.target.value)}
                className="w-32 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-telegram-text text-right focus:outline-none focus:border-telegram-primary/50 transition placeholder:text-telegram-subtext/40"
              />
            </SettingRow>
          </>
        )}

        {settings.proxyType === 'mtproto' && (
          <SettingRow label="Secret" description="MTProto proxy secret key" divider={false}>
            <input
              type="password"
              placeholder="Required"
              value={settings.proxySecret}
              onChange={e => updateSetting('proxySecret', e.target.value)}
              className="w-32 bg-telegram-bg border border-telegram-border rounded-lg px-2 py-1.5 text-xs text-telegram-text text-right focus:outline-none focus:border-telegram-primary/50 transition placeholder:text-telegram-subtext/40"
            />
          </SettingRow>
        )}

        {/* Info note */}
        <div className="p-2.5 rounded-lg bg-yellow-500/5 border border-yellow-500/10 mt-2">
          <p className="text-[10px] text-yellow-400/70 leading-relaxed">
            ⚠️ Proxy changes require reconnecting.
          </p>
        </div>
      </div>

      {/* About */}
      <div className="p-4 rounded-2xl bg-telegram-hover/20 border border-telegram-border/30 space-y-4">
        <h3 className="text-sm font-bold text-telegram-primary tracking-wide uppercase text-[10px]">About</h3>
        <div className="flex flex-col items-center py-3 space-y-4">
          <img src="/logo.svg" className="w-14 h-14 drop-shadow-lg" alt="Telegram Drive Logo" />
          <div className="text-center">
            <p className="text-sm font-bold text-telegram-text">Telegram Drive</p>
            <p className="text-[11px] text-telegram-subtext mt-0.5">v{appVersion}</p>
          </div>

          <div className="w-10 h-px bg-telegram-border" />

          <div className="text-center space-y-2.5">
            <p className="text-xs font-semibold text-telegram-text">Cameron Amer</p>

            <button
              onClick={(e) => { e.preventDefault(); openShell('https://www.cameronamer.com'); }}
              className="flex items-center justify-center gap-1.5 text-[11px] text-telegram-primary hover:text-telegram-primary/80 transition-colors cursor-pointer"
            >
              <Globe className="w-3 h-3" />
              www.cameronamer.com
            </button>

            <button
              onClick={(e) => { e.preventDefault(); openShell('https://github.com/caamer20/telegram-drive'); }}
              className="flex items-center justify-center gap-1.5 text-[11px] text-telegram-primary hover:text-telegram-primary/80 transition-colors cursor-pointer"
            >
              <svg className="w-3 h-3" viewBox="0 0 24 24" fill="currentColor">
                <path d="M12 0c-6.626 0-12 5.373-12 12 0 5.302 3.438 9.8 8.207 11.387.599.111.793-.261.793-.577v-2.234c-3.338.726-4.033-1.416-4.033-1.416-.546-1.387-1.333-1.756-1.333-1.756-1.089-.745.083-.729.083-.729 1.205.084 1.839 1.237 1.839 1.237 1.07 1.834 2.807 1.304 3.492.997.107-.775.418-1.305.762-1.604-2.665-.305-5.467-1.334-5.467-5.931 0-1.311.469-2.381 1.236-3.221-.124-.303-.535-1.524.117-3.176 0 0 1.008-.322 3.301 1.23.957-.266 1.983-.399 3.003-.404 1.02.005 2.047.138 3.006.404 2.291-1.552 3.297-1.23 3.297-1.23.653 1.653.242 2.874.118 3.176.77.84 1.235 1.911 1.235 3.221 0 4.609-2.807 5.624-5.479 5.921.43.372.823 1.102.823 2.222v3.293c0 .319.192.694.801.576 4.765-1.589 8.199-6.086 8.199-11.386 0-6.627-5.373-12-12-12z"/>
              </svg>
              github.com/caamer20/telegram-drive
            </button>
          </div>

          <p className="text-[10px] text-telegram-subtext/60 leading-relaxed text-center px-2">
            Turn your Telegram account into unlimited, secure cloud storage.
            Open-source and free forever.
          </p>
        </div>
      </div>

      <button onClick={onLogout} className="w-full flex items-center justify-center gap-2 py-3 rounded-2xl bg-red-500/10 hover:bg-red-500/20 text-red-400 border border-red-500/20 font-semibold text-xs active:scale-98 transition-all duration-200">
        <LogOut className="w-4 h-4" />
        Log Out
      </button>
    </div>
  );
}
