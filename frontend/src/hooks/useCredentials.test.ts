import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useCredentials } from '../hooks/useCredentials';

const mockCredentials = {
  defaultProviderId: 'provider-1',
  providers: [
    {
      id: 'provider-1',
      name: 'MiniMax',
      apiKeyMasked: '****abcd',
      apiBaseUrl: 'https://api.example.com',
      models: [
        { id: 'MiniMax-M2.7', contextWindow: 128000 },
        { id: 'MiniMax-M2.5', contextWindow: 256000 },
      ],
      hasApiKey: true,
    },
  ],
};

describe('useCredentials', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetches credentials on mount', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockCredentials,
    });

    const { result } = renderHook(() => useCredentials());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.credentials?.defaultProviderId).toBe('provider-1');
    expect(result.current.credentials?.providers[0]?.apiKeyMasked).toBe('****abcd');
    expect(result.current.credentials?.providers[0]?.apiBaseUrl).toBe('https://api.example.com');
    expect(result.current.credentials?.providers[0]?.models).toEqual([
      { id: 'MiniMax-M2.7', contextWindow: 128000 },
      { id: 'MiniMax-M2.5', contextWindow: 256000 },
    ]);
    expect(result.current.credentials?.providers[0]?.hasApiKey).toBe(true);
    expect(result.current.error).toBeNull();
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useCredentials());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
  });

  it('saveCredentials calls PUT /api/credentials and updates state', async () => {
    const updatedCredentials = {
      ...mockCredentials,
      providers: [
        {
          ...mockCredentials.providers[0],
          apiBaseUrl: 'https://new-api.example.com',
        },
      ],
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockCredentials })
      .mockResolvedValueOnce({ ok: true, json: async () => updatedCredentials });

    const { result } = renderHook(() => useCredentials());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.saveCredentials({
        defaultProviderId: 'provider-1',
        providers: [
          {
            id: 'provider-1',
            name: 'MiniMax',
            apiBaseUrl: 'https://new-api.example.com',
            models: [
              { id: 'MiniMax-M2.7', contextWindow: 128000 },
              { id: 'MiniMax-M2.5', contextWindow: 256000 },
            ],
            apiType: 'CLAUDE_CODE',
          },
        ],
      });
    });

    expect(result.current.credentials?.providers[0]?.apiBaseUrl).toBe('https://new-api.example.com');
    expect(result.current.saving).toBe(false);
  });

  it('loading state transitions from true to false', async () => {
    let resolvePromise: (value: unknown) => void;
    const promise = new Promise((resolve) => {
      resolvePromise = resolve;
    });

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockReturnValueOnce({
      ok: true,
      json: () => promise.then(() => mockCredentials),
    });

    const { result } = renderHook(() => useCredentials());
    expect(result.current.loading).toBe(true);

    await act(async () => {
      resolvePromise!(mockCredentials);
    });

    await waitFor(() => expect(result.current.loading).toBe(false));
  });
});
