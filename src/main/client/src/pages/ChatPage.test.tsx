import { render, screen, fireEvent, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { Terminal as MockTerminal } from 'xterm';
import ChatPage from '../pages/ChatPage';
import { MultiProjectProvider } from '../context/MultiProjectContext';
import type { WebSettings } from '../types/api';

const mockTerminalClass = MockTerminal as unknown as {
  reset: () => void;
  instances: Array<{
    write: ReturnType<typeof vi.fn>;
    focus: ReturnType<typeof vi.fn>;
    emitData: (data: string) => void;
  }>;
};

const mockSession = {
  sessionId: 'ses-abc',
  createdAt: '2026-04-07T10:00:00Z',
  updatedAt: '2026-04-07T10:05:00Z',
  status: 'ACTIVE',
  activeRun: null,
  messages: [],
  providerId: 'provider-1',
  model: 'MiniMax-M2.7',
  permissionMode: 'BYPASS',
  planMode: false,
  buildMode: true,
  availableProviders: [
    {
      id: 'provider-1',
      name: 'Anthropic',
      models: ['MiniMax-M2.7', 'MiniMax-M2.5', 'MiniMax-M2.1'],
      modelOptions: [
        { id: 'MiniMax-M2.7', label: 'MiniMax-M2.7', modelModeSupported: true, availableModelModes: ['default', 'think'] },
        { id: 'MiniMax-M2.5', label: 'MiniMax-M2.5', modelModeSupported: true, availableModelModes: ['default', 'think'] },
        { id: 'MiniMax-M2.1', label: 'MiniMax-M2.1', modelModeSupported: false, availableModelModes: [] },
      ],
      unavailable: false,
    },
    {
      id: 'provider-2',
      name: 'OpenAI',
      models: ['gpt-4o', 'gpt-4.1'],
      modelOptions: [
        { id: 'gpt-4o', label: 'gpt-4o', modelModeSupported: false, availableModelModes: [] },
        { id: 'gpt-4.1', label: 'gpt-4.1', modelModeSupported: false, availableModelModes: [] },
      ],
      unavailable: false,
    },
  ],
  availableModels: ['MiniMax-M2.7', 'MiniMax-M2.5', 'MiniMax-M2.1'],
  modelModeSupported: true,
  availableModelModes: ['default', 'think'],
  modelMode: 'default',
  worktreeId: 'default',
  worktree: {
    id: 'default',
    name: 'default',
    path: '/tmp/proj-1',
    defaultWorktree: true,
    managed: false,
    createdAt: '2026-04-07T10:00:00Z',
  },
};

const mockReferences = {
  references: [
    { id: 'api-guidelines', label: 'Api Guidelines', markdown: '# API Guidelines\n\nKeep contracts stable.' },
    { id: 'bug-investigation', label: 'Bug Investigation', markdown: '# Bug Investigation\n\n1. Reproduce.' },
  ],
};

class MockAudio {
  static instances: MockAudio[] = [];

  src: string;
  currentTime = 0;
  ended = false;
  paused = true;
  private listeners: Record<string, Array<() => void>> = {};

  constructor(src: string) {
    this.src = src;
    MockAudio.instances.push(this);
  }

  addEventListener(type: string, listener: () => void) {
    this.listeners[type] ??= [];
    this.listeners[type]!.push(listener);
  }

  removeEventListener(type: string, listener: () => void) {
    this.listeners[type] = (this.listeners[type] ?? []).filter((candidate) => candidate !== listener);
  }

  play = vi.fn(async () => {
    this.paused = false;
    this.emit('playing');
  });

  pause = vi.fn(() => {
    this.paused = true;
    this.emit('pause');
  });

  emit(type: string) {
    for (const listener of this.listeners[type] ?? []) {
      listener();
    }
  }

  static reset() {
    MockAudio.instances = [];
  }
}

function mockChatFetch(overrides: Record<string, unknown> = {}) {
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
    if (url in overrides) {
      return Promise.resolve({ ok: true, json: async () => overrides[url]! });
    }
    if (url === '/api/projects/proj-1') {
      return Promise.resolve({
        ok: true,
        json: async () => ({
          id: 'proj-1',
          name: 'Project One',
          path: '/tmp/proj-1',
          lastOpened: '2026-04-07T10:00:00Z',
          createdAt: '2026-04-07T10:00:00Z',
        }),
      });
    }
    if (url === '/api/sessions?projectId=proj-1') {
      return Promise.resolve({
        ok: true,
        json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }),
      });
    }
    if (url === '/api/sessions/ses-abc' && !init?.method) {
      return Promise.resolve({
        ok: true,
        json: async () => ({ ...mockSession, projectId: 'proj-1' }),
      });
    }
    if (url === '/api/sessions/ses-abc/runs' && init?.method === 'POST') {
      return Promise.resolve({
        ok: true,
        json: async () => ({ runId: 'run-default', status: 'RUNNING', visiblePrompt: 'hello' }),
      });
    }
    if (url === '/api/commands') {
      return Promise.resolve({
        ok: true,
        json: async () => ([
          { name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false },
          { name: 'vim', description: 'Vim mode', aliases: [], webCompatible: false, promptBacked: false },
        ]),
      });
    }
    if (url === '/api/references') {
      return Promise.resolve({
        ok: true,
        json: async () => mockReferences,
      });
    }
      if (url === '/api/commands/execute' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ prompt: '/status', output: 'Everything is healthy', success: true, commandName: 'status' }),
        });
      }
    if (url.startsWith('/api/sessions/ses-abc/runs/') && init?.method === 'DELETE') {
      return Promise.resolve({ ok: true, json: async () => ({}) });
    }
    if (url.startsWith('/api/sessions/ses-abc/runs/') && url.endsWith('/answer') && init?.method === 'POST') {
      return Promise.resolve({ ok: true, json: async () => ({ runId: 'run-default', status: 'RUNNING' }) });
    }

    return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
  });
}

type EventHandler = (event: MessageEvent) => void;

class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  onerror: (() => void) | null = null;
  private listeners: Record<string, EventHandler[]> = {};

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(type: string, handler: EventHandler) {
    if (!this.listeners[type]) this.listeners[type] = [];
    this.listeners[type].push(handler);
  }

  emit(type: string, data: string) {
    const handlers = this.listeners[type] ?? [];
    for (const h of handlers) {
      h(new MessageEvent(type, { data }));
    }
  }

  close() {}

  static reset() {
    MockEventSource.instances = [];
  }
}

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  static OPEN = 1;
  static CLOSED = 3;
  url: string;
  readyState = MockWebSocket.OPEN;
  private listeners: Record<string, Array<(event: MessageEvent | Event) => void>> = {};

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
    queueMicrotask(() => this.emit('open', new Event('open')));
  }

  addEventListener(type: string, listener: (event: MessageEvent | Event) => void) {
    this.listeners[type] ??= [];
    this.listeners[type]!.push(listener);
  }

  send = vi.fn();

  close() {
    this.readyState = MockWebSocket.CLOSED;
  }

  emit(type: string, event: MessageEvent | Event) {
    for (const listener of this.listeners[type] ?? []) {
      listener(event);
    }
  }

  static reset() {
    MockWebSocket.instances = [];
  }
}

function renderPage(
  sessionId = 'ses-abc',
  props: Partial<React.ComponentProps<typeof ChatPage>> = {},
) {
  return render(
    <MultiProjectProvider>
      <MemoryRouter initialEntries={[`/projects/proj-1/sessions/${sessionId}`]}>
        <Routes>
          <Route path="/projects/:projectId/sessions/:id" element={<ChatPage {...props} />} />
        </Routes>
      </MemoryRouter>
    </MultiProjectProvider>,
  );
}

const mockSettings: WebSettings = {
  defaultPermissionMode: 'BYPASS',
  theme: 'dark',
  defaultModel: 'MiniMax-M2.7',
  sidebarFontFamily: 'sans',
  sidebarFontSize: 13,
  chatFontFamily: 'mono',
  chatFontSize: 16,
};

function seedProjectState(projectId = 'proj-1', sessionId = 'ses-abc') {
  localStorage.setItem(
    'coderhino-multi-project',
    JSON.stringify({
      openProjectIds: [projectId],
      lastActiveSessionByProject: { [projectId]: sessionId },
      recentSessionOrder: [sessionId],
      activeProjectId: projectId,
    }),
  );
}

function setMessagesAreaScrollMetrics(
  element: HTMLElement,
  metrics: { scrollTop: number; clientHeight: number; scrollHeight: number },
) {
  Object.defineProperty(element, 'scrollTop', {
    configurable: true,
    value: metrics.scrollTop,
    writable: true,
  });
  Object.defineProperty(element, 'clientHeight', {
    configurable: true,
    value: metrics.clientHeight,
  });
  Object.defineProperty(element, 'scrollHeight', {
    configurable: true,
    value: metrics.scrollHeight,
  });
}

