import { useCallback, useRef } from 'react';
import { api } from '../api/client';
import type { DirectoryListing } from '../types/api';

export function useFileTree() {
  const cache = useRef(new Map<string, DirectoryListing>());

  const fetchDirectory = useCallback(
    async (projectPath: string, dirPath: string): Promise<DirectoryListing> => {
      const key = `${projectPath}::${dirPath}`;
      const cached = cache.current.get(key);
      if (cached) return cached;

      const listing = await api.files.tree(projectPath, dirPath);
      cache.current.set(key, listing);
      return listing;
    },
    [],
  );

  const clearCache = useCallback(() => {
    cache.current.clear();
  }, []);

  return { fetchDirectory, clearCache };
}
