import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { WebSettings } from '../types/api';

interface UseSettingsResult {
  settings: WebSettings | null;
  loading: boolean;
  error: string | null;
  saving: boolean;
  saveSettings: (updates: Partial<WebSettings>) => Promise<void>;
}

export function useSettings(): UseSettingsResult {
  const [settings, setSettings] = useState<WebSettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.settings
      .get()
      .then((data) => {
        if (!cancelled) setSettings(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load settings');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const saveSettings = useCallback(async (updates: Partial<WebSettings>) => {
    setSaving(true);
    try {
      const updated = await api.settings.update(updates);
      setSettings(updated);
    } finally {
      setSaving(false);
    }
  }, []);

  return { settings, loading, error, saving, saveSettings };
}