describe('ChatPage', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    MockEventSource.reset();
    MockWebSocket.reset();
    MockAudio.reset();
    mockTerminalClass.reset();
    (globalThis as Record<string, unknown>)['navigator'] = {
      clipboard: {
        writeText: vi.fn().mockResolvedValue(undefined),
      },
    };
    (globalThis as Record<string, unknown>)['EventSource'] = MockEventSource;
    (globalThis as Record<string, unknown>)['WebSocket'] = MockWebSocket;
    (globalThis as Record<string, unknown>)['ResizeObserver'] = class {
      observe() {}
      disconnect() {}
    };
    (globalThis as Record<string, unknown>)['Audio'] = MockAudio;
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
    const store = new Map<string, string>();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => {
          store.set(key, value);
        },
        removeItem: (key: string) => {
          store.delete(key);
        },
        clear: () => {
          store.clear();
        },
      },
    });
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('fetches and displays session on mount', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage('ses-abc');
    await waitFor(() => expect(screen.getByText('New Session')).toBeTruthy());
    expect(screen.getByTestId('messages-area')).toBeTruthy();
    expect(screen.queryByTestId('chatpage-new-session-btn')).toBeNull();
  });

  it('shows empty messages state when no messages and no live text', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() =>
      expect(screen.getByText('No messages yet in this session.')).toBeTruthy(),
    );
  });

  it('shows loading state then resolves', async () => {
    seedProjectState();
    let resolvePromise!: (value: Response | PromiseLike<Response>) => void;
    mockChatFetch();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementationOnce(
      () => new Promise((res) => {
        resolvePromise = res;
      }) as Promise<Response>,
    );

    renderPage();
    expect(screen.getByText('Loading session…')).toBeTruthy();

    resolvePromise({ ok: true, json: async () => mockSession } as Response);
    await waitFor(() => expect(screen.queryByText('Loading session…')).toBeNull());
  });

  it('shows error state on failed fetch', async () => {
    seedProjectState();
    mockChatFetch();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-missing' && !init?.method) {
        return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      }
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'proj-1',
            name: 'Project One',
            path: '/tmp/proj-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [mockSession.worktree],
          }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage('ses-missing');
    await waitFor(() =>
      expect(screen.getByText(/API GET \/api\/sessions\/ses-missing failed/)).toBeTruthy(),
    );
  });

  it('re-fetches on remount (session restore after refresh)', async () => {
    seedProjectState();
    let status = 'ACTIVE';
    mockChatFetch();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'proj-1',
            name: 'Project One',
            path: '/tmp/proj-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
          }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1', status }) });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    const { unmount } = renderPage('ses-abc');
    await waitFor(() => expect(screen.getByText('ACTIVE')).toBeTruthy());

    unmount();
    status = 'COMPLETED';

    renderPage('ses-abc');
    await waitFor(() => expect(screen.getByText('COMPLETED')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.filter(([url, init]) => url === '/api/sessions/ses-abc' && !init?.method)).toHaveLength(2);
  });

  it('renders composer form with inline send button and bottom toolbar', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer')).toBeTruthy());
    const composer = screen.getByTestId('composer');
    const inputRow = screen.getByTestId('composer-input-row');
    const toolbar = screen.getByTestId('composer-toolbar');
    const input = screen.getByTestId('message-input');
    const sendBtn = screen.getByTestId('send-btn');

    expect(inputRow).toBeTruthy();
    expect(toolbar).toBeTruthy();
    expect(screen.getByTestId('composer-intent-trigger')).toBeTruthy();
    expect(screen.getByTestId('composer-provider-model-trigger')).toBeTruthy();
    expect(screen.getByTestId('composer-model-mode-trigger')).toBeTruthy();
    expect(screen.getByTestId('composer-reference-trigger')).toBeTruthy();
    expect(input).toBeTruthy();
    expect(sendBtn).toBeTruthy();
    expect(sendBtn.querySelector('svg')).toBeTruthy();
    expect(sendBtn.getAttribute('aria-label')).toBe('Send message');
    expect(composer.firstElementChild).toBe(inputRow);
    expect(inputRow.contains(input)).toBe(true);
    expect(inputRow.contains(sendBtn)).toBe(true);
    expect(composer.lastElementChild).toBe(toolbar);
  });

  it('initializes composer model from resolved session model value', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        model: 'MiniMax-M2.1',
        availableModels: ['MiniMax-M2.1'],
        availableProviders: [
          {
            id: 'provider-1',
            name: 'Anthropic',
            models: ['MiniMax-M2.1'],
            modelOptions: [
              { id: 'MiniMax-M2.1', label: 'MiniMax-M2.1', modelModeSupported: false, availableModelModes: [] },
            ],
            unavailable: false,
          },
        ],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          ...mockSession,
          projectId: 'proj-1',
          model: 'MiniMax-M2.1',
          availableModels: ['MiniMax-M2.1'],
          availableProviders: [
            {
              id: 'provider-1',
              name: 'Anthropic',
              models: ['MiniMax-M2.1'],
              modelOptions: [
                { id: 'MiniMax-M2.1', label: 'MiniMax-M2.1', modelModeSupported: false, availableModelModes: [] },
              ],
              unavailable: false,
            },
          ],
        }],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-provider-model-trigger')).toBeTruthy());
    expect(screen.getByTestId('composer-provider-model-trigger').textContent).toContain('MiniMax-M2.1');
  });

  it('submits message and shows user message in list', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-1', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'Hello Agent' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => expect(screen.getByText('Hello Agent')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('run-start-placeholder')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    const submitCall = calls.find(([url, init]) => url === '/api/sessions/ses-abc/runs' && init?.method === 'POST');
    expect(submitCall).toBeTruthy();
    expect(submitCall?.[1]?.body).toBe(JSON.stringify({
      message: 'Hello Agent',
      model: 'MiniMax-M2.7',
      providerId: 'provider-1',
      buildMode: true,
      planMode: false,
      modelMode: 'default',
    }));
  });

  it('renders user messages as literal text while assistant messages keep rich formatting', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          {
            type: 'user',
            content: '# Heading\n- literal list\n`literal code`',
            timestamp: '2026-04-07T10:01:00Z',
            rollbackIndex: 0,
          },
          {
            type: 'assistant',
            content: '# Rendered heading\n- formatted list\n`inline code`',
            timestamp: '2026-04-07T10:01:05Z',
            rollbackIndex: null,
          },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('user-message-content-0')).toBeTruthy());

    expect(screen.getByTestId('user-message-content-0').textContent).toBe('# Heading\n- literal list\n`literal code`');
    expect(screen.getByTestId('user-message-frame-0').querySelector('[data-testid="rich-message-content"]')).toBeNull();
    expect(screen.getByTestId('assistant-message-frame-1').querySelector('[data-testid="rich-message-content"]')).toBeTruthy();
    expect(screen.getByRole('heading', { name: 'Rendered heading' })).toBeTruthy();
  });

  it('submits selected composer toolbar values', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-provider-model-trigger')).toBeTruthy());

    fireEvent.click(screen.getByTestId('composer-provider-model-trigger'));
    await waitFor(() => expect(screen.getByTestId('composer-model-option-provider-1-minimax-m2-5')).toBeTruthy());
    fireEvent.click(screen.getByTestId('composer-model-option-provider-1-minimax-m2-5'));
    fireEvent.click(screen.getByTestId('composer-intent-trigger'));
    fireEvent.click(screen.getByTestId('composer-intent-option-plan'));
    fireEvent.click(screen.getByTestId('composer-model-mode-trigger'));
    fireEvent.click(screen.getByTestId('composer-model-mode-option-think'));
    fireEvent.change(screen.getByTestId('message-input'), { target: { value: 'Configured run' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    const submitCall = calls.find(([url, init]) => url === '/api/sessions/ses-abc/runs' && init?.method === 'POST');
    expect(submitCall?.[1]?.body).toBe(JSON.stringify({
      message: 'Configured run',
      model: 'MiniMax-M2.5',
      providerId: 'provider-1',
      buildMode: false,
      planMode: true,
      modelMode: 'think',
    }));
  });

  it('switching provider updates model options', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-provider-model-trigger')).toBeTruthy());

    fireEvent.click(screen.getByTestId('composer-provider-model-trigger'));
    fireEvent.click(screen.getByTestId('composer-provider-option-provider-2'));

    await waitFor(() => {
      expect(screen.getByTestId('composer-model-option-provider-2-gpt-4o')).toBeTruthy();
      expect(screen.getByTestId('composer-model-option-provider-2-gpt-4-1')).toBeTruthy();
    });
  });

  it('hides model mode when selected model does not support it', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-model-mode-trigger')).toBeTruthy());

    fireEvent.click(screen.getByTestId('composer-provider-model-trigger'));
    await waitFor(() => expect(screen.getByTestId('composer-model-option-provider-1-minimax-m2-1')).toBeTruthy());
    fireEvent.click(screen.getByTestId('composer-model-option-provider-1-minimax-m2-1'));

    await waitFor(() => expect(screen.queryByTestId('composer-model-mode-trigger')).toBeNull());
  });

  it('resets invalid model mode when switching to a model without support', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-model-mode-trigger')).toBeTruthy());

    fireEvent.click(screen.getByTestId('composer-model-mode-trigger'));
    fireEvent.click(screen.getByTestId('composer-model-mode-option-think'));
    fireEvent.click(screen.getByTestId('composer-provider-model-trigger'));
    fireEvent.click(screen.getByTestId('composer-provider-option-provider-2'));
    await waitFor(() => expect(screen.getByTestId('composer-model-option-provider-2-gpt-4o')).toBeTruthy());
    fireEvent.click(screen.getByTestId('composer-model-option-provider-2-gpt-4o'));

    await waitFor(() => expect(screen.queryByTestId('composer-model-mode-trigger')).toBeNull());
  });

  it('loads available references into the composer menu', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('composer-reference-trigger')).toBeTruthy());

    fireEvent.click(screen.getByTestId('composer-reference-trigger'));

    await waitFor(() => expect(screen.getByTestId('composer-reference-menu')).toBeTruthy());
    expect(screen.getByTestId('composer-reference-option-api-guidelines')).toBeTruthy();
    expect(screen.getByTestId('composer-reference-option-bug-investigation')).toBeTruthy();
  });

  it('inserts selected reference markdown at the current cursor position and restores focus', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'Hello world', selectionStart: 6, selectionEnd: 6 } });
    input.focus();
    input.setSelectionRange(6, 6);

    fireEvent.click(screen.getByTestId('composer-reference-trigger'));
    await waitFor(() => expect(screen.getByTestId('composer-reference-option-api-guidelines')).toBeTruthy());
    fireEvent.click(screen.getByTestId('composer-reference-option-api-guidelines'));

    await waitFor(() => expect(input.value).toBe('Hello # API Guidelines\n\nKeep contracts stable.world'));
    await waitFor(() => expect(document.activeElement).toBe(input));
    await waitFor(() => expect(input.selectionStart).toBe('Hello # API Guidelines\n\nKeep contracts stable.'.length));
    await waitFor(() => expect(input.selectionEnd).toBe('Hello # API Guidelines\n\nKeep contracts stable.'.length));
    expect(screen.queryByTestId('composer-reference-menu')).toBeNull();
  });

  it('replaces the current selection when inserting a reference', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'Hello world', selectionStart: 6, selectionEnd: 11 } });
    input.focus();
    input.setSelectionRange(6, 11);

    fireEvent.click(screen.getByTestId('composer-reference-trigger'));
    await waitFor(() => expect(screen.getByTestId('composer-reference-option-bug-investigation')).toBeTruthy());
    fireEvent.click(screen.getByTestId('composer-reference-option-bug-investigation'));

    await waitFor(() => expect(input.value).toBe('Hello # Bug Investigation\n\n1. Reproduce.'));
  });

  it('shows cancel button while run is active', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-2', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'do something' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => expect(screen.getByTestId('cancel-btn')).toBeTruthy());
  });

  it('disables textarea while run is active', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-3', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'task' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => {
      expect((screen.getByTestId('message-input') as HTMLTextAreaElement).disabled).toBe(true);
    });
  });

  it('navigates submitted prompts with ArrowUp and ArrowDown', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-history', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    fireEvent.change(input, { target: { value: 'first prompt' } });
    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });
    act(() => {
      es.emit('completed', JSON.stringify({ runId: 'run-history', finalText: 'done one' }));
    });
    await waitFor(() => expect(input.disabled).toBe(false));

    fireEvent.change(input, { target: { value: 'second prompt' } });
    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });
    act(() => {
      es.emit('completed', JSON.stringify({ runId: 'run-history', finalText: 'done two' }));
    });
    await waitFor(() => expect(input.disabled).toBe(false));

    fireEvent.keyDown(input, { key: 'ArrowUp' });
    await waitFor(() => expect(input.value).toBe('second prompt'));

    fireEvent.keyDown(input, { key: 'ArrowUp' });
    await waitFor(() => expect(input.value).toBe('first prompt'));

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    await waitFor(() => expect(input.value).toBe('second prompt'));
  });

  it('restores the unsent draft when leaving history navigation', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-history-draft', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    fireEvent.change(input, { target: { value: 'saved prompt' } });
    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });
    act(() => {
      es.emit('completed', JSON.stringify({ runId: 'run-history-draft', finalText: 'done' }));
    });
    await waitFor(() => expect(input.disabled).toBe(false));

    fireEvent.change(input, { target: { value: 'working draft' } });
    fireEvent.keyDown(input, { key: 'ArrowUp' });
    await waitFor(() => expect(input.value).toBe('saved prompt'));

    fireEvent.keyDown(input, { key: 'ArrowDown' });
    await waitFor(() => expect(input.value).toBe('working draft'));
  });

  it('shows live streaming output as text-chunk events arrive', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('status', JSON.stringify({}));
    });

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Streaming response' }));
    });

    await waitFor(() => expect(screen.getByTestId('live-output')).toBeTruthy());
    expect(screen.getByText('Streaming response')).toBeTruthy();
    expect((screen.getByTestId('live-output') as HTMLDivElement).style.alignItems).toBe('flex-start');
    expect(screen.getByTestId('messages-area').textContent).not.toContain('Claude');
  });

  it('auto-scrolls streamed updates when the transcript is already near the bottom', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const messagesArea = screen.getByTestId('messages-area');
    const scrollIntoViewSpy = vi.mocked(window.HTMLElement.prototype.scrollIntoView);
    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    setMessagesAreaScrollMetrics(messagesArea, { scrollTop: 560, clientHeight: 400, scrollHeight: 1000 });
    fireEvent.scroll(messagesArea);
    scrollIntoViewSpy.mockClear();

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Follow bottom' }));
    });

    await waitFor(() => expect(scrollIntoViewSpy).toHaveBeenCalled());
  });

  it('preserves manual upward scrolling until the user returns near the bottom', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const messagesArea = screen.getByTestId('messages-area');
    const scrollIntoViewSpy = vi.mocked(window.HTMLElement.prototype.scrollIntoView);
    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    setMessagesAreaScrollMetrics(messagesArea, { scrollTop: 500, clientHeight: 400, scrollHeight: 1000 });
    fireEvent.scroll(messagesArea);
    scrollIntoViewSpy.mockClear();

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Do not yank viewport' }));
    });

    await waitFor(() => expect(screen.getByTestId('live-output')).toBeTruthy());
    expect(scrollIntoViewSpy).not.toHaveBeenCalled();

    setMessagesAreaScrollMetrics(messagesArea, { scrollTop: 560, clientHeight: 400, scrollHeight: 1000 });
    fireEvent.scroll(messagesArea);

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Resume follow' }));
    });

    await waitFor(() => expect(scrollIntoViewSpy).toHaveBeenCalled());
  });

  it('renders structured live output and preserves it after completion', async () => {
    seedProjectState();
    mockChatFetch();
    const structured = [
      '**Proposed Change: Structured Chat Output**',
      'Short overview.',
      '',
      '### Brainstorming & Exploration',
      '- Checked renderer',
      '',
      '### Next Action',
      'Run `/opsx-apply`.',
    ].join('\n');
    const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
    let sessionFetchCount = 0;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        sessionFetchCount += 1;
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockSession,
            projectId: 'proj-1',
            messages: sessionFetchCount > 1
              ? [{ type: 'assistant', content: structured, timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null }]
              : [],
          }),
        });
      }
      return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: structured }));
    });

    await waitFor(() => expect(screen.getByTestId('structured-summary')).toBeTruthy());
    expect(screen.getByText('Show Details')).toBeTruthy();

    act(() => {
      es.emit('completed', JSON.stringify({ finalText: structured }));
    });

    await waitFor(() => expect(screen.queryByTestId('live-output')).toBeNull());
    await waitFor(() => expect(screen.getByTestId('structured-summary')).toBeTruthy());
    expect(screen.getByTestId('structured-summary').textContent).toContain('Proposed Change: Structured Chat Output');
    expect(screen.getByTestId('structured-section-next-action')).toBeTruthy();
  });

  it('expands brainstorming details for structured assistant messages', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          {
            type: 'assistant',
            content: [
              '**Proposed Change: Structured Chat Output**',
              'Short overview.',
              '',
              '### Brainstorming & Exploration',
              '- Checked renderer',
            ].join('\n'),
          },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('brainstorming-toggle')).toBeTruthy());

    expect(screen.queryByTestId('brainstorming-content')).toBeNull();
    fireEvent.click(screen.getByTestId('brainstorming-toggle'));
    expect(screen.getByTestId('brainstorming-content')).toBeTruthy();
    expect(screen.getByText('Checked renderer')).toBeTruthy();
  });

  it('renders tool activity inline when tool-call events arrive', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('tool-call', JSON.stringify({ toolName: 'glob', argumentsJson: '{"pattern":"*.ts"}' }));
    });

    await waitFor(() => expect(screen.getByTestId('inline-tool-block-0')).toBeTruthy());
    expect(screen.queryByTestId('tool-activity-pane')).toBeNull();
  });

  it('keeps inline tool blocks in chronological order and expands details', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Checking files' }));
      es.emit('tool-call', JSON.stringify({ toolName: 'glob', argumentsJson: '{"pattern":"*.ts"}' }));
      es.emit('tool-result', JSON.stringify({ toolName: 'glob', result: 'src/index.ts' }));
      es.emit('text-chunk', JSON.stringify({ chunk: 'Done' }));
    });

    await waitFor(() => expect(screen.getByTestId('inline-tool-block-1')).toBeTruthy());
    expect(screen.getByTestId('run-transcript-text-0').textContent).toContain('Checking files');
    expect(screen.getByTestId('live-output').textContent).toContain('Done');

    fireEvent.click(screen.getByRole('button', { name: /glob/i }));
    expect(screen.getByText('Input')).toBeTruthy();
    expect(screen.getByText(/src\/index\.ts/)).toBeTruthy();
  });

  it('renders thinking and tool-input progress inline in chronological order', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('thinking-delta', JSON.stringify({ thinking: 'Planning steps' }));
      es.emit('tool-input-delta', JSON.stringify({ toolName: 'glob', toolUseId: 'tool-3', partialJson: '{"pattern":"*.java"}' }));
      es.emit('text-chunk', JSON.stringify({ chunk: 'Ready to run.' }));
    });

    await waitFor(() => expect(screen.getByTestId('thinking-block-0')).toBeTruthy());
    expect(screen.getByTestId('thinking-block-0').textContent).toContain('Thinking');
    expect(screen.getByTestId('thinking-block-0').textContent).toContain('Planning steps');
    expect(screen.getByTestId('tool-input-block-1').textContent).toContain('Preparing glob');
    expect(screen.getByTestId('tool-input-block-1').textContent).toContain('{"pattern":"*.java"}');
    expect(screen.getByTestId('live-output').textContent).toContain('Ready to run.');
  });

  it('hides thinking items without hiding assistant text when the thinking toggle is off', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-toggle-thinking', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());
    expect(screen.getByTestId('transcript-controls')).toBeTruthy();

    fireEvent.change(screen.getByTestId('message-input'), { target: { value: 'Start run' } });
    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => expect(screen.getByTestId('cancel-btn')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('thinking-delta', JSON.stringify({ thinking: 'Planning steps' }));
      es.emit('text-chunk', JSON.stringify({ chunk: 'Ready to run.' }));
    });

    await waitFor(() => expect(screen.getByTestId('thinking-block-0')).toBeTruthy());
    fireEvent.click(screen.getByTestId('toggle-thinking-visibility'));

    expect(screen.queryByTestId('thinking-block-0')).toBeNull();
    expect(screen.getByTestId('live-output').textContent).toContain('Ready to run.');
    expect(screen.getByTestId('toggle-thinking-visibility')).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows transcript icon toggles even before any run starts', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('transcript-controls')).toBeTruthy());

    expect(screen.getByTestId('toggle-thinking-visibility')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('toggle-tool-visibility')).toHaveAttribute('aria-pressed', 'true');
  });

  it('hides tool-input and tool activity items without hiding assistant text when the tools toggle is off', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('tool-input-delta', JSON.stringify({ toolName: 'glob', toolUseId: 'tool-3', partialJson: '{"pattern":"*.java"}' }));
      es.emit('tool-call', JSON.stringify({ toolName: 'glob', argumentsJson: '{"pattern":"*.java"}' }));
      es.emit('tool-result', JSON.stringify({ toolName: 'glob', result: 'src/Main.java' }));
      es.emit('text-chunk', JSON.stringify({ chunk: 'Ready to run.' }));
    });

    await waitFor(() => expect(screen.getByTestId('tool-input-block-0')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('inline-tool-block-1')).toBeTruthy());
    fireEvent.click(screen.getByTestId('toggle-tool-visibility'));

    expect(screen.queryByTestId('tool-input-block-0')).toBeNull();
    expect(screen.queryByTestId('inline-tool-block-1')).toBeNull();
    expect(screen.getByTestId('live-output').textContent).toContain('Ready to run.');
    expect(screen.getByTestId('toggle-tool-visibility')).toHaveAttribute('aria-pressed', 'false');
  });

  it('renders retry status activity inline in chronological order', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('status', JSON.stringify({ runId: 'run-retry', status: 'Retrying LLM request: attempt 2 of 5 after service overloaded' }));
      es.emit('text-chunk', JSON.stringify({ runId: 'run-retry', chunk: 'Recovered answer' }));
    });

    await waitFor(() => expect(screen.getByTestId('status-block-0')).toBeTruthy());
    expect(screen.getByTestId('status-block-0').textContent).toContain('Retrying');
    expect(screen.getByTestId('status-block-0').textContent).toContain('attempt 2 of 5');
    expect(screen.getByTestId('live-output').textContent).toContain('Recovered answer');
  });

  it('finalizes live text into message list on completed event', async () => {
    seedProjectState();
    mockChatFetch();
    const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
    let sessionFetchCount = 0;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        sessionFetchCount += 1;
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockSession,
            projectId: 'proj-1',
            messages: sessionFetchCount > 1
              ? [{ type: 'assistant', content: 'Final answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null }]
              : [],
          }),
        });
      }
      return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Final answer' }));
    });
    act(() => {
      es.emit('completed', JSON.stringify({ finalText: 'Final answer' }));
    });

    await waitFor(() => {
      expect(screen.queryByTestId('live-output')).toBeNull();
      expect(screen.getByTestId('assistant-message-0').textContent).toContain('Final answer');
    });
    expect(screen.getByTestId('assistant-message-0').textContent).toContain('Final answer');
  });

  it('renders persisted completed-turn activity after session reload', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          {
            type: 'assistant',
            content: 'Completed answer',
            timestamp: '2026-04-07T10:01:05Z',
            rollbackIndex: null,
            activityTimeline: [
              { kind: 'thinking', content: 'Plan carefully' },
              { kind: 'tool', toolName: 'glob', toolUseId: 'tool-1', argumentsJson: '{"pattern":"*.java"}', output: 'src/Main.java' },
            ],
            fileSummary: {
              totalChanges: 1,
              created: [],
              modified: ['src/Main.java'],
              deleted: [],
            },
          },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('assistant-message-0')).toBeTruthy());

    expect(screen.getByTestId('persisted-activity-0')).toBeTruthy();
    expect(screen.getByTestId('persisted-activity-0-thinking-0').textContent).toContain('Plan carefully');
    expect(screen.getByTestId('persisted-activity-0-tool-1')).toBeTruthy();
    expect(screen.getByTestId('persisted-file-summary-0').textContent).toContain('src/Main.java');
  });

  it('cancels run and hides cancel button', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-4', status: 'RUNNING' } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: 'cancel me' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => expect(screen.getByTestId('cancel-btn')).toBeTruthy());
    expect(screen.getByTestId('run-start-placeholder')).toBeTruthy();

    await act(async () => {
      fireEvent.click(screen.getByTestId('cancel-btn'));
    });

    await waitFor(() => expect(screen.queryByTestId('cancel-btn')).toBeNull());
    expect(screen.queryByTestId('run-start-placeholder')).toBeNull();
  });

  it('shows failed run error state from SSE', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    fireEvent.change(screen.getByTestId('message-input'), { target: { value: 'fail please' } });
    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });
    await waitFor(() => expect(screen.getByTestId('run-start-placeholder')).toBeTruthy());

    act(() => {
      es.emit('failed', JSON.stringify({ error: 'Run exploded' }));
    });

    await waitFor(() => expect(screen.getByTestId('run-error-badge')).toBeTruthy());
    expect(screen.getByText('Run failed: Run exploded')).toBeTruthy();
    expect(screen.queryByTestId('run-start-placeholder')).toBeNull();
  });

  it('renders pending question and submits a custom answer', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc': { ...mockSession, projectId: 'proj-1', activeRun: { runId: 'run-default', status: 'RUNNING' } } });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;
    act(() => {
      es.emit('ask-user-question', JSON.stringify({
        runId: 'run-default',
        toolUseId: 'tool-question-1',
        question: 'Which guidance files would you like to set up?',
        choices: ['Project CLAUDE.md', 'Personal CLAUDE.local.md'],
      }));
    });

    await waitFor(() => expect(screen.getByTestId('pending-question-card')).toBeTruthy());
    expect(screen.getByTestId('pending-question-text').textContent).toContain('Which guidance files would you like to set up?');

    fireEvent.click(screen.getByTestId('pending-question-custom-option').querySelector('input') as Element);
    fireEvent.change(screen.getByTestId('pending-question-input'), { target: { value: 'Both project + personal' } });

    await act(async () => {
      fireEvent.click(screen.getByTestId('pending-question-submit'));
    });

    await waitFor(() => expect(screen.queryByTestId('pending-question-card')).toBeNull());
  });

  it('renders persisted final text even without streamed chunks', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('messages-area')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('completed', JSON.stringify({ finalText: 'Saved final response' }));
    });

    await waitFor(() => expect(screen.getByText('Saved final response')).toBeTruthy());
  });

  it('displays session name in header when available', async () => {
    seedProjectState();
    const namedSession = { ...mockSession, name: 'My Chat' };
    mockChatFetch({ '/api/sessions/ses-abc': { ...namedSession, projectId: 'proj-1' } });

    renderPage();
    await waitFor(() => expect(screen.getByText('My Chat')).toBeTruthy());
  });

  it('shows New Session fallback when name is null', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByText('New Session')).toBeTruthy());
  });

  it('opens session context from header title row button', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          name: 'My Chat',
          model: 'MiniMax-M2.7',
          providerId: 'provider-1',
          permissionMode: 'BYPASS',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 2,
          currentUsage: {
            inputTokens: 12,
            outputTokens: 34,
            cacheReadTokens: 5,
            cacheWriteTokens: 3,
            toolUses: 1,
            contextLength: 54,
          },
          sessionTotals: {
            inputTokens: 12,
            outputTokens: 34,
            cacheReadTokens: 5,
            cacheWriteTokens: 3,
            toolUses: 1,
            contextLength: 54,
          },
        },
        rawAiHistory: [
          {
            direction: 'response',
            content: 'Context from chat page',
            timestamp: '2026-04-07T10:01:00Z',
          },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-context-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));

    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());
    expect(screen.getByText('Context')).toBeTruthy();
    expect(screen.getByText('Tree')).toBeTruthy();
    expect(screen.getByText('Session Information')).toBeTruthy();
    expect(screen.getByText('Session Usage')).toBeTruthy();
    expect(screen.queryByText('Current Usage')).toBeNull();
    expect(screen.queryByText('Session Totals')).toBeNull();
    expect(screen.queryByText('Context Length')).toBeNull();
    expect(screen.getByText('AI History')).toBeTruthy();
    fireEvent.click(screen.getByLabelText('Expand AI history entry 1'));
    fireEvent.click(screen.getByLabelText('Expand response for AI history entry 1'));
    expect(screen.getByText('Context from chat page')).toBeTruthy();
  });

  it('caches session context when reopening the context tab', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
          sessionTotals: {
            inputTokens: 0,
            outputTokens: 0,
            cacheReadTokens: 0,
            cacheWriteTokens: 0,
            toolUses: 0,
            contextLength: 0,
          },
        },
        rawAiHistory: [],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-context-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    fireEvent.click(screen.getByLabelText('Close Context'));
    await waitFor(() => expect(screen.queryByTestId('session-context-panel')).toBeNull());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.filter(([url]) => url === '/api/sessions/ses-abc/context')).toHaveLength(1);
  });

  it('closes the context panel when clicking the active context button again', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
        },
        rawAiHistory: [],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-context-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.queryByTestId('session-context-panel')).toBeNull());
  });

  it('keeps the context tab open while switching back to tree', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
        },
        rawAiHistory: [],
      },
      '/api/files/tree?projectPath=%2Ftmp%2Fproj-1': {
        root: { name: 'proj-1', path: '', isDirectory: true, children: [] },
      },
    });

    renderPage('ses-abc', { fileExplorerOpen: true });
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    fireEvent.click(screen.getByText('Tree'));
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());
    expect(screen.getByText('Context')).toBeTruthy();
  });

  it('falls back to tree when closing the active context tab with no file tab selected', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
        },
        rawAiHistory: [],
      },
      '/api/files/tree?projectPath=%2Ftmp%2Fproj-1': {
        path: '.',
        children: [],
      },
    });

    renderPage('ses-abc', { fileExplorerOpen: true });
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    fireEvent.click(screen.getByLabelText('Close Context'));
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());
  });

  it('opens session git status from header button', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [
          { kind: 'tracked', path: 'src/App.tsx', status: 'modified' },
        ],
        unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));

    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());
    expect(screen.getByText('Git')).toBeTruthy();
    expect(screen.getByText('Tracked Changes')).toBeTruthy();
    expect(screen.getByText('Unversioned Files')).toBeTruthy();
    expect(screen.getByText('src/App.tsx')).toBeTruthy();
    expect(screen.getByText('notes/todo.md')).toBeTruthy();
    expect(screen.getByTestId('session-git-change-badge-src-app-tsx')).toHaveTextContent('modified');
    expect(screen.getByTestId('session-git-change-badge-notes-todo-md')).toHaveTextContent('unversioned');
  });

  it('caches session git status when reopening the git tab', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }],
        unversionedFiles: [],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByLabelText('Close Git'));
    await waitFor(() => expect(screen.queryByTestId('session-git-panel')).toBeNull());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.filter(([url]) => url === '/api/sessions/ses-abc/git')).toHaveLength(1);
  });

  it('closes the git panel when clicking the active git button again', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [],
        unversionedFiles: [],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.queryByTestId('session-git-panel')).toBeNull());
  });

  it('renders git status load failures in the git panel', async () => {
    seedProjectState();
    mockChatFetch();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-abc/git') {
        return Promise.resolve({
          ok: false,
          status: 400,
          json: async () => ({ error: 'Resolved worktree is not a git repository.' }),
        });
      }
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1' }) });
      }
      if (url === '/api/commands') {
        return Promise.resolve({ ok: true, json: async () => ([{ name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false }]) });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));

    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());
    expect(screen.getByText('Resolved worktree is not a git repository.')).toBeTruthy();
  });

  it('opens a full-size git diff popup when a tracked file is clicked', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }],
        unversionedFiles: [],
      },
      '/api/sessions/ses-abc/git/diff?path=src%2FApp.tsx': {
        kind: 'tracked',
        path: 'src/App.tsx',
        diff: 'diff --git a/src/App.tsx b/src/App.tsx\n@@ -1 +1 @@\n-const value = 1;\n+const value = 2;',
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-tracked-change-src-app-tsx'));

    await waitFor(() => expect(screen.getByTestId('session-git-diff-modal')).toBeTruthy());
    expect(screen.getByText('Git Diff')).toBeTruthy();
    expect(screen.getAllByText('src/App.tsx').length).toBeGreaterThan(0);
    expect(screen.getByTestId('session-git-diff-viewer')).toBeTruthy();
    expect(screen.getByTestId('session-git-diff-left-column').textContent).toContain('const value = 1;');
    expect(screen.getByTestId('session-git-diff-right-column').textContent).toContain('const value = 2;');
    expect(screen.getByTestId('session-git-full-file-tab')).toBeTruthy();
  });

  it('renders git diff failures inside the popup', async () => {
    seedProjectState();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-abc/git/diff?path=src%2FApp.tsx') {
        return Promise.resolve({
          ok: false,
          status: 400,
          json: async () => ({ error: 'Failed to load git diff for the selected file.' }),
        });
      }
      if (url === '/api/sessions/ses-abc/git') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }], unversionedFiles: [] }),
        });
      }
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1' }) });
      }
      if (url === '/api/commands') {
        return Promise.resolve({ ok: true, json: async () => ([{ name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false }]) });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-tracked-change-src-app-tsx'));

    await waitFor(() => expect(screen.getByTestId('session-git-diff-modal')).toBeTruthy());
    expect(screen.getByText('Failed to load git diff for the selected file.')).toBeTruthy();
  });

  it('renders an empty git diff state when the popup gets no diff body', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }],
        unversionedFiles: [],
      },
      '/api/sessions/ses-abc/git/diff?path=src%2FApp.tsx': {
        kind: 'tracked',
        path: 'src/App.tsx',
        diff: '',
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-tracked-change-src-app-tsx'));

    await waitFor(() => expect(screen.getByTestId('session-git-diff-empty')).toBeTruthy());
    expect(screen.getByText('No diff content is available for this file.')).toBeTruthy();
  });

  it('opens git diff popup for an unversioned file', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [],
        unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }],
      },
      '/api/sessions/ses-abc/git/diff?path=notes%2Ftodo.md': {
        kind: 'unversioned',
        path: 'notes/todo.md',
        diff: 'diff --git a/notes/todo.md b/notes/todo.md\nnew file mode 100644\n--- /dev/null\n+++ b/notes/todo.md\n@@ -0,0 +1,1 @@\n+todo',
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-unversioned-file-notes-todo-md'));

    await waitFor(() => expect(screen.getByTestId('session-git-diff-modal')).toBeTruthy());
    expect(screen.getByTestId('session-git-diff-right-column').textContent).toContain('todo');
  });

  it('loads the full tracked file on demand from the git diff popup', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }],
        unversionedFiles: [],
      },
      '/api/sessions/ses-abc/git/diff?path=src%2FApp.tsx': {
        kind: 'tracked',
        path: 'src/App.tsx',
        diff: 'diff --git a/src/App.tsx b/src/App.tsx\n@@ -2,3 +2,3 @@\n line2\n-old\n+new\n line4',
      },
      '/api/files/content?projectPath=%2Ftmp%2Fproj-1&filePath=src%2FApp.tsx': {
        name: 'App.tsx',
        path: 'src/App.tsx',
        content: 'const value = 2;\nconsole.log(value);\n',
        size: 36,
        truncated: false,
        binary: false,
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-tracked-change-src-app-tsx'));

    await waitFor(() => expect(screen.getByTestId('session-git-full-file-tab')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-full-file-tab'));

    await waitFor(() => expect(screen.getByTestId('session-git-full-file-viewer')).toBeTruthy());
    expect(screen.getByTestId('file-code-area').textContent).toContain('console.log(value);');
    fireEvent.click(screen.getByTestId('session-git-diff-tab'));
    await waitFor(() => expect(screen.getByTestId('session-git-diff-viewer')).toBeTruthy());
  });

  it('loads the full unversioned file on demand from the git diff popup', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/git': {
        trackedChanges: [],
        unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }],
      },
      '/api/sessions/ses-abc/git/diff?path=notes%2Ftodo.md': {
        kind: 'unversioned',
        path: 'notes/todo.md',
        diff: 'diff --git a/notes/todo.md b/notes/todo.md\nnew file mode 100644\n--- /dev/null\n+++ b/notes/todo.md\n@@ -0,0 +1,3 @@\n+todo\n+second\n+third',
      },
      '/api/files/content?projectPath=%2Ftmp%2Fproj-1&filePath=notes%2Ftodo.md': {
        name: 'todo.md',
        path: 'notes/todo.md',
        content: 'todo\nsecond\nthird\nfourth\n',
        size: 25,
        truncated: false,
        binary: false,
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('chat-session-git-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-git-btn'));
    await waitFor(() => expect(screen.getByTestId('session-git-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-git-unversioned-file-notes-todo-md'));

    await waitFor(() => expect(screen.getByTestId('session-git-full-file-tab')).toBeTruthy());
    fireEvent.click(screen.getByTestId('session-git-full-file-tab'));

    await waitFor(() => expect(screen.getByTestId('session-git-full-file-viewer')).toBeTruthy());
    expect(screen.getByTestId('file-code-area').textContent).toContain('fourth');
  });

  it('loads persisted chat history when an existing session is opened', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          { type: 'user', content: 'Previous question', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
          { type: 'assistant', content: 'Previous answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('Previous question')).toBeTruthy());
    expect(screen.getByText('Previous answer')).toBeTruthy();
    expect((screen.getByTestId('user-message-0') as HTMLDivElement).style.alignItems).toBe('flex-end');
    expect((screen.getByTestId('user-message-0') as HTMLDivElement).style.textAlign).toBe('right');
    expect((screen.getByTestId('user-message-frame-0') as HTMLDivElement).style.border).toBe('1px solid var(--border)');
    expect((screen.getByTestId('user-message-frame-0') as HTMLDivElement).style.background).toBe('var(--surface-accent)');
    expect((screen.getByTestId('assistant-message-1') as HTMLDivElement).style.alignItems).toBe('flex-start');
    expect((screen.getByTestId('assistant-message-1') as HTMLDivElement).style.textAlign).toBe('left');
    expect(screen.getByTestId('messages-area').textContent).not.toContain('You');
    expect(screen.getByTestId('messages-area').textContent).not.toContain('Claude');
  });

  it('shows hover actions and timestamp metadata for persisted user and assistant messages', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          { type: 'user', content: 'Previous question', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
          { type: 'assistant', content: 'Previous answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('Previous question')).toBeTruthy());

    expect(screen.queryByTestId('message-actions-0')).toBeNull();
    fireEvent.mouseEnter(screen.getByTestId('user-message-0'));
    await waitFor(() => expect(screen.getByTestId('message-actions-0')).toBeTruthy());
    expect(screen.getByTestId('message-rollback-0')).toBeTruthy();
    expect(screen.getByTestId('message-copy-0')).toBeTruthy();
    expect(screen.getByTestId('message-timestamp-0').textContent).toBeTruthy();

    fireEvent.mouseEnter(screen.getByTestId('assistant-message-1'));
    await waitFor(() => expect(screen.getByTestId('message-actions-1')).toBeTruthy());
    expect(screen.queryByTestId('message-rollback-1')).toBeNull();
    expect(screen.getByTestId('message-copy-1')).toBeTruthy();
    expect(screen.getByTestId('message-timestamp-1').textContent).toBeTruthy();
  });

  it('copies persisted message content with one click', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          { type: 'assistant', content: 'Copy this answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
        ],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('Copy this answer')).toBeTruthy());

    fireEvent.mouseEnter(screen.getByTestId('assistant-message-0'));
    await waitFor(() => expect(screen.getByTestId('message-copy-0')).toBeTruthy());

    await act(async () => {
      fireEvent.click(screen.getByTestId('message-copy-0'));
    });

    expect((navigator.clipboard.writeText as ReturnType<typeof vi.fn>)).toHaveBeenCalledWith('Copy this answer');
  });

  it('rolls back from a persisted user message and restores it to the composer', async () => {
    seedProjectState();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'proj-1',
            name: 'Project One',
            path: '/tmp/proj-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
          }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }),
        });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        const initialMessages = [
          { type: 'user', content: 'Revise me', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
          { type: 'assistant', content: 'Old answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
        ];
        const rewoundMessages: Array<{ type: string; content: string; timestamp?: string; rollbackIndex?: number | null }> = [];
        const payload = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls.some(([calledUrl, calledInit]) =>
          calledUrl === '/api/commands/execute' && calledInit?.method === 'POST')
          ? rewoundMessages
          : initialMessages;
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1', messages: payload }) });
      }
      if (url === '/api/commands') {
        return Promise.resolve({
          ok: true,
          json: async () => ([
            { name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false },
            { name: 'vim', description: 'Vim mode', aliases: [], webCompatible: false, promptBacked: false },
          ]),
        });
      }
      if (url === '/api/commands/execute' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ prompt: '/rewind jump 0', output: 'Rewound to message [0].', success: true, commandName: 'rewind' }),
        });
      }
      if (url.startsWith('/api/sessions/ses-abc/runs/') && init?.method === 'DELETE') {
        return Promise.resolve({ ok: true, json: async () => ({}) });
      }
      if (url === '/api/sessions/ses-abc/runs' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ runId: 'run-default', status: 'RUNNING' }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByText('Revise me')).toBeTruthy());

    fireEvent.mouseEnter(screen.getByTestId('user-message-0'));
    await waitFor(() => expect(screen.getByTestId('message-rollback-0')).toBeTruthy());

    await act(async () => {
      fireEvent.click(screen.getByTestId('message-rollback-0'));
    });

    await waitFor(() => expect(screen.queryByText('Old answer')).toBeNull());
    expect((screen.getByTestId('message-input') as HTMLTextAreaElement).value).toBe('Revise me');
  });

  it('enters edit mode on name click and saves on Enter', async () => {
    seedProjectState();
    mockChatFetch({ '/api/sessions/ses-abc': { ...mockSession, projectId: 'proj-1' } });
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({ ok: true, json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }) });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1' }) });
      }
      if (url === '/api/sessions/ses-abc' && init?.method === 'PATCH') {
        return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1', name: 'Renamed' }) });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('session-name')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-name'));
    await waitFor(() => expect(screen.getByTestId('session-name-input')).toBeTruthy());

    const input = screen.getByTestId('session-name-input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'Renamed' } });
    await act(async () => {
      fireEvent.keyDown(input, { key: 'Enter' });
    });

    await waitFor(() => expect(screen.getByText('Renamed')).toBeTruthy());
  });

  it('cancels rename on Escape', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();
    await waitFor(() => expect(screen.getByTestId('session-name')).toBeTruthy());

    fireEvent.click(screen.getByTestId('session-name'));
    await waitFor(() => expect(screen.getByTestId('session-name-input')).toBeTruthy());

    const input = screen.getByTestId('session-name-input') as HTMLInputElement;
    fireEvent.change(input, { target: { value: 'Discarded' } });
    await act(async () => {
      fireEvent.keyDown(input, { key: 'Escape' });
    });

    await waitFor(() => expect(screen.getByText('New Session')).toBeTruthy());
  });

  it('uses the session worktree path for file explorer requests', async () => {
    seedProjectState();
    mockChatFetch();

    render(
      <MultiProjectProvider>
        <MemoryRouter initialEntries={['/projects/proj-1/sessions/ses-abc']}>
          <Routes>
            <Route path="/projects/:projectId/sessions/:id" element={<ChatPage fileExplorerOpen />} />
          </Routes>
        </MemoryRouter>
      </MultiProjectProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.some(([url]) => url.includes('/api/files/tree?projectPath=%2Ftmp%2Fproj-1'))).toBe(true);
  });

  it('uses a managed worktree path for file explorer requests', async () => {
    seedProjectState();
    const managedSession = {
      ...mockSession,
      worktreeId: 'managed-1',
      worktree: {
        id: 'managed-1',
        name: 'feature-a',
        path: '/tmp/proj-1-worktree',
        defaultWorktree: false,
        managed: true,
        createdAt: '2026-04-07T10:00:00Z',
      },
    };
    mockChatFetch({
      '/api/sessions/ses-abc': { ...managedSession, projectId: 'proj-1' },
      '/api/sessions?projectId=proj-1': { sessions: [{ ...managedSession, projectId: 'proj-1' }] },
    });

    render(
      <MultiProjectProvider>
        <MemoryRouter initialEntries={['/projects/proj-1/sessions/ses-abc']}>
          <Routes>
            <Route path="/projects/:projectId/sessions/:id" element={<ChatPage fileExplorerOpen />} />
          </Routes>
        </MemoryRouter>
      </MultiProjectProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.some(([url]) => url.includes('/api/files/tree?projectPath=%2Ftmp%2Fproj-1-worktree'))).toBe(true);
  });

  it('does not render workspace toolbar controls inside the chat session column', async () => {
    seedProjectState();
    mockChatFetch();

    render(
      <MultiProjectProvider>
        <MemoryRouter initialEntries={['/projects/proj-1/sessions/ses-abc']}>
          <Routes>
            <Route
              path="/projects/:projectId/sessions/:id"
              element={<ChatPage fileExplorerOpen={false} />}
            />
          </Routes>
        </MemoryRouter>
      </MultiProjectProvider>,
    );

    await waitFor(() => expect(screen.getByTestId('session-name')).toBeTruthy());
    expect(screen.queryByTestId('chat-toolbar')).toBeNull();
    expect(screen.queryByTestId('toolbar-file-toggle')).toBeNull();
  });

  it('uses the expanded main-column layout when the side panel is hidden', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage('ses-abc', { fileExplorerOpen: false });

    await waitFor(() => expect(screen.getByTestId('chat-main-column')).toBeTruthy());
    expect(screen.getByTestId('chat-main-column')).toHaveStyle({ maxWidth: 'none' });
    expect(screen.queryByTestId('file-panel')).toBeNull();
  });

  it('keeps the constrained main-column layout when the side panel is visible', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/files/tree?projectPath=%2Ftmp%2Fproj-1': {
        root: { name: 'proj-1', path: '', isDirectory: true, children: [] },
      },
    });

    renderPage('ses-abc', { fileExplorerOpen: true });

    await waitFor(() => expect(screen.getByTestId('file-panel')).toBeTruthy());
    expect(screen.getByTestId('chat-main-column')).toHaveStyle({ maxWidth: '900px' });
  });

  it('does not render the header new session action', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage();

    await waitFor(() => expect(screen.getByTestId('session-name')).toBeTruthy());
    expect(screen.queryByTestId('chatpage-new-session-btn')).toBeNull();
  });

  it('applies chat font settings to the chat scope', async () => {
    seedProjectState();
    mockChatFetch();

    renderPage('ses-abc', { settings: mockSettings });

    await waitFor(() => expect(screen.getByTestId('chat-font-scope')).toBeTruthy());
    expect(screen.getByTestId('chat-font-scope')).toHaveStyle({
      '--chat-font-family': "ui-monospace, 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', monospace",
      '--chat-font-size': '16px',
    });
  });

  it('creates terminal tabs and hides the panel when the last tab closes', async () => {
    seedProjectState();
    const terminalOne = {
      terminalId: 'term-1',
      label: 'Terminal 1',
      status: 'RUNNING',
      cwd: '/tmp/proj-1',
      worktreeId: 'default',
      createdAt: '2026-04-11T00:00:00Z',
    };
    const terminalTwo = {
      terminalId: 'term-2',
      label: 'Terminal 2',
      status: 'RUNNING',
      cwd: '/tmp/proj-1',
      worktreeId: 'default',
      createdAt: '2026-04-11T00:00:01Z',
    };
    mockChatFetch({
      '/api/sessions/ses-abc/terminals': { terminals: [] },
    });
    const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
    let terminalCreateCount = 0;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/sessions/ses-abc/terminals' && init?.method === 'POST') {
        terminalCreateCount += 1;
        return Promise.resolve({
          ok: true,
          json: async () => terminalCreateCount === 1 ? terminalOne : terminalTwo,
        });
      }
      if ((url === '/api/sessions/ses-abc/terminals/term-1' || url === '/api/sessions/ses-abc/terminals/term-2') && init?.method === 'DELETE') {
        return Promise.resolve({ ok: true, status: 204, json: async () => ({}) });
      }
      return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage('ses-abc', {
      terminalPanelOpen: true,
    });

    await waitFor(() => expect(screen.getByTestId('terminal-panel')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('terminal-tab-term-1')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('terminal-panel-title')).toHaveTextContent('Terminal 1'));
    await waitFor(() => expect(MockWebSocket.instances).toHaveLength(1));
    await waitFor(() => expect(mockTerminalClass.instances).toHaveLength(1));
    expect(screen.getByTestId('terminal-status-badge')).toHaveTextContent('Running');
    expect(screen.getByTestId('terminal-meta-cwd')).toHaveTextContent('/tmp/proj-1');

    const socket = MockWebSocket.instances[0]!;
    const terminal = mockTerminalClass.instances[0]!;
    socket.emit('message', new MessageEvent('message', {
      data: JSON.stringify({ type: 'output', data: 'Last login: Sat Apr 11 17:54:22 on ttys020\r\njianguo@Mac ~ % ' }),
    }));
    await waitFor(() => expect(terminal.write).toHaveBeenCalledWith('Last login: Sat Apr 11 17:54:22 on ttys020\r\njianguo@Mac ~ % '));

    act(() => {
      terminal.emitData('pwd\r');
    });
    expect(socket.send).toHaveBeenCalledWith(JSON.stringify({ type: 'input', data: 'pwd\r' }));

    await act(async () => {
      fireEvent.click(screen.getByTestId('terminal-add-tab'));
    });

    await waitFor(() => expect(screen.getByTestId('terminal-tab-term-2')).toBeTruthy());
    await waitFor(() => expect(mockTerminalClass.instances).toHaveLength(2));
    expect(mockTerminalClass.instances[1]?.focus).toHaveBeenCalled();

    await act(async () => {
      fireEvent.click(screen.getByTestId('terminal-close-term-1'));
    });

    await act(async () => {
      fireEvent.click(screen.getByTestId('terminal-close-term-2'));
    });

    await waitFor(() => expect(screen.queryByTestId('terminal-panel')).toBeNull());
  });

  it('renders terminal metadata for exited terminals', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/terminals': {
        terminals: [{
          terminalId: 'term-exited',
          label: 'Build terminal',
          status: 'EXITED',
          cwd: '/tmp/proj-1/apps/web',
          worktreeId: 'default',
          createdAt: '2026-04-11T00:00:00Z',
          exitCode: 1,
          message: 'Command failed',
        }],
      },
    });

    renderPage('ses-abc', {
      terminalPanelOpen: true,
    });

    await waitFor(() => expect(screen.getByTestId('terminal-panel')).toBeTruthy());
    expect(MockWebSocket.instances).toHaveLength(0);
    expect(screen.getByTestId('terminal-panel-title')).toHaveTextContent('Build terminal');
    expect(screen.getByTestId('terminal-status-badge')).toHaveTextContent('Exited');
    expect(screen.getByTestId('terminal-meta-cwd')).toHaveTextContent('/tmp/proj-1/apps/web');
    expect(screen.getByTestId('terminal-meta-detail')).toHaveTextContent('Exit code 1');
  });

  it('does not echo parent terminal visibility back during prop sync', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc/terminals': { terminals: [] },
    });
    const onTerminalPanelVisibilityChange = vi.fn();

    renderPage('ses-abc', {
      terminalPanelOpen: true,
      onTerminalPanelVisibilityChange,
    });

    await waitFor(() => expect(screen.getByTestId('terminal-panel')).toBeTruthy());
    expect(onTerminalPanelVisibilityChange).not.toHaveBeenCalled();
  });

  describe('@-triggered file autocomplete', () => {
    it('shows autocomplete dropdown when @ is typed in textarea', async () => {
      seedProjectState();
      mockChatFetch();
      const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (typeof url === 'string' && url.includes('/api/files/tree')) {
          return Promise.resolve({
            ok: true,
            json: async () => ({
              path: '.',
              children: [
                { name: 'src', path: 'src', isDirectory: true, size: 0, lastModified: '' },
                { name: 'CLAUDE.md', path: 'CLAUDE.md', isDirectory: false, size: 100, lastModified: '' },
              ],
            }),
          });
        }
        return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      await act(async () => {
        fireEvent.change(input, { target: { value: '@', selectionStart: 1 } });
      });

      await waitFor(() => expect(screen.getByTestId('file-autocomplete')).toBeTruthy());
    });

    it('does not show autocomplete for text without @', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      await act(async () => {
        fireEvent.change(input, { target: { value: 'hello world', selectionStart: 11 } });
      });

      expect(screen.queryByTestId('file-autocomplete')).toBeNull();
    });

    it('dismisses autocomplete on Escape key', async () => {
      seedProjectState();
      mockChatFetch();
      const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (typeof url === 'string' && url.includes('/api/files/tree')) {
          return Promise.resolve({
            ok: true,
            json: async () => ({
              path: '.',
              children: [
                { name: 'src', path: 'src', isDirectory: true, size: 0, lastModified: '' },
              ],
            }),
          });
        }
        return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      await act(async () => {
        fireEvent.change(input, { target: { value: '@', selectionStart: 1 } });
      });

      await waitFor(() => expect(screen.getByTestId('file-autocomplete')).toBeTruthy());

      await act(async () => {
        fireEvent.keyDown(input, { key: 'Escape' });
      });

      await waitFor(() => expect(screen.queryByTestId('file-autocomplete')).toBeNull());
    });

    it('keeps autocomplete navigation on arrow keys instead of using composer history', async () => {
      seedProjectState();
      mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-autocomplete-history', status: 'RUNNING' } });
      const originalMock = (globalThis.fetch as ReturnType<typeof vi.fn>).getMockImplementation();
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (typeof url === 'string' && url.includes('/api/files/tree')) {
          return Promise.resolve({
            ok: true,
            json: async () => ({
              path: '.',
              children: [
                { name: 'src', path: 'src', isDirectory: true, size: 0, lastModified: '' },
                { name: 'specs', path: 'specs', isDirectory: true, size: 0, lastModified: '' },
              ],
            }),
          });
        }
        return originalMock?.(url, init) ?? Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

      fireEvent.change(input, { target: { value: 'previous prompt' } });
      await act(async () => {
        fireEvent.submit(screen.getByTestId('composer'));
      });
      act(() => {
        es.emit('completed', JSON.stringify({ runId: 'run-autocomplete-history', finalText: 'done' }));
      });
      await waitFor(() => expect(input.disabled).toBe(false));

      await act(async () => {
        fireEvent.change(input, { target: { value: '@', selectionStart: 1 } });
      });
      await waitFor(() => expect(screen.getByTestId('file-autocomplete')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'ArrowDown' });

      await waitFor(() => expect(screen.getByTestId('file-autocomplete-item-0').getAttribute('aria-selected')).toBe('true'));
      expect(input.value).toBe('@');
    });

    it('placeholder mentions @ file references', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      expect(input.placeholder).toContain('@');
    });
  });

  describe('/-triggered command palette', () => {
    it('shows command palette when / is typed at start of textarea', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      fireEvent.change(screen.getByTestId('message-input'), { target: { value: '/', selectionStart: 1 } });

      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());
    });

    it('inserts selected web-compatible command into the composer without executing immediately', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      fireEvent.change(input, { target: { value: '/sta', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'Enter' });

      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
      expect(input.value).toBe('/status ');
      expect(screen.queryByTestId('command-palette')).toBeNull();
      await waitFor(() => expect(document.activeElement).toBe(input));
      await waitFor(() => expect(input.selectionStart).toBe('/status '.length));
      await waitFor(() => expect(input.selectionEnd).toBe('/status '.length));
      expect(calls.some(([url, init]) => url === '/api/commands/execute' && init?.method === 'POST')).toBe(false);
    });

    it('inserts selected non-web-compatible command into the composer without executing immediately', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      fireEvent.change(input, { target: { value: '/vim', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'Enter' });

      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
      expect(input.value).toBe('/vim ');
      expect(screen.queryByText('/vim is not available in web mode.')).toBeNull();
      expect(calls.some(([url]) => url === '/api/commands/execute')).toBe(false);
    });

    it('keeps command palette navigation on arrow keys instead of using composer history', async () => {
      seedProjectState();
      mockChatFetch({ '/api/sessions/ses-abc/runs': { runId: 'run-command-history', status: 'RUNNING' } });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

      fireEvent.change(input, { target: { value: 'previous prompt' } });
      await act(async () => {
        fireEvent.submit(screen.getByTestId('composer'));
      });
      act(() => {
        es.emit('completed', JSON.stringify({ runId: 'run-command-history', finalText: 'done' }));
      });
      await waitFor(() => expect(input.disabled).toBe(false));

      fireEvent.change(input, { target: { value: '/', selectionStart: 1 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'ArrowDown' });

      expect(screen.queryByTestId('command-palette')).toBeTruthy();
      expect(input.value).toBe('/');
    });

  it('executes selected web-compatible command only after the user submits it', async () => {
    seedProjectState();
    let sessionFetchCount = 0;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'proj-1',
            name: 'Project One',
            path: '/tmp/proj-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
          }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }),
        });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        sessionFetchCount += 1;
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockSession,
            projectId: 'proj-1',
            messages: sessionFetchCount > 1
              ? [
                  { type: 'user', content: '/status', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
                  { type: 'assistant', content: 'Everything is healthy', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
                ]
              : [],
          }),
        });
      }
      if (url === '/api/commands') {
        return Promise.resolve({
          ok: true,
          json: async () => ([
            { name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false },
            { name: 'vim', description: 'Vim mode', aliases: [], webCompatible: false, promptBacked: false },
          ]),
        });
      }
      if (url === '/api/commands/execute' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ prompt: '/status', output: 'Everything is healthy', success: true, commandName: 'status' }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      fireEvent.change(input, { target: { value: '/sta', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => expect(input.value).toBe('/status '));

      fireEvent.keyDown(input, { key: 'Enter' });

      await waitFor(() => expect(screen.getByText('/status')).toBeTruthy());
      expect(screen.getByText('Everything is healthy')).toBeTruthy();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.some(([url, init]) => url === '/api/commands/execute' && init?.method === 'POST')).toBe(true);
  });

  it('shows slash command immediately and reconciles with refreshed persisted messages', async () => {
    seedProjectState();
    let sessionFetchCount = 0;
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }),
        });
      }
      if (url === '/api/sessions?projectId=proj-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }),
        });
      }
      if (url === '/api/sessions/ses-abc' && !init?.method) {
        sessionFetchCount += 1;
        return Promise.resolve({
          ok: true,
          json: async () => ({
            ...mockSession,
            projectId: 'proj-1',
            messages: sessionFetchCount > 1
              ? [
                  { type: 'user', content: 'this is a propose command, user want you to query weather for : now', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
                  { type: 'assistant', content: 'Everything is healthy', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
                ]
              : [],
          }),
        });
      }
      if (url === '/api/commands') {
        return Promise.resolve({
          ok: true,
          json: async () => ([
            { name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: true },
          ]),
        });
      }
      if (url === '/api/commands/resolve-prompt' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ commandName: 'status', visiblePrompt: 'this is a propose command, user want you to query weather for : now', promptBacked: true }),
        });
      }
      if (url === '/api/sessions/ses-abc/runs' && init?.method === 'POST') {
        return Promise.resolve({
          ok: true,
          json: async () => ({ runId: 'run-status', status: 'RUNNING', visiblePrompt: 'this is a propose command, user want you to query weather for : now' }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

    const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
    fireEvent.change(input, { target: { value: '/sta', selectionStart: 4 } });
    await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());
    fireEvent.keyDown(input, { key: 'Enter' });
    await waitFor(() => expect(input.value).toBe('/status '));
    fireEvent.change(input, { target: { value: '/status now' } });

    await act(async () => {
      fireEvent.submit(screen.getByTestId('composer'));
    });

    await waitFor(() => expect(screen.getByText('this is a propose command, user want you to query weather for : now')).toBeTruthy());

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;
    act(() => {
      es.emit('text-chunk', JSON.stringify({ runId: 'run-status', chunk: 'Everything is healthy' }));
      es.emit('completed', JSON.stringify({ runId: 'run-status', finalText: 'Everything is healthy' }));
    });

    await waitFor(() => expect(screen.getByText('Everything is healthy')).toBeTruthy());
    await waitFor(() => expect(screen.getAllByText('this is a propose command, user want you to query weather for : now')).toHaveLength(1));
  });

  it('restores persisted command and assistant messages after refresh', async () => {
    seedProjectState();
    mockChatFetch({
      '/api/sessions/ses-abc': {
        ...mockSession,
        projectId: 'proj-1',
        messages: [
          { type: 'user', content: '/opsx-propose fix session refresh', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
          {
            type: 'assistant',
            content: [
              '**Proposed Change: Fix Session Refresh**',
              'Short overview.',
              '',
              '### Next Action',
              'Run `/opsx-apply`.',
            ].join('\n'),
            timestamp: '2026-04-07T10:01:05Z',
            rollbackIndex: null,
          },
        ],
      },
    });

    renderPage();

    await waitFor(() => expect(screen.getByText('/opsx-propose fix session refresh')).toBeTruthy());
    expect(screen.getByTestId('structured-summary').textContent).toContain('Proposed Change: Fix Session Refresh');
    expect(screen.getByTestId('structured-section-next-action')).toBeTruthy();
  });

    it('shows inline error for non-web-compatible commands only after submit', async () => {
      seedProjectState();
      mockChatFetch();

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input') as HTMLTextAreaElement;
      fireEvent.change(input, { target: { value: '/vim', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => expect(input.value).toBe('/vim '));

      fireEvent.keyDown(input, { key: 'Enter' });

      await waitFor(() => expect(screen.getByText('/vim is not available in web mode.')).toBeTruthy());

      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
      expect(calls.some(([url]) => url === '/api/commands/execute')).toBe(false);
    });

    it('shows progress while generating read audio then renders playback controls', async () => {
      seedProjectState();
      let sessionFetchCount = 0;

      let resolveExecute: ((value: { ok: boolean; json: () => Promise<{ prompt: string; output: string; success: boolean; commandName: string; audio?: { token: string; url: string } | null }> }) => void) | null = null;
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (url === '/api/projects/proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }) });
        }
        if (url === '/api/sessions?projectId=proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
        }
        if (url === '/api/sessions/ses-abc' && !init?.method) {
          sessionFetchCount += 1;
          return Promise.resolve({
            ok: true,
            json: async () => ({
              ...mockSession,
              projectId: 'proj-1',
              messages: sessionFetchCount > 1
                ? [
                    { type: 'user', content: '/read hello', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
                    { type: 'assistant', content: 'Read aloud text.', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
                  ]
                : [],
            }),
          });
        }
        if (url === '/api/commands') {
          return Promise.resolve({
            ok: true,
            json: async () => ([
              { name: 'read', description: 'Read text aloud', aliases: [], webCompatible: true, promptBacked: false },
            ]),
          });
        }
        if (url === '/api/commands/execute' && init?.method === 'POST') {
          return new Promise((resolve) => {
            resolveExecute = resolve;
          });
        }
        if (url === '/api/commands/audio/tok-1' && init?.method === 'HEAD') {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
        }
        if (url === '/api/commands/audio/tok-1' && init?.method === 'DELETE') {
          return Promise.resolve({ ok: true, status: 204, json: async () => ({}) });
        }
        return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input');
      fireEvent.change(input, { target: { value: '/rea', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());

      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => expect(input).toHaveValue('/read '));
      fireEvent.change(input, { target: { value: '/read hello', selectionStart: 11 } });
      fireEvent.keyDown(input, { key: 'Enter' });

      await waitFor(() => expect(screen.getByTestId('read-command-progress')).toBeTruthy());

      await act(async () => {
        resolveExecute?.({
          ok: true,
          json: async () => ({
            prompt: '/read hello',
            output: 'Read aloud text.',
            success: true,
            commandName: 'read',
            audio: { token: 'tok-1', url: '/api/commands/audio/tok-1' },
          }),
        });
      });

      await waitFor(() => expect(screen.queryByTestId('read-command-progress')).toBeNull());
      await waitFor(() => expect(screen.getByTestId('read-command-player')).toBeTruthy());
      expect(screen.getByTestId('read-command-play')).toBeTruthy();
      expect(screen.getByTestId('read-command-player')).toHaveTextContent('/read hello');
      expect(screen.getByText('Read aloud text.')).toBeTruthy();
    });

    it('shows pause and stop controls while read audio is playing', async () => {
      seedProjectState();
      mockChatFetch();
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (url === '/api/projects/proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }) });
        }
        if (url === '/api/sessions?projectId=proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
        }
        if (url === '/api/sessions/ses-abc' && !init?.method) {
          return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1' }) });
        }
        if (url === '/api/commands') {
          return Promise.resolve({ ok: true, json: async () => ([{ name: 'read', description: 'Read text aloud', aliases: [], webCompatible: true, promptBacked: false }]) });
        }
        if (url === '/api/commands/execute' && init?.method === 'POST') {
          return Promise.resolve({
            ok: true,
            json: async () => ({
              prompt: '/read hello',
              output: 'Read aloud text.',
              success: true,
              commandName: 'read',
              audio: { token: 'tok-1', url: '/api/commands/audio/tok-1' },
            }),
          });
        }
        if (url === '/api/commands/audio/tok-1' && init?.method === 'HEAD') {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
        }
        if (url === '/api/commands/audio/tok-1' && init?.method === 'DELETE') {
          return Promise.resolve({ ok: true, status: 204, json: async () => ({}) });
        }
        return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();
      await waitFor(() => expect(screen.getByTestId('message-input')).toBeTruthy());

      const input = screen.getByTestId('message-input');
      fireEvent.change(input, { target: { value: '/rea', selectionStart: 4 } });
      await waitFor(() => expect(screen.getByTestId('command-palette')).toBeTruthy());
      fireEvent.keyDown(input, { key: 'Enter' });
      await waitFor(() => expect(input).toHaveValue('/read '));
      fireEvent.change(input, { target: { value: '/read hello', selectionStart: 11 } });

      fireEvent.keyDown(input, { key: 'Enter' });

      await waitFor(() => expect(screen.getByTestId('read-command-play')).toBeTruthy());
      fireEvent.click(screen.getByTestId('read-command-play'));

      await waitFor(() => expect(screen.getByTestId('read-command-pause')).toBeTruthy());
      expect(screen.getByTestId('read-command-stop')).toBeTruthy();
      expect(MockAudio.instances).toHaveLength(1);

      fireEvent.click(screen.getByTestId('read-command-stop'));

      expect(MockAudio.instances[0]?.pause).toHaveBeenCalled();
      expect(MockAudio.instances[0]?.currentTime).toBe(0);
    });

    it('restores read playback controls after refresh when stored audio is still available', async () => {
      seedProjectState();
      localStorage.setItem('coderhino-read-playback', JSON.stringify({
        status: 'paused',
        token: 'tok-restore',
        url: '/api/commands/audio/tok-restore',
        prompt: '/read restored',
        output: 'Read aloud text.',
      }));
      (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
        if (url === '/api/projects/proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ id: 'proj-1', name: 'Project One', path: '/tmp/proj-1', lastOpened: '2026-04-07T10:00:00Z', createdAt: '2026-04-07T10:00:00Z' }) });
        }
        if (url === '/api/sessions?projectId=proj-1') {
          return Promise.resolve({ ok: true, json: async () => ({ sessions: [{ ...mockSession, projectId: 'proj-1' }] }) });
        }
        if (url === '/api/sessions/ses-abc' && !init?.method) {
          return Promise.resolve({ ok: true, json: async () => ({ ...mockSession, projectId: 'proj-1' }) });
        }
        if (url === '/api/commands') {
          return Promise.resolve({ ok: true, json: async () => ([{ name: 'read', description: 'Read text aloud', aliases: [], webCompatible: true, promptBacked: false }]) });
        }
        if (url === '/api/commands/audio/tok-restore' && init?.method === 'HEAD') {
          return Promise.resolve({ ok: true, status: 200, json: async () => ({}) });
        }
        if (url === '/api/commands/audio/tok-restore' && init?.method === 'DELETE') {
          return Promise.resolve({ ok: true, status: 204, json: async () => ({}) });
        }
        return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
      });

      renderPage();

      await waitFor(() => expect(screen.getByTestId('read-command-player')).toBeTruthy());
      expect(screen.getByText('/read restored')).toBeTruthy();
      expect(screen.getByTestId('read-command-play')).toBeTruthy();
      expect(screen.getByTestId('read-command-stop')).toBeTruthy();
    });
  });
});
