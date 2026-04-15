import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { McpConfigDto } from '../types/api';

interface UseMcpConfigResult {
  config: McpConfigDto | null;
  loading: boolean;
  error: string | null;
  saving: boolean;
  saveConfig: (updates: McpConfigDto) => Promise<void>;
}

export function useMcpConfig(enabled = true): UseMcpConfigResult {
  const [config, setConfig] = useState<McpConfigDto | null>(null);
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!enabled || loaded) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    api.mcpConfig
      .get()
      .then((data) => {
        if (!cancelled) {
          setConfig(data);
        }
      })
      .catch((err: unknown) => {
        if (!cancelled) {
          setError(err instanceof Error ? err.message : 'Failed to load MCP config');
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
          setLoaded(true);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [enabled, loaded]);

  const saveConfig = useCallback(async (updates: McpConfigDto) => {
    setSaving(true);
    setError(null);
    try {
      const updated = await api.mcpConfig.update(updates);
      setConfig(updated);
      setLoaded(true);
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Failed to save MCP config');
      throw err;
    } finally {
      setSaving(false);
    }
  }, []);

  return { config, loading, error, saving, saveConfig };
}
