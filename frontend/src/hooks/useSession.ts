import { useState, useEffect } from 'react';
import { api } from '../api/client';
import type { SessionDto } from '../types/api';

interface UseSessionResult {
  session: SessionDto | null;
  loading: boolean;
  error: string | null;
}

export function useSession(sessionId: string | undefined): UseSessionResult {
  const [session, setSession] = useState<SessionDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!sessionId) {
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setError(null);
    setSession(null);
    api.sessions
      .get(sessionId)
      .then((data) => {
        if (!cancelled) setSession(data);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load session');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  return { session, loading, error };
}
