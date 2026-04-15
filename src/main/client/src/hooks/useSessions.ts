import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { SessionDto } from '../types/api';

interface UseSessionsResult {
  sessions: SessionDto[];
  loading: boolean;
  error: string | null;
  createSession: (projectId: string, worktreeId?: string) => Promise<SessionDto>;
  deleteSession: (sessionId: string) => Promise<void>;
  reload: () => void;
}

export function useSessions(projectId?: string): UseSessionsResult {
  const [sessions, setSessions] = useState<SessionDto[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    if (!projectId) {
      setSessions([]);
      setLoading(false);
      setError(null);
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);
    api.sessions
      .list(projectId)
      .then((data) => {
        if (!cancelled) setSessions(data.sessions);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load sessions');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [projectId, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  const createSession = useCallback(
    async (nextProjectId: string, worktreeId?: string): Promise<SessionDto> => {
      const body = worktreeId ? { projectId: nextProjectId, worktreeId } : { projectId: nextProjectId };
      const session = await api.sessions.create(body);
      if (nextProjectId === projectId) {
        reload();
      }
      return session;
    },
    [projectId, reload],
  );

  const deleteSession = useCallback(
    async (sessionId: string): Promise<void> => {
      await api.sessions.delete(sessionId);
      setSessions((prev) => prev.filter((session) => session.sessionId !== sessionId));
    },
    [],
  );

  return { sessions, loading, error, createSession, deleteSession, reload };
}
