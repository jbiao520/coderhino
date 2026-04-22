import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useSessions } from '../hooks/useSessions';

describe('useSessions', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('returns sessions from GET /api/sessions scoped by projectId', async () => {
    const mockSessions = [
      {
        sessionId: 'ses-001',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:00:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        projectId: 'project-1',
      },
    ];
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ sessions: mockSessions }),
    });

    const { result } = renderHook(() => useSessions('project-1'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.sessions).toHaveLength(1);
    expect(result.current.sessions[0]?.sessionId).toBe('ses-001');
    expect(result.current.error).toBeNull();
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useSessions('project-1'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
    expect(result.current.sessions).toHaveLength(0);
  });

  it('createSession calls POST /api/sessions with projectId', async () => {
    const newSession = {
      sessionId: 'ses-new',
      createdAt: '2026-04-07T11:00:00Z',
      updatedAt: '2026-04-07T11:00:00Z',
      status: 'ACTIVE',
      activeRun: null,
      messages: [],
      projectId: 'project-1',
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ sessions: [] }) })
      .mockResolvedValueOnce({ ok: true, json: async () => newSession })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ sessions: [newSession] }) });

    const { result } = renderHook(() => useSessions('project-1'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    let created: { sessionId: string } | undefined;
    await waitFor(async () => {
      created = await result.current.createSession('project-1');
    });
    expect(created?.sessionId).toBe('ses-new');
  });

  it('stays empty and idle when projectId is missing', async () => {
    const { result } = renderHook(() => useSessions());

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.sessions).toEqual([]);
    expect(result.current.error).toBeNull();
    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls).toHaveLength(0);
  });

  it('deleteSession calls DELETE /api/sessions/{id} and removes the session locally', async () => {
    const mockSessions = [
      {
        sessionId: 'ses-001',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:00:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        projectId: 'project-1',
      },
    ];
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ sessions: mockSessions }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({}),
      });

    const { result } = renderHook(() => useSessions('project-1'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await result.current.deleteSession('ses-001');

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as [string, RequestInit | undefined][];
    expect(calls.some(([url, init]) => url === '/api/sessions/ses-001' && init?.method === 'DELETE')).toBe(true);
    await waitFor(() => expect(result.current.sessions).toEqual([]));
  });
});
