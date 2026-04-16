import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SettingsPage from '../pages/SettingsPage';

const mockSettings = {
  defaultPermissionMode: 'BYPASS',
  theme: 'dark',
  defaultModel: 'MiniMax-M2.7',
  sidebarFontFamily: 'sans',
  sidebarFontSize: 13,
  chatFontFamily: 'sans',
  chatFontSize: 13,
  referenceSourcePaths: ['/docs/references', '/notes/wiki'],
};

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
      apiType: 'CLAUDE_CODE',
      hasApiKey: true,
    },
  ],
};

const mockMcpConfig = {
  content: '{\n  "mcpServers": {}\n}',
};

function mockFetchImplementation(overrides?: {
  settings?: unknown;
  credentials?: unknown;
  mcpConfig?: unknown;
}) {
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
    const method = init?.method ?? 'GET';

    if (url === '/api/settings' && method === 'GET') {
      return Promise.resolve({ ok: true, json: async () => overrides?.settings ?? mockSettings });
    }

    if (url === '/api/credentials' && method === 'GET') {
      return Promise.resolve({ ok: true, json: async () => overrides?.credentials ?? mockCredentials });
    }

    if (url === '/api/mcp-config' && method === 'GET') {
      return Promise.resolve({ ok: true, json: async () => overrides?.mcpConfig ?? mockMcpConfig });
    }

    if (url === '/api/settings' && method === 'PUT') {
      return Promise.resolve({ ok: true, json: async () => overrides?.settings ?? mockSettings });
    }

    if (url === '/api/credentials' && method === 'PUT') {
      return Promise.resolve({ ok: true, json: async () => overrides?.credentials ?? mockCredentials });
    }

    if (url === '/api/mcp-config' && method === 'PUT') {
      return Promise.resolve({ ok: true, json: async () => overrides?.mcpConfig ?? mockMcpConfig });
    }

    return Promise.resolve({ ok: true, json: async () => ({}) });
  });
}

function renderPage() {
  return render(
    <MemoryRouter>
      <SettingsPage />
    </MemoryRouter>,
  );
}

function renderEmbeddedPage() {
  return render(
    <MemoryRouter>
      <SettingsPage embedded />
    </MemoryRouter>,
  );
}

