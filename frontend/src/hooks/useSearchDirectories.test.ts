import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useSearchDirectories } from '../hooks/useSearchDirectories';

const mockResults = [
  { path: '/home/user/my-project', name: 'my-project', matchType: 'EXACT' as const },
  { path: '/home/user/my-app', name: 'my-app', matchType: 'STARTS_WITH' as const },
];

describe('useSearchDirectories', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('search with non-empty query eventually sets results', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    const { result } = renderHook(() => useSearchDirectories());

    act(() => {
      result.current.search('my-project');
    });

    await waitFor(() => expect(result.current.results.length).toBeGreaterThan(0), { timeout: 2000 });

    expect(result.current.results).toHaveLength(2);
    expect(result.current.results[0]?.name).toBe('my-project');
  });

  it('search with empty query clears results without API call', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockResults,
    });

    const { result } = renderHook(() => useSearchDirectories());

    act(() => {
      result.current.search('my-project');
    });
    await waitFor(() => expect(result.current.results.length).toBeGreaterThan(0), { timeout: 2000 });

    act(() => {
      result.current.search('');
    });

    expect(result.current.results).toHaveLength(0);
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useSearchDirectories());

    act(() => {
      result.current.search('fail-query');
    });

    await waitFor(() => expect(result.current.error).toBeTruthy(), { timeout: 2000 });
  });
});
