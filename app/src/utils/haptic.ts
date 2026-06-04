// ── Haptic feedback helper ──────────────────────────────────────────────
// Mobile users expect subtle vibration on interactive feedback.
// Uses the Vibration API where available; silent no-op otherwise.
//
// 10ms = tap feedback (selection toggles, navigation)
// 30ms = destructive actions (delete, bulk delete)
// 50ms = success confirmations (upload complete, share copied)

export type HapticStrength = 'light' | 'medium' | 'strong';

const HAPTIC_DURATIONS: Record<HapticStrength, number> = {
  light: 10,
  medium: 30,
  strong: 50,
};

export function haptic(strength: HapticStrength = 'light'): void {
  // Feature-detect Vibration API. Available on Android WebView and most
  // mobile browsers; silently no-op on desktop.
  if (typeof navigator === 'undefined') return;
  const nav = navigator as Navigator & { vibrate?: (ms: number | number[]) => boolean };
  if (typeof nav.vibrate !== 'function') return;
  try {
    nav.vibrate(HAPTIC_DURATIONS[strength]);
  } catch {
    // Some environments throw if user hasn't interacted yet. Best-effort only.
  }
}

// Convenience shortcuts
export const hapticLight = () => haptic('light');
export const hapticMedium = () => haptic('medium');
export const hapticStrong = () => haptic('strong');
