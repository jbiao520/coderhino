import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useMcpConfig } from '../hooks/useMcpConfig';

const mockConfig = {
  content: '{\n  "mcpServers": {}\n}',
};

describe('useMcpConfig', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetches MCP config when enabled', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockConfig,
    });

    const { result } = renderHook(() => useMcpConfig());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.config?.content).toContain('mcpServers');
    expect(result.current.error).toBeNull();
  });

  it('waits to fetch MCP config until enabled', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockConfig,
    });

    const { result, rerender } = renderHook(({ enabled }) => useMcpConfig(enabled), {
      initialProps: { enabled: false },
    });

    expect(globalThis.fetch).not.toHaveBeenCalled();

    rerender({ enabled: true });

    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/mcp-config', expect.anything());
  });

  it('saveConfig calls PUT /api/mcp-config and updates state', async () => {
    const updatedConfig = {
      content: '{\n  "mcpServers": {\n    "filesystem": {}\n  }\n}',
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockConfig })
      .mockResolvedValueOnce({ ok: true, json: async () => updatedConfig });

    const { result } = renderHook(() => useMcpConfig());
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.saveConfig(updatedConfig);
    });

    expect(result.current.config?.content).toContain('filesystem');
    expect(result.current.saving).toBe(false);
  });
});
