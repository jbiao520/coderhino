import { useState, useRef, useCallback } from 'react';
import type { FileNode } from '../types/api';
import { api } from '../api/client';

interface FuzzyResult {
  node: FileNode;
  score: number;
}

function fuzzyMatch(text: string, query: string): number {
  const lower = text.toLowerCase();
  const q = query.toLowerCase();
  let score = 0;
  let qi = 0;
  let lastMatchIdx = -2;

  for (let i = 0; i < lower.length && qi < q.length; i++) {
    if (lower[i] === q[qi]) {
      score += (lastMatchIdx === i - 1) ? 3 : 1;
      if (i === 0 || lower[i - 1] === '/' || lower[i - 1] === '.' || lower[i - 1] === '-') {
        score += 2;
      }
      if (i === 0) {
        score += 1;
      }
      lastMatchIdx = i;
      qi++;
    }
  }

  return qi === q.length ? score : 0;
}

function sortResults(results: FuzzyResult[]): FileNode[] {
  return results
    .sort((a, b) => {
      if (b.score !== a.score) return b.score - a.score;
      return a.node.path.localeCompare(b.node.path);
    })
    .map((r) => r.node);
}

async function loadFileIndex(projectPath: string): Promise<FileNode[]> {
  const allFiles: FileNode[] = [];
  const queue: string[] = ['.'];
  const visited = new Set<string>();

  while (queue.length > 0) {
    const dirPath = queue.shift()!;
    if (visited.has(dirPath)) {
      continue;
    }
    visited.add(dirPath);
    const listing = await api.files.tree(projectPath, dirPath);
    for (const child of listing.children) {
      allFiles.push(child);
      if (child.isDirectory && !visited.has(child.path)) {
        queue.push(child.path);
      }
    }
  }

  return allFiles;
}

interface UseFileSearchReturn {
  search: (query: string) => FileNode[];
  loading: boolean;
  error: string | null;
  ensureLoaded: () => Promise<void>;
  isLoaded: boolean;
}

export function useFileSearch(projectPath: string): UseFileSearchReturn {
  const indexRef = useRef<FileNode[]>([]);
  const loadedRef = useRef(false);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [isLoaded, setIsLoaded] = useState(false);
  const loadingPromiseRef = useRef<Promise<void> | null>(null);

  const ensureLoaded = useCallback(async () => {
    if (loadedRef.current) return;
    if (loadingPromiseRef.current) {
      await loadingPromiseRef.current;
      return;
    }

    setLoading(true);
    setError(null);

    loadingPromiseRef.current = loadFileIndex(projectPath)
      .then((files) => {
        indexRef.current = files;
        loadedRef.current = true;
        setIsLoaded(true);
        setLoading(false);
      })
      .catch((err) => {
        setError(err instanceof Error ? err.message : 'Failed to load file index');
        setLoading(false);
      })
      .finally(() => {
        loadingPromiseRef.current = null;
      });

    await loadingPromiseRef.current;
  }, [projectPath]);

  const search = useCallback((query: string): FileNode[] => {
    if (!loadedRef.current) return [];

    const files = indexRef.current;
    if (!query) {
      return files.slice(0, 50);
    }

    const results: FuzzyResult[] = [];
    for (const node of files) {
      const score = fuzzyMatch(node.path, query);
      if (score > 0) {
        results.push({ node, score });
      }
    }

    return sortResults(results).slice(0, 50);
  }, []);

  return { search, loading, error, ensureLoaded, isLoaded };
}

export { fuzzyMatch, sortResults };
