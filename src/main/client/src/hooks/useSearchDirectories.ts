import { useState, useEffect, useRef, useCallback } from 'react';
import type { SearchResult } from '../types/api';
import { api } from '../api/client';

interface UseSearchDirectoriesReturn {
  results: SearchResult[];
  loading: boolean;
  error: string | null;
  search: (query: string) => void;
}

export function useSearchDirectories(): UseSearchDirectoriesReturn {
  const [results, setResults] = useState<SearchResult[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const timerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const abortRef = useRef<AbortController | null>(null);

  const search = useCallback((query: string) => {
    if (timerRef.current !== null) {
      clearTimeout(timerRef.current);
      timerRef.current = null;
    }

    const trimmed = query.trim();
    if (trimmed.length === 0) {
      if (abortRef.current) {
        abortRef.current.abort();
        abortRef.current = null;
      }
      setResults([]);
      setLoading(false);
      setError(null);
      return;
    }

    timerRef.current = setTimeout(async () => {
      if (abortRef.current) {
        abortRef.current.abort();
      }
      const controller = new AbortController();
      abortRef.current = controller;

      setLoading(true);
      setError(null);
      try {
        const data = await api.search.directories(trimmed);
        if (!controller.signal.aborted) {
          setResults(data);
          setLoading(false);
        }
      } catch (err) {
        if (!controller.signal.aborted) {
          setError(err instanceof Error ? err.message : 'Search failed');
          setLoading(false);
        }
      }
    }, 300);
  }, []);

  useEffect(() => {
    return () => {
      if (timerRef.current !== null) {
        clearTimeout(timerRef.current);
      }
      if (abortRef.current) {
        abortRef.current.abort();
      }
    };
  }, []);

  return { results, loading, error, search };
}
