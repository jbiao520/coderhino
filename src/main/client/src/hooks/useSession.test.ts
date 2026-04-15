import { renderHook, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useSession } from '../hooks/useSession';

const mockSession = {
  sessionId: 'ses-abc',
  createdAt: '2026-04-07T10:00:00Z',
  updatedAt: '2026-04-07T10:05:00Z',
  status: 'ACTIVE',
  activeRun: null,
  messages: ['hello'],
};

describe('useSession', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetches session by id on mount', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useSession('ses-abc'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.session?.sessionId).toBe('ses-abc');
    expect(result.current.session?.messages).toHaveLength(1);
    expect(result.current.error).toBeNull();
  });

  it('re-fetches when sessionId changes (reconnect on remount)', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ ...mockSession, sessionId: 'ses-abc' }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ ...mockSession, sessionId: 'ses-xyz' }) });

    const { result, rerender } = renderHook(({ id }) => useSession(id), {
      initialProps: { id: 'ses-abc' },
    });
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.session?.sessionId).toBe('ses-abc');

    rerender({ id: 'ses-xyz' });
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.session?.sessionId).toBe('ses-xyz');
  });

  it('sets error on failed fetch', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useSession('ses-missing'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
    expect(result.current.session).toBeNull();
  });

  it('does not fetch when sessionId is undefined', async () => {
    const { result } = renderHook(() => useSession(undefined));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(globalThis.fetch).not.toHaveBeenCalled();
    expect(result.current.session).toBeNull();
  });
});
