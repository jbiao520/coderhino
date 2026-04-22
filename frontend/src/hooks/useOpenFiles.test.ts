import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useOpenFiles } from './useOpenFiles';

const mockFileContent = {
  name: 'test.ts',
  path: 'src/test.ts',
  content: 'const x = 1;',
  size: 12,
  truncated: false,
  binary: false,
};

describe('useOpenFiles', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('initializes with empty files and tree as active tab', () => {
    const { result } = renderHook(() => useOpenFiles('/tmp/project'));
    expect(result.current.openFiles).toEqual([]);
    expect(result.current.activeTabId).toBe('tree');
    expect(result.current.getActiveFile()).toBeNull();
  });

  it('opens a file and sets it as active', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'test.ts', path: 'src/test.ts', isDirectory: false, size: 12, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.openFiles).toHaveLength(1);
      expect(result.current.openFiles[0]!.path).toBe('src/test.ts');
      expect(result.current.activeTabId).toBe('src/test.ts');
    });
  });

  it('activates existing tab instead of duplicating', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'test.ts', path: 'src/test.ts', isDirectory: false, size: 12, lastModified: '' });
    });

    await waitFor(() => expect(result.current.openFiles).toHaveLength(1));

    result.current.setActiveTab('tree');

    await act(async () => {
      await result.current.openFile({ name: 'test.ts', path: 'src/test.ts', isDirectory: false, size: 12, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.openFiles).toHaveLength(1);
      expect(result.current.activeTabId).toBe('src/test.ts');
    });
  });

  it('closes active tab and activates neighbor', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'a.ts', path: 'a.ts', isDirectory: false, size: 10, lastModified: '' });
    });
    await act(async () => {
      await result.current.openFile({ name: 'b.ts', path: 'b.ts', isDirectory: false, size: 10, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.activeTabId).toBe('b.ts');
    });

    act(() => {
      result.current.closeTab('b.ts');
    });

    await waitFor(() => {
      expect(result.current.openFiles).toHaveLength(1);
      expect(result.current.activeTabId).toBe('a.ts');
    });
  });

  it('closes non-active tab without changing active', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'a.ts', path: 'a.ts', isDirectory: false, size: 10, lastModified: '' });
    });
    await act(async () => {
      await result.current.openFile({ name: 'b.ts', path: 'b.ts', isDirectory: false, size: 10, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.activeTabId).toBe('b.ts');
    });

    act(() => {
      result.current.closeTab('a.ts');
    });

    await waitFor(() => {
      expect(result.current.openFiles).toHaveLength(1);
      expect(result.current.activeTabId).toBe('b.ts');
    });
  });

  it('returns to tree tab when last file tab is closed', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'a.ts', path: 'a.ts', isDirectory: false, size: 10, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.activeTabId).toBe('a.ts');
    });

    act(() => {
      result.current.closeTab('a.ts');
    });

    await waitFor(() => {
      expect(result.current.openFiles).toHaveLength(0);
      expect(result.current.activeTabId).toBe('tree');
    });
  });

  it('setActiveTab changes the active tab', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValue({
      ok: true,
      json: async () => mockFileContent,
    });

    const { result } = renderHook(() => useOpenFiles('/tmp/project'));

    await act(async () => {
      await result.current.openFile({ name: 'a.ts', path: 'a.ts', isDirectory: false, size: 10, lastModified: '' });
    });

    await waitFor(() => {
      expect(result.current.activeTabId).toBe('a.ts');
    });

    act(() => {
      result.current.setActiveTab('tree');
    });

    await waitFor(() => {
      expect(result.current.activeTabId).toBe('tree');
    });
  });
});
