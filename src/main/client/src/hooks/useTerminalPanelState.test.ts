import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { useTerminalPanelState } from './useTerminalPanelState';

describe('useTerminalPanelState', () => {
  let setItemMock: ReturnType<typeof vi.fn>;

  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    const store = new Map<string, string>();
    setItemMock = vi.fn((key: string, value: string) => {
      store.set(key, value);
    });
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: setItemMock,
        removeItem: (key: string) => {
          store.delete(key);
        },
        clear: () => {
          store.clear();
        },
      },
    });
  });

  it('keeps the persisted selected terminal when it still exists after restore', async () => {
    localStorage.setItem('claude-terminal-state', JSON.stringify({
      'session:ses-1': {
        visible: true,
        selectedTerminalId: 'term-2',
        tabs: [{
          terminalId: 'term-2',
          label: 'Terminal 2',
          status: 'RUNNING',
          cwd: '/tmp/project',
          worktreeId: 'default',
          createdAt: '2026-04-11T00:00:00Z',
          exitCode: null,
          message: null,
        }],
      },
    }));

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        terminals: [
          {
            terminalId: 'term-1',
            label: 'Terminal 1',
            status: 'RUNNING',
            cwd: '/tmp/project',
            worktreeId: 'default',
            createdAt: '2026-04-11T00:00:01Z',
          },
          {
            terminalId: 'term-2',
            label: 'Terminal 2',
            status: 'RUNNING',
            cwd: '/tmp/project',
            worktreeId: 'default',
            createdAt: '2026-04-11T00:00:00Z',
          },
        ],
      }),
    });

    const { result } = renderHook(() => useTerminalPanelState('ses-1'));

    await waitFor(() => expect(result.current.restoring).toBe(false));
    expect(result.current.selectedTerminalId).toBe('term-2');
    expect(result.current.selectedTerminal?.terminalId).toBe('term-2');
  });

  it('preserves the persisted terminal snapshot when the live terminal is gone', async () => {
    localStorage.setItem('claude-terminal-state', JSON.stringify({
      'session:ses-1': {
        visible: true,
        selectedTerminalId: 'missing-term',
        tabs: [{
          terminalId: 'missing-term',
          label: 'Missing terminal',
          status: 'RUNNING',
          cwd: '/tmp/project',
          worktreeId: 'default',
          createdAt: '2026-04-11T00:00:00Z',
          exitCode: null,
          message: null,
        }],
      },
    }));

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({
        terminals: [{
          terminalId: 'term-1',
          label: 'Terminal 1',
          status: 'RUNNING',
          cwd: '/tmp/project',
          worktreeId: 'default',
          createdAt: '2026-04-11T00:00:01Z',
        }],
      }),
    });

    const { result } = renderHook(() => useTerminalPanelState('ses-1'));

    await waitFor(() => expect(result.current.restoring).toBe(false));
    expect(result.current.selectedTerminalId).toBe('missing-term');
    expect(result.current.selectedTerminal?.terminalId).toBe('missing-term');
    expect(result.current.selectedTerminal?.status).toBe('EXITED');
    expect(result.current.terminals.map((terminal) => terminal.terminalId)).toEqual(['missing-term', 'term-1']);
  });

  it('forwards the requested worktree when creating a terminal', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ terminals: [] }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          terminalId: 'term-1',
          label: 'Terminal 1',
          status: 'RUNNING',
          cwd: '/tmp/project',
          worktreeId: 'feature-a',
          createdAt: '2026-04-11T00:00:02Z',
        }),
      });

    const { result } = renderHook(() => useTerminalPanelState('ses-1'));

    await waitFor(() => expect(result.current.restoring).toBe(false));
    await act(async () => {
      await result.current.createTerminal('feature-a');
    });

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.some(([url, init]) => url === '/api/sessions/ses-1/terminals'
      && init?.method === 'POST'
      && init.body === JSON.stringify({ worktreeId: 'feature-a' }))).toBe(true);
    expect(result.current.selectedTerminal?.worktreeId).toBe('feature-a');
  });

  it('treats redundant visibility updates as no-ops', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => ({ terminals: [] }),
    });

    const { result } = renderHook(() => useTerminalPanelState('ses-1'));

    await waitFor(() => expect(result.current.restoring).toBe(false));
    setItemMock.mockClear();

    act(() => {
      result.current.setVisible(false);
    });
    act(() => {
      result.current.setVisible(false);
    });
    expect(setItemMock).not.toHaveBeenCalled();

    act(() => {
      result.current.openPanel();
    });
    expect(setItemMock).toHaveBeenCalledTimes(1);

    setItemMock.mockClear();
    act(() => {
      result.current.openPanel();
    });
    expect(setItemMock).not.toHaveBeenCalled();

    act(() => {
      result.current.hidePanel();
    });
    expect(setItemMock).toHaveBeenCalledTimes(1);

    setItemMock.mockClear();
    act(() => {
      result.current.hidePanel();
    });
    expect(setItemMock).not.toHaveBeenCalled();
  });
});
