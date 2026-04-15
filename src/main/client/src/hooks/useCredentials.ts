import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { CredentialsDto, CredentialsUpdateRequest } from '../types/api';

interface UseCredentialsResult {
  credentials: CredentialsDto | null;
  loading: boolean;
  error: string | null;
  saving: boolean;
  saveCredentials: (updates: CredentialsUpdateRequest) => Promise<void>;
}

export function useCredentials(): UseCredentialsResult {
  const [credentials, setCredentials] = useState<CredentialsDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.credentials
      .get()
      .then((data) => {
        if (!cancelled) setCredentials(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load credentials');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const saveCredentials = useCallback(async (updates: CredentialsUpdateRequest) => {
    setSaving(true);
    try {
      const updated = await api.credentials.update(updates);
      setCredentials(updated);
    } finally {
      setSaving(false);
    }
  }, []);

  return { credentials, loading, error, saving, saveCredentials };
}