describe('SettingsPage', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    mockFetchImplementation();
  });

  it('defaults to the general tab and hides provider content', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByTestId('settings-form')).toBeTruthy());

    expect(screen.getByTestId('settings-tab-general').getAttribute('aria-selected')).toBe('true');
    expect(screen.getByTestId('settings-tabpanel-general')).toBeTruthy();
    expect(screen.queryByTestId('credentials-form')).toBeNull();
    expect(screen.queryByTestId('mcp-config-form')).toBeNull();
    expect((screen.getByTestId('model-input') as HTMLInputElement).value).toBe('MiniMax-M2.7');
  });

  it('saves settings via PUT /api/settings from the general tab', async () => {
    const updated = { ...mockSettings, theme: 'light', sidebarFontFamily: 'mono', sidebarFontSize: 16, chatFontSize: 15 };
    mockFetchImplementation({ settings: updated });

    renderPage();
    await waitFor(() => screen.getByTestId('settings-form'));

    fireEvent.change(screen.getByTestId('theme-select'), { target: { value: 'light' } });
    fireEvent.change(screen.getByTestId('sidebar-font-family-select'), { target: { value: 'mono' } });
    fireEvent.change(screen.getByTestId('sidebar-font-size-select'), { target: { value: '16' } });
    fireEvent.change(screen.getByTestId('chat-font-size-select'), { target: { value: '15' } });
    fireEvent.submit(screen.getByTestId('settings-form'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
      const putCall = calls.find(
        (c: unknown[]) => Array.isArray(c) && c[0] === '/api/settings' && c[1] && (c[1] as RequestInit).method === 'PUT',
      );
      expect(putCall).toBeTruthy();
      const body = JSON.parse(((putCall?.[1] as RequestInit).body as string) ?? '{}');
      expect(body.sidebarFontFamily).toBe('mono');
      expect(body.sidebarFontSize).toBe(16);
      expect(body.chatFontSize).toBe(15);
    });
  });

  it('renders sidebar and chat font controls in the general tab', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByTestId('settings-form')).toBeTruthy());

    expect(screen.getByTestId('sidebar-font-settings-group')).toBeTruthy();
    expect(screen.getByTestId('chat-font-settings-group')).toBeTruthy();
    expect(screen.getByTestId('reference-source-paths-group')).toBeTruthy();
    expect((screen.getByTestId('sidebar-font-family-select') as HTMLSelectElement).value).toBe('sans');
    expect((screen.getByTestId('chat-font-size-select') as HTMLSelectElement).value).toBe('13');
    expect((screen.getByTestId('reference-source-path-input-0') as HTMLInputElement).value).toBe('/docs/references');
  });

  it('saves normalized reference source paths via PUT /api/settings', async () => {
    renderPage();
    await waitFor(() => screen.getByTestId('settings-form'));

    fireEvent.change(screen.getByTestId('reference-source-path-input-0'), { target: { value: '  /docs/references  ' } });
    fireEvent.click(screen.getByTestId('add-reference-source-path-btn'));
    fireEvent.change(screen.getByTestId('reference-source-path-input-1'), { target: { value: '/notes/wiki' } });
    fireEvent.click(screen.getByTestId('add-reference-source-path-btn'));
    fireEvent.change(screen.getByTestId('reference-source-path-input-2'), { target: { value: '/docs/references' } });
    fireEvent.submit(screen.getByTestId('settings-form'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
      const putCall = calls.find(
        (c: unknown[]) => Array.isArray(c) && c[0] === '/api/settings' && c[1] && (c[1] as RequestInit).method === 'PUT',
      );
      expect(putCall).toBeTruthy();
      const body = JSON.parse(((putCall?.[1] as RequestInit).body as string) ?? '{}');
      expect(body.referenceSourcePaths).toEqual(['/docs/references', '/notes/wiki']);
    });
  });

  it('removes a reference source path before saving', async () => {
    renderPage();
    await waitFor(() => screen.getByTestId('settings-form'));

    fireEvent.click(screen.getByTestId('remove-reference-source-path-btn-0'));
    expect((screen.getByTestId('reference-source-path-input-0') as HTMLInputElement).value).toBe('/notes/wiki');
  });

  it('shows error state on settings fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementationOnce(() => Promise.resolve({
      ok: false,
      status: 500,
      json: async () => ({}),
    }));

    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/API GET \/api\/settings failed/)).toBeTruthy(),
    );
  });

  it('renders embedded settings content without the page title', async () => {
    renderEmbeddedPage();

    await waitFor(() => expect(screen.getByTestId('settings-embedded')).toBeTruthy());
    expect(screen.queryByRole('heading', { name: 'Settings' })).toBeNull();
    expect(screen.getByTestId('settings-tabs')).toBeTruthy();
    expect(screen.getByTestId('settings-section-select')).toBeTruthy();
    expect(screen.getByTestId('settings-form')).toBeTruthy();
  });

  it('renders both desktop and mobile navigation controls for settings sections', async () => {
    renderPage();

    await waitFor(() => expect(screen.getByTestId('settings-tabs')).toBeTruthy());

    expect(screen.getByTestId('settings-tab-general').getAttribute('aria-selected')).toBe('true');
    expect((screen.getByTestId('settings-section-select') as HTMLSelectElement).value).toBe('general');
  });

  it('switches to the providers tab and renders provider configuration', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-providers')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-providers'));

    await waitFor(() => expect(screen.getByTestId('credentials-form')).toBeTruthy());
    expect(screen.getByTestId('settings-tab-providers').getAttribute('aria-selected')).toBe('true');
    expect(screen.getByTestId('settings-tabpanel-providers')).toBeTruthy();
    expect((screen.getByTestId('default-provider-select') as HTMLSelectElement).value).toBe('provider-1');
    expect((screen.getByTestId('provider-name-input-provider-1') as HTMLInputElement).value).toBe('MiniMax');
    expect(screen.queryByTestId('settings-form')).toBeNull();
  });

  it('displays masked API key value and can show password input on the providers tab', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-providers')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-providers'));
    await waitFor(() => expect(screen.getByText('****abcd')).toBeTruthy());

    fireEvent.click(screen.getByTestId('change-api-key-btn-provider-1'));

    await waitFor(() => expect(screen.getByTestId('api-key-input-provider-1')).toBeTruthy());
    expect((screen.getByTestId('api-key-input-provider-1') as HTMLInputElement).type).toBe('password');
  });

  it('can add and remove a provider before saving', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-providers')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-providers'));
    await waitFor(() => expect(screen.getByTestId('add-provider-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('add-provider-btn'));
    expect(screen.getAllByTestId(/provider-card-/)).toHaveLength(2);

    fireEvent.click(screen.getByTestId('remove-provider-btn-provider-2'));
    expect(screen.getAllByTestId(/provider-card-/)).toHaveLength(1);
  });

  it('saves multiple providers payload from the providers tab', async () => {
    const updatedCredentials = {
      defaultProviderId: 'provider-2',
      providers: [
        ...mockCredentials.providers,
        {
          id: 'provider-2',
          name: 'OpenAI',
          apiKeyMasked: '****wxyz',
          apiBaseUrl: 'https://api.openai.com/v1',
          models: [{ id: 'gpt-4o', contextWindow: 64000 }],
          apiType: 'OPENAI',
          hasApiKey: true,
        },
      ],
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      const method = init?.method ?? 'GET';

      if (url === '/api/settings' && method === 'GET') {
        return Promise.resolve({ ok: true, json: async () => mockSettings });
      }
      if (url === '/api/credentials' && method === 'GET') {
        return Promise.resolve({ ok: true, json: async () => mockCredentials });
      }
      if (url === '/api/credentials' && method === 'PUT') {
        return Promise.resolve({ ok: true, json: async () => updatedCredentials });
      }
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-providers')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-providers'));
    await waitFor(() => expect(screen.getByTestId('add-provider-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('add-provider-btn'));
    fireEvent.change(screen.getByTestId('provider-name-input-provider-2'), { target: { value: 'OpenAI' } });
    fireEvent.change(screen.getByTestId('provider-base-url-input-provider-2'), { target: { value: 'https://api.openai.com/v1' } });
    fireEvent.click(screen.getByTestId('add-provider-model-btn-provider-2'));
    fireEvent.change(screen.getByTestId('provider-model-id-input-provider-2-0'), { target: { value: 'gpt-4o' } });
    fireEvent.change(screen.getByTestId('provider-model-context-window-input-provider-2-0'), { target: { value: '64000' } });
    fireEvent.change(screen.getByTestId('provider-api-type-input-provider-2'), { target: { value: 'OPENAI' } });
    fireEvent.change(screen.getByTestId('default-provider-select'), { target: { value: 'provider-2' } });
    fireEvent.submit(screen.getByTestId('credentials-form'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
      const putCall = calls.find(
        (c: unknown[]) => Array.isArray(c) && c[0] === '/api/credentials' && c[1] && (c[1] as RequestInit).method === 'PUT',
      );
      expect(putCall).toBeTruthy();
      expect((putCall?.[1] as RequestInit).body).toBe(JSON.stringify({
        defaultProviderId: 'provider-2',
        providers: [
          {
            id: 'provider-1',
            name: 'MiniMax',
            apiBaseUrl: 'https://api.example.com',
            models: [
              { id: 'MiniMax-M2.7', contextWindow: 128000 },
              { id: 'MiniMax-M2.5', contextWindow: 256000 },
            ],
            apiType: 'CLAUDE_CODE',
          },
          {
            id: 'provider-2',
            name: 'OpenAI',
            apiBaseUrl: 'https://api.openai.com/v1',
            models: [{ id: 'gpt-4o', contextWindow: 64000 }],
            apiType: 'OPENAI',
          },
        ],
      }));
    });
  });

  it('adds model rows with default context window and normalizes invalid values before save', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-providers')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-providers'));
    await waitFor(() => expect(screen.getByTestId('add-provider-model-btn-provider-1')).toBeTruthy());

    fireEvent.click(screen.getByTestId('add-provider-model-btn-provider-1'));
    expect((screen.getByTestId('provider-model-context-window-input-provider-1-2') as HTMLInputElement).value).toBe('128000');

    fireEvent.change(screen.getByTestId('provider-model-id-input-provider-1-2'), { target: { value: 'MiniMax-M2.1' } });
    fireEvent.change(screen.getByTestId('provider-model-context-window-input-provider-1-2'), { target: { value: '' } });
    fireEvent.submit(screen.getByTestId('credentials-form'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
      const putCall = calls.find(
        (c: unknown[]) => Array.isArray(c) && c[0] === '/api/credentials' && c[1] && (c[1] as RequestInit).method === 'PUT',
      );
      expect(putCall).toBeTruthy();
      expect((putCall?.[1] as RequestInit).body).toContain('"contextWindow":128000');
    });
  });

  it('loads MCP config when opening the MCP tab', async () => {
    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-mcp')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-mcp'));

    await waitFor(() => expect(screen.getByTestId('mcp-config-form')).toBeTruthy());
    expect((screen.getByTestId('mcp-config-textarea') as HTMLTextAreaElement).value).toContain('mcpServers');
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/mcp-config', expect.anything());
  });

  it('saves MCP config from the MCP tab', async () => {
    const updatedMcp = {
      content: '{\n  "mcpServers": {\n    "filesystem": {\n      "command": "npx"\n    }\n  }\n}',
    };
    mockFetchImplementation({ mcpConfig: updatedMcp });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('settings-tab-mcp')).toBeTruthy());

    fireEvent.click(screen.getByTestId('settings-tab-mcp'));
    await waitFor(() => expect(screen.getByTestId('mcp-config-form')).toBeTruthy());

    fireEvent.change(screen.getByTestId('mcp-config-textarea'), { target: { value: updatedMcp.content } });
    fireEvent.submit(screen.getByTestId('mcp-config-form'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
      const putCall = calls.find(
        (c: unknown[]) => Array.isArray(c) && c[0] === '/api/mcp-config' && c[1] && (c[1] as RequestInit).method === 'PUT',
      );
      expect(putCall).toBeTruthy();
      expect((putCall?.[1] as RequestInit).body).toBe(JSON.stringify(updatedMcp));
    });
  });
});
