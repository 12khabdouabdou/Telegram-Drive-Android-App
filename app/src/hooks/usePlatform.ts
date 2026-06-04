import { useState } from 'react';
import { type } from '@tauri-apps/plugin-os';

const getInitialPlatform = () => {
  try {
    const osType = type();
    const isAndroid = osType === 'android';
    const isIos = osType === 'ios';
    const isMobile = isAndroid || isIos;

    return {
      isMobile,
      isDesktop: !isMobile,
      isAndroid,
    };
  } catch (e) {
    // Fallback for browser/development environments
    const ua = typeof navigator !== 'undefined' ? navigator.userAgent.toLowerCase() : '';
    const isAndroid = ua.includes('android');
    const isMobile = isAndroid || ua.includes('iphone') || ua.includes('ipad');

    return {
      isMobile,
      isDesktop: !isMobile,
      isAndroid,
    };
  }
};

export function usePlatform() {
  const [platformInfo] = useState(getInitialPlatform());
  return platformInfo;
}
