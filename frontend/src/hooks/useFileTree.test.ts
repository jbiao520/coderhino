import { renderHook, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useFileTree } from '../hooks/useFileTree';

const mockListing = {
  path: '.',
  children: [
    { name: 'src', path: 'src', isDirectory: true, size: 0, lastModified: '1234567890000' },
    { name: 'package.json', path: 'package.json', isDirectory: false, size: 1024, lastModified: '1234567890000' },
  ],
};

describe('useFileTree', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetchDirectory calls API and returns listing', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    const { result } = renderHook(() => useFileTree());

    let listing: typeof mockListing;
    await act(async () => {
      listing = await result.current.fetchDirectory('/tmp/test-project', '.');
    });

    expect(listing!.children).toHaveLength(2);
    expect(listing!.children[0]?.name).toBe('src');
    expect(listing!.children[1]?.name).toBe('package.json');
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it('fetchDirectory caches results (second call does not hit API)', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockListing,
    });

    const { result } = renderHook(() => useFileTree());

    await act(async () => {
      await result.current.fetchDirectory('/tmp/test-project', '.');
    });
    await act(async () => {
      await result.current.fetchDirectory('/tmp/test-project', '.');
    });

    expect(globalThis.fetch).toHaveBeenCalledTimes(1);
  });

  it('clearCache resets the cache so next call hits API again', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockListing,
    });

    const { result } = renderHook(() => useFileTree());

    await act(async () => {
      await result.current.fetchDirectory('/tmp/test-project', '.');
    });
    expect(globalThis.fetch).toHaveBeenCalledTimes(1);

    act(() => {
      result.current.clearCache();
    });

    await act(async () => {
      await result.current.fetchDirectory('/tmp/test-project', '.');
    });
    expect(globalThis.fetch).toHaveBeenCalledTimes(2);
  });
});
