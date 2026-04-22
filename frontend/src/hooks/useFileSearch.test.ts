import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useFileSearch, fuzzyMatch } from '../hooks/useFileSearch';

const makeFileNode = (name: string, path: string, isDirectory = false) => ({
  name,
  path,
  isDirectory,
  size: 0,
  lastModified: '',
});

const rootListing = {
  path: '.',
  children: [
    makeFileNode('src', 'src', true),
    makeFileNode('CLAUDE.md', 'CLAUDE.md'),
    makeFileNode('pom.xml', 'pom.xml'),
  ],
};

const srcListing = {
  path: 'src',
  children: [
    makeFileNode('main', 'src/main', true),
    makeFileNode('test', 'src/test', true),
  ],
};

const srcMainListing = {
  path: 'src/main',
  children: [
    makeFileNode('java', 'src/main/java', true),
    makeFileNode('Main.java', 'src/main/Main.java'),
  ],
};

const srcTestListing = {
  path: 'src/test',
  children: [],
};

const srcMainJavaListing = {
  path: 'src/main/java',
  children: [
    makeFileNode('App.java', 'src/main/java/App.java'),
  ],
};

function mockFetchWithListings() {
  const listings: Record<string, typeof rootListing> = {
    '.': rootListing,
    'src': srcListing,
    'src/main': srcMainListing,
    'src/test': srcTestListing,
    'src/main/java': srcMainJavaListing,
  };

  return (input: RequestInfo | URL) => {
    const url = typeof input === 'string' ? input : input.toString();
    const dirPath = new URL(url, 'http://localhost').searchParams.get('dirPath') ?? '.';
    const listing = listings[dirPath] ?? { path: dirPath, children: [] };
    return Promise.resolve({
      ok: true,
      json: async () => listing,
    } as Response);
  };
}

describe('fuzzyMatch', () => {
  it('returns positive score for matching query', () => {
    expect(fuzzyMatch('src/main/java', 'smj')).toBeGreaterThan(0);
  });

  it('returns 0 when query does not match', () => {
    expect(fuzzyMatch('pom.xml', 'xyz')).toBe(0);
  });

  it('scores consecutive matches higher', () => {
    const consecutive = fuzzyMatch('src/main', 'src');
    const scattered = fuzzyMatch('s_r_c_file', 'src');
    expect(consecutive).toBeGreaterThan(scattered);
  });

  it('is case insensitive', () => {
    expect(fuzzyMatch('SRC/MAIN', 'src')).toBeGreaterThan(0);
  });

  it('scores word boundary matches higher', () => {
    const boundary = fuzzyMatch('src/main/java', 'mj');
    const nonBoundary = fuzzyMatch('src/main/java', 'ai');
    expect(boundary).toBeGreaterThan(nonBoundary);
  });
});

describe('useFileSearch', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('search returns empty before loading', () => {
    const { result } = renderHook(() => useFileSearch('/project'));
    expect(result.current.search('src')).toEqual([]);
    expect(result.current.isLoaded).toBe(false);
  });

  it('loads file index and returns search results', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(mockFetchWithListings());

    const { result } = renderHook(() => useFileSearch('/project'));

    await act(async () => {
      await result.current.ensureLoaded();
    });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));

    const results = result.current.search('java');
    expect(results.length).toBeGreaterThan(0);
    expect(results.some((r) => r.path.includes('java'))).toBe(true);
  });

  it('returns limited results for empty query', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(mockFetchWithListings());

    const { result } = renderHook(() => useFileSearch('/project'));

    await act(async () => {
      await result.current.ensureLoaded();
    });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));

    const results = result.current.search('');
    expect(results.length).toBeGreaterThan(0);
    expect(results.length).toBeLessThanOrEqual(50);
  });

  it('returns empty array when no files match', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(mockFetchWithListings());

    const { result } = renderHook(() => useFileSearch('/project'));

    await act(async () => {
      await result.current.ensureLoaded();
    });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));

    const results = result.current.search('zzzznonexistent');
    expect(results).toEqual([]);
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useFileSearch('/project'));

    await act(async () => {
      await result.current.ensureLoaded();
    });

    await waitFor(() => expect(result.current.error).toBeTruthy());
    expect(result.current.isLoaded).toBe(false);
  });

  it('uses cache on subsequent calls', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation(mockFetchWithListings());

    const { result } = renderHook(() => useFileSearch('/project'));

    await act(async () => {
      await result.current.ensureLoaded();
    });

    await waitFor(() => expect(result.current.isLoaded).toBe(true));

    const firstCallCount = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.length;

    await act(async () => {
      await result.current.ensureLoaded();
    });

    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.length).toBe(firstCallCount);
  });
});
