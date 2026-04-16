import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useSettings } from '../hooks/useSettings';

const mockSettings = {
  defaultPermissionMode: 'BYPASS',
  theme: 'dark',
  defaultModel: 'MiniMax-M2.7',
  sidebarFontFamily: 'sans',
  sidebarFontSize: 13,
  chatFontFamily: 'sans',
  chatFontSize: 13,
  referenceSourcePaths: ['/docs/references'],
};

describe('useSettings', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetches settings on mount', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSettings,
    });

    const { result } = renderHook(() => useSettings());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.settings?.defaultPermissionMode).toBe('BYPASS');
    expect(result.current.settings?.theme).toBe('dark');
    expect(result.current.settings?.defaultModel).toBe('MiniMax-M2.7');
    expect(result.current.settings?.sidebarFontFamily).toBe('sans');
    expect(result.current.settings?.chatFontSize).toBe(13);
    expect(result.current.settings?.referenceSourcePaths).toEqual(['/docs/references']);
    expect(result.current.error).toBeNull();
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useSettings());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
  });

  it('saveSettings calls PUT /api/settings and updates state', async () => {
    const updatedSettings = { ...mockSettings, theme: 'light', sidebarFontFamily: 'mono', chatFontSize: 16, referenceSourcePaths: ['/docs/references', '/notes/wiki'] };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSettings })
      .mockResolvedValueOnce({ ok: true, json: async () => updatedSettings });

    const { result } = renderHook(() => useSettings());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.saveSettings({ theme: 'light', sidebarFontFamily: 'mono', chatFontSize: 16, referenceSourcePaths: ['/docs/references', '/notes/wiki'] });
    });

    expect(result.current.settings?.theme).toBe('light');
    expect(result.current.settings?.sidebarFontFamily).toBe('mono');
    expect(result.current.settings?.chatFontSize).toBe(16);
    expect(result.current.settings?.referenceSourcePaths).toEqual(['/docs/references', '/notes/wiki']);
  });
});
