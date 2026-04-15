import { useState, useCallback } from 'react';
import { api } from '../api/client';
import type { FileNode, FileContent } from '../types/api';

export interface OpenFile {
  path: string;
  name: string;
  content: FileContent | null;
  loading: boolean;
}

interface UseOpenFilesReturn {
  openFiles: OpenFile[];
  activeTabId: string;
  openFile: (node: FileNode) => void;
  closeTab: (path: string) => void;
  setActiveTab: (id: string) => void;
  getActiveFile: () => OpenFile | null;
}

export function useOpenFiles(projectPath: string): UseOpenFilesReturn {
  const [openFiles, setOpenFiles] = useState<OpenFile[]>([]);
  const [activeTabId, setActiveTabId] = useState<string>('tree');

  const openFile = useCallback(
    async (node: FileNode) => {
      const existing = openFiles.find((f) => f.path === node.path);
      if (existing) {
        setActiveTabId(node.path);
        return;
      }

      const entry: OpenFile = { path: node.path, name: node.name, content: null, loading: true };
      setOpenFiles((prev) => [...prev, entry]);
      setActiveTabId(node.path);

      try {
        const content = await api.files.content(projectPath, node.path);
        setOpenFiles((prev) =>
          prev.map((f) => (f.path === node.path ? { ...f, content, loading: false } : f)),
        );
      } catch {
        setOpenFiles((prev) =>
          prev.map((f) => (f.path === node.path ? { ...f, loading: false } : f)),
        );
      }
    },
    [projectPath, openFiles],
  );

  const closeTab = useCallback(
    (path: string) => {
      setOpenFiles((prev) => {
        const idx = prev.findIndex((f) => f.path === path);
        const next = prev.filter((f) => f.path !== path);

        if (activeTabId === path) {
          if (next.length > 0) {
            const newIdx = Math.min(idx, next.length - 1);
            const candidate = next[newIdx];
            setActiveTabId(candidate ? candidate.path : 'tree');
          } else {
            setActiveTabId('tree');
          }
        }

        return next;
      });
    },
    [activeTabId],
  );

  const setActiveTab = useCallback((id: string) => {
    setActiveTabId(id);
  }, []);

  const getActiveFile = useCallback((): OpenFile | null => {
    if (activeTabId === 'tree') return null;
    return openFiles.find((f) => f.path === activeTabId) ?? null;
  }, [activeTabId, openFiles]);

  return { openFiles, activeTabId, openFile, closeTab, setActiveTab, getActiveFile };
}
