import { useCallback, useEffect, useMemo, useState } from 'react';
import { api } from '../api/client';
import type { TerminalDto } from '../types/api';

const STORAGE_KEY = 'claude-terminal-state';

interface PersistedTerminalTab {
  terminalId: string;
  label: string;
  status: string;
  cwd: string;
  worktreeId?: string | null;
  createdAt?: string;
  exitCode?: number | null;
  message?: string | null;
}

interface PersistedSessionTerminalState {
  visible: boolean;
  selectedTerminalId: string | null;
  tabs: PersistedTerminalTab[];
}

type PersistedTerminalStore = Record<string, PersistedSessionTerminalState>;

function loadStore(): PersistedTerminalStore {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? JSON.parse(raw) as PersistedTerminalStore : {};
  } catch {
    return {};
  }
}

function saveStore(store: PersistedTerminalStore) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(store));
  } catch {
  }
}

function keyForSession(sessionId: string | undefined): string | null {
  if (!sessionId) {
    return null;
  }
  return `session:${sessionId}`;
}

function toTerminalDto(tab: PersistedTerminalTab): TerminalDto {
  return {
    terminalId: tab.terminalId,
    label: tab.label,
    status: tab.status,
    cwd: tab.cwd,
    worktreeId: tab.worktreeId ?? null,
    createdAt: tab.createdAt ?? new Date(0).toISOString(),
    exitCode: tab.exitCode ?? null,
    message: tab.message ?? null,
  };
}

function mergePersistedWithLive(persisted: PersistedTerminalTab[], live: TerminalDto[]): TerminalDto[] {
  const liveById = new Map(live.map((terminal) => [terminal.terminalId, terminal]));
  const merged: TerminalDto[] = [];
  for (const tab of persisted) {
    const liveTerminal = liveById.get(tab.terminalId);
    if (liveTerminal) {
      merged.push(liveTerminal);
      liveById.delete(tab.terminalId);
      continue;
    }
    merged.push({
      ...toTerminalDto(tab),
      status: 'EXITED',
      message: tab.message ?? 'Terminal session ended',
    });
  }
  for (const terminal of liveById.values()) {
    merged.push(terminal);
  }
  return merged;
}

export interface UseTerminalPanelStateResult {
  visible: boolean;
  terminals: TerminalDto[];
  selectedTerminalId: string | null;
  selectedTerminal: TerminalDto | null;
  restoring: boolean;
  setVisible: (visible: boolean) => void;
  openPanel: () => void;
  hidePanel: () => void;
  createTerminal: (worktreeId?: string | null) => Promise<TerminalDto>;
  closeTerminal: (terminalId: string) => Promise<void>;
  selectTerminal: (terminalId: string) => void;
  replaceTerminal: (terminal: TerminalDto) => void;
}

export function useTerminalPanelState(sessionId: string | undefined): UseTerminalPanelStateResult {
  const storageKey = keyForSession(sessionId);
  const initialState = useMemo(() => {
    if (!storageKey) {
      return { visible: false, selectedTerminalId: null, tabs: [] as PersistedTerminalTab[] };
    }
    return loadStore()[storageKey] ?? { visible: false, selectedTerminalId: null, tabs: [] as PersistedTerminalTab[] };
  }, [storageKey]);

  const [visible, setVisibleState] = useState(initialState.visible);
  const [selectedTerminalId, setSelectedTerminalId] = useState<string | null>(initialState.selectedTerminalId);
  const [terminals, setTerminals] = useState<TerminalDto[]>(initialState.tabs.map(toTerminalDto));
  const [restoring, setRestoring] = useState(Boolean(storageKey));

  useEffect(() => {
    if (!storageKey) {
      setVisibleState(false);
      setSelectedTerminalId(null);
      setTerminals([]);
      setRestoring(false);
      return;
    }
    const persisted = loadStore()[storageKey] ?? { visible: false, selectedTerminalId: null, tabs: [] as PersistedTerminalTab[] };
    setVisibleState(persisted.visible);
    setSelectedTerminalId(persisted.selectedTerminalId);
    setTerminals(persisted.tabs.map(toTerminalDto));
    setRestoring(true);

    api.sessions.listTerminals(sessionId!).then((response) => {
      const merged = mergePersistedWithLive(persisted.tabs, response.terminals);
      setTerminals(merged);
      const hasPersistedSelection = persisted.selectedTerminalId != null
        && merged.some((terminal) => terminal.terminalId === persisted.selectedTerminalId);
      setSelectedTerminalId(hasPersistedSelection
        ? persisted.selectedTerminalId
        : merged[0]?.terminalId ?? null);
    }).catch(() => {
    }).finally(() => {
      setRestoring(false);
    });
  }, [sessionId, storageKey]);

  useEffect(() => {
    if (!storageKey) {
      return;
    }
    const store = loadStore();
    store[storageKey] = {
      visible,
      selectedTerminalId,
      tabs: terminals.map((terminal) => ({
        terminalId: terminal.terminalId,
        label: terminal.label,
        status: terminal.status,
        cwd: terminal.cwd,
        worktreeId: terminal.worktreeId ?? null,
        createdAt: terminal.createdAt,
        exitCode: terminal.exitCode ?? null,
        message: terminal.message ?? null,
      })),
    };
    saveStore(store);
  }, [storageKey, visible, selectedTerminalId, terminals]);

  const openPanel = useCallback(() => {
    setVisibleState((current) => (current ? current : true));
  }, []);

  const hidePanel = useCallback(() => {
    setVisibleState((current) => (current ? false : current));
  }, []);

  const setVisible = useCallback((nextVisible: boolean) => {
    setVisibleState((current) => (current === nextVisible ? current : nextVisible));
  }, []);

  const createTerminal = useCallback(async (worktreeId?: string | null) => {
    if (!sessionId) {
      throw new Error('Missing session ID');
    }
    const created = await api.sessions.createTerminal(sessionId, { worktreeId: worktreeId ?? null });
    setTerminals((current) => [...current, created]);
    setSelectedTerminalId(created.terminalId);
    setVisibleState(true);
    return created;
  }, [sessionId]);

  const closeTerminal = useCallback(async (terminalId: string) => {
    if (!sessionId) {
      return;
    }
    const remaining = terminals.filter((terminal) => terminal.terminalId !== terminalId);
    if (terminals.some((terminal) => terminal.status === 'RUNNING' && terminal.terminalId === terminalId)) {
      await api.sessions.closeTerminal(sessionId, terminalId);
    }
    setTerminals(remaining);
    setSelectedTerminalId((current) => {
      if (current !== terminalId) {
        return current;
      }
      return remaining[0]?.terminalId ?? null;
    });
    if (remaining.length === 0) {
      setVisibleState(false);
    }
  }, [sessionId, terminals]);

  const selectTerminal = useCallback((terminalId: string) => {
    setSelectedTerminalId(terminalId);
    setVisibleState(true);
  }, []);

  const replaceTerminal = useCallback((terminal: TerminalDto) => {
    setTerminals((current) => {
      const index = current.findIndex((item) => item.terminalId === terminal.terminalId);
      if (index < 0) {
        return [...current, terminal];
      }
      const next = [...current];
      next[index] = terminal;
      return next;
    });
  }, []);

  const selectedTerminal = terminals.find((terminal) => terminal.terminalId === selectedTerminalId) ?? terminals[0] ?? null;

  return {
    visible,
    terminals,
    selectedTerminalId,
    selectedTerminal,
    restoring,
    setVisible,
    openPanel,
    hidePanel,
    createTerminal,
    closeTerminal,
    selectTerminal,
    replaceTerminal,
  };
}
