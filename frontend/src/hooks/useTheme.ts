import { useEffect, useCallback } from 'react';
import { api } from '../api/client';

type Theme = 'light' | 'dark' | 'system';

const STORAGE_KEY = 'coderhino-theme';

function safeGetItem(key: string): string | null {
  try {
    return localStorage.getItem(key);
  } catch {
    return null;
  }
}

function safeSetItem(key: string, value: string) {
  try {
    localStorage.setItem(key, value);
  } catch {}
}

function getSystemPreference(): 'light' | 'dark' {
  if (typeof window === 'undefined') return 'light';
  try {
    return window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light';
  } catch {
    return 'light';
  }
}

function resolveTheme(theme: Theme): 'light' | 'dark' {
  return theme === 'system' ? getSystemPreference() : theme;
}

function applyTheme(resolved: 'light' | 'dark') {
  try {
    document.documentElement.dataset.theme = resolved;
  } catch {}
}

export function useTheme() {
  useEffect(() => {
    const stored = safeGetItem(STORAGE_KEY) as Theme | null;

    if (stored) {
      applyTheme(resolveTheme(stored));
    }

    api.settings.get().then((settings) => {
      const theme = (settings.theme as Theme) || 'system';
      if (!stored) {
        applyTheme(resolveTheme(theme));
      }
      safeSetItem(STORAGE_KEY, theme);
    }).catch(() => {});

    try {
      const mq = window.matchMedia('(prefers-color-scheme: dark)');
      const handler = () => {
        const current = safeGetItem(STORAGE_KEY) as Theme | null;
        if (current === 'system' || !current) {
          applyTheme(getSystemPreference());
        }
      };
      mq.addEventListener('change', handler);
      return () => mq.removeEventListener('change', handler);
    } catch {
      return undefined;
    }
  }, []);

  const cycleTheme = useCallback(async () => {
    const current = (safeGetItem(STORAGE_KEY) as Theme) || 'system';
    const next: Theme = current === 'light' ? 'dark' : current === 'dark' ? 'system' : 'light';
    safeSetItem(STORAGE_KEY, next);
    applyTheme(resolveTheme(next));
    try {
      await api.settings.update({ theme: next });
    } catch {}
    return next;
  }, []);

  const getCurrentTheme = useCallback((): Theme => {
    return (safeGetItem(STORAGE_KEY) as Theme) || 'system';
  }, []);

  return { cycleTheme, getCurrentTheme };
}
