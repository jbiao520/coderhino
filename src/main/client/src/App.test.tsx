import { render, fireEvent, screen, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import App from './App';

const playMock = vi.fn(() => Promise.resolve());

class MockEventSource {
  addEventListener() {}
  close() {}
}

function mockFetchResponses(responses?: Record<string, unknown>) {
  const defaults = {
    '/api/sessions': { sessions: [] },
    '/api/projects': { projects: [], count: 0 },
    '/api/commands': [],
  };
  const all = { ...defaults, ...responses };
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
    for (const [path, data] of Object.entries(all)) {
      if (url === path || url.startsWith(path + '?')) {
        return Promise.resolve({ ok: true, json: async () => data });
      }
    }
    return Promise.resolve({ ok: true, json: async () => ({}) });
  });
}

function renderWithRouter(initialPath = '/') {
  return render(
    <MemoryRouter initialEntries={[initialPath]}>
      <App />
    </MemoryRouter>,
  );
}

function seedProjectChatRoute() {
  localStorage.setItem(
    'coderhino-multi-project',
    JSON.stringify({
      openProjectIds: ['proj-1'],
      lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
      recentSessionOrder: ['ses-abc'],
      activeProjectId: 'proj-1',
    }),
  );

  mockFetchResponses({
    '/api/projects/proj-1': {
      id: 'proj-1',
      name: 'Project One',
      path: '/tmp/proj-1',
      lastOpened: '2026-04-07T10:00:00Z',
      createdAt: '2026-04-07T10:00:00Z',
      workspaceEnabled: false,
      worktrees: [
        {
          id: 'default',
          name: 'default',
          path: '/tmp/proj-1',
          defaultWorktree: true,
          managed: false,
          createdAt: '2026-04-07T10:00:00Z',
        },
      ],
    },
    '/api/sessions?projectId=proj-1': {
      sessions: [
        {
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          model: 'MiniMax-M2.7',
          permissionMode: 'BYPASS',
          planMode: false,
          buildMode: true,
          availableModels: ['MiniMax-M2.7'],
          modelModeSupported: true,
          availableModelModes: ['default'],
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
        },
      ],
    },
    '/api/sessions/ses-abc': {
      sessionId: 'ses-abc',
      projectId: 'proj-1',
      createdAt: '2026-04-07T10:00:00Z',
      updatedAt: '2026-04-07T10:05:00Z',
      status: 'ACTIVE',
      activeRun: null,
      messages: [],
      model: 'MiniMax-M2.7',
      permissionMode: 'BYPASS',
      planMode: false,
      buildMode: true,
      availableModels: ['MiniMax-M2.7'],
      modelModeSupported: true,
      availableModelModes: ['default'],
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
    },
  });
}

describe('App', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    (globalThis as Record<string, unknown>)['EventSource'] = MockEventSource;
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
    playMock.mockClear();
    (globalThis as Record<string, unknown>).Audio = vi.fn(() => ({
      currentTime: 0,
      play: playMock,
    }));
    mockFetchResponses();
  });

  it('renders without crashing', () => {
    const { container } = renderWithRouter('/');
    expect(container).toBeTruthy();
  });

  it('renders sidebar with icon rail buttons', () => {
    const { getByTestId } = renderWithRouter('/');
    expect(getByTestId('rail-add-project')).toBeTruthy();
    expect(getByTestId('rail-settings')).toBeTruthy();
  });

  it('renders empty state in detail panel', () => {
    const { getByText } = renderWithRouter('/');
    expect(getByText('No projects open.')).toBeTruthy();
    expect(getByText('Click + to open one.')).toBeTruthy();
  });

  it('renders IdeLeftPanel in sidebar', () => {
    const { container } = renderWithRouter('/');
    const aside = container.querySelector('aside');
    expect(aside).toBeTruthy();
    expect(aside?.textContent).toContain('No projects open.');
  });

  it('renders open project button in icon rail', () => {
    const { getByTestId } = renderWithRouter('/');
    expect(getByTestId('rail-add-project')).toBeTruthy();
  });

  it('redirects legacy session routes to new format', async () => {
    // Mock a session API response for legacy redirect with projectId
    mockFetchResponses({
      '/api/sessions/session-123': {
        sessionId: 'session-123',
        projectId: 'project-456',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    });

    const { container } = renderWithRouter('/sessions/session-123');
    expect(container).toBeTruthy();
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/session-123', expect.anything());
  });

  it('redirects legacy session route to /sessions when projectId missing', () => {
    mockFetchResponses({
      '/api/sessions/session-123': {
        sessionId: 'session-123',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    });

    const { container } = renderWithRouter('/sessions/session-123');
    expect(container).toBeTruthy();
    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/session-123', expect.anything());
  });

  it('folds and restores the sidebar from the chat toolbar', async () => {
    seedProjectChatRoute();

    const { queryByTestId, getByTestId } = renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(getByTestId('chat-toolbar')).toBeTruthy());
    expect(getByTestId('app-sidebar')).toHaveClass('app-sidebar');
    expect(getByTestId('app-sidebar')).not.toHaveClass('app-sidebar-collapsed');
    expect(getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '260px' });
    expect(getByTestId('toolbar-project-toggle')).toHaveAccessibleName('Fold project panel');
    expect(getByTestId('toolbar-terminal-toggle')).toHaveAccessibleName('Show terminal panel');
    expect(getByTestId('toolbar-file-toggle')).toHaveAccessibleName('Open file explorer');
    expect(getByTestId('toolbar-project-toggle')).toHaveTextContent('');
    expect(getByTestId('toolbar-terminal-toggle')).toHaveTextContent('');
    expect(getByTestId('toolbar-file-toggle')).toHaveTextContent('');
    expect((getByTestId('toolbar-search-input') as HTMLInputElement).value).toBe('');
    expect(getByTestId('app-sidebar')).toBeTruthy();
    expect(getByTestId('toolbar-project-toggle-icon')).toBeTruthy();
    expect(getByTestId('sidebar-session-panel')).toBeTruthy();

    fireEvent.click(getByTestId('toolbar-project-toggle'));
    await waitFor(() => expect(queryByTestId('sidebar-session-panel')).toBeNull());
    expect(getByTestId('app-sidebar')).toBeTruthy();
    expect(getByTestId('app-sidebar')).toHaveClass('app-sidebar-collapsed');
    expect(getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '72px' });
    expect(getByTestId('project-menu-trigger')).toBeTruthy();
    expect(getByTestId('rail-settings')).toBeTruthy();
    expect(getByTestId('toolbar-project-toggle')).toHaveAccessibleName('Expand project panel');

    fireEvent.click(getByTestId('toolbar-project-toggle'));
    await waitFor(() => expect(getByTestId('sidebar-session-panel')).toBeTruthy());
    expect(getByTestId('app-sidebar')).not.toHaveClass('app-sidebar-collapsed');
    expect(getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '260px' });
  });

  it('keeps the routed project open after refreshing a project session URL', async () => {
    mockFetchResponses({
      '/api/projects/workspace-state': { openProjectIds: [], activeProjectId: null },
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/proj-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [
          {
            sessionId: 'ses-abc',
            projectId: 'proj-1',
            createdAt: '2026-04-07T10:00:00Z',
            updatedAt: '2026-04-07T10:05:00Z',
            status: 'ACTIVE',
            activeRun: null,
            messages: [],
            model: 'MiniMax-M2.7',
            permissionMode: 'BYPASS',
            planMode: false,
            buildMode: true,
            availableModels: ['MiniMax-M2.7'],
            modelModeSupported: true,
            availableModelModes: ['default'],
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
          },
        ],
      },
      '/api/sessions/ses-abc': {
        sessionId: 'ses-abc',
        projectId: 'proj-1',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:05:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        model: 'MiniMax-M2.7',
        permissionMode: 'BYPASS',
        planMode: false,
        buildMode: true,
        availableModels: ['MiniMax-M2.7'],
        modelModeSupported: true,
        availableModelModes: ['default'],
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
      },
    });

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('sidebar-session-panel')).toBeTruthy());
    await waitFor(() => expect(screen.getByTestId('project-menu-trigger')).toBeTruthy());
    expect(screen.queryByText('No projects open.')).toBeNull();
    expect(globalThis.fetch).toHaveBeenCalledWith(
      '/api/projects/workspace-state',
      expect.objectContaining({ method: 'PUT' }),
    );
  });

  it('restores and clamps the expanded sidebar width from localStorage', async () => {
    localStorage.setItem('coderhino-project-sidebar-width', '999');
    seedProjectChatRoute();

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('chat-toolbar')).toBeTruthy());
    expect(screen.getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '520px' });
    expect(screen.getByTestId('app-sidebar-resize-handle')).toBeTruthy();
  });

  it('resizes the expanded sidebar and preserves the stored width across folding', async () => {
    seedProjectChatRoute();

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('app-sidebar-resize-handle')).toBeTruthy());

    fireEvent.mouseDown(screen.getByTestId('app-sidebar-resize-handle'), { clientX: 260 });
    fireEvent.mouseMove(document, { clientX: 360 });
    fireEvent.mouseUp(document);

    expect(screen.getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '360px' });
    expect(localStorage.getItem('coderhino-project-sidebar-width')).toBe('360');

    fireEvent.click(screen.getByTestId('toolbar-project-toggle'));
    await waitFor(() => expect(screen.queryByTestId('sidebar-session-panel')).toBeNull());
    expect(screen.getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '72px' });
    expect(screen.queryByTestId('app-sidebar-resize-handle')).toBeNull();

    fireEvent.click(screen.getByTestId('toolbar-project-toggle'));
    await waitFor(() => expect(screen.getByTestId('sidebar-session-panel')).toBeTruthy());
    expect(screen.getByTestId('app-sidebar')).toHaveStyle({ '--app-sidebar-width': '360px' });
  });

  it('opens settings in a popup without leaving the active workspace view', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: 'proj-1',
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [{
          id: 'default',
          name: 'default',
          path: '/tmp/proj-1',
          defaultWorktree: true,
          managed: false,
          createdAt: '2026-04-07T10:00:00Z',
        }],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          model: 'MiniMax-M2.7',
          permissionMode: 'BYPASS',
          planMode: false,
          buildMode: true,
          availableModels: ['MiniMax-M2.7'],
          modelModeSupported: true,
          availableModelModes: ['default'],
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
        }],
      },
      '/api/sessions/ses-abc': {
        sessionId: 'ses-abc',
        projectId: 'proj-1',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:05:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        model: 'MiniMax-M2.7',
        permissionMode: 'BYPASS',
        planMode: false,
        buildMode: true,
        availableModels: ['MiniMax-M2.7'],
        modelModeSupported: true,
        availableModelModes: ['default'],
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
      },
      '/api/settings': {
        defaultPermissionMode: 'BYPASS',
        theme: 'dark',
        defaultModel: 'MiniMax-M2.7',
      },
      '/api/credentials': {
        defaultProviderId: 'provider-1',
        providers: [{
          id: 'provider-1',
          name: 'MiniMax',
          apiKeyMasked: '****abcd',
          apiBaseUrl: 'https://api.example.com',
          models: [{ id: 'MiniMax-M2.7', contextWindow: 128000 }],
          apiType: 'CLAUDE_CODE',
          hasApiKey: true,
        }],
      },
    });

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('chat-toolbar')).toBeTruthy());

    fireEvent.click(screen.getByTestId('rail-settings'));

    await waitFor(() => expect(screen.getByTestId('popup-overlay')).toBeTruthy());
    expect(screen.getByTestId('chat-toolbar')).toBeTruthy();
    expect(screen.getByTestId('settings-embedded')).toBeTruthy();
    expect(screen.queryByTestId('settings-page')).toBeNull();
    expect(screen.getByRole('heading', { name: 'Settings' })).toBeTruthy();
    expect(screen.getByTestId('settings-tabs')).toBeTruthy();
    expect(screen.getByTestId('settings-section-select')).toBeTruthy();
    expect(screen.queryByTestId('popup-close')).toBeNull();

    fireEvent.click(screen.getByTestId('popup-overlay'));

    await waitFor(() => expect(screen.queryByTestId('popup-overlay')).toBeNull());
    expect(screen.getByTestId('chat-toolbar')).toBeTruthy();
    expect(screen.getByTestId('sidebar-session-panel')).toBeTruthy();
  });

  it('toggles the files panel from the toolbar and closes it on repeat', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: 'proj-1',
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [{
          id: 'default',
          name: 'default',
          path: '/tmp/proj-1',
          defaultWorktree: true,
          managed: false,
          createdAt: '2026-04-07T10:00:00Z',
        }],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          model: 'MiniMax-M2.7',
          permissionMode: 'BYPASS',
          planMode: false,
          buildMode: true,
          availableModels: ['MiniMax-M2.7'],
          modelModeSupported: true,
          availableModelModes: ['default'],
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
        }],
      },
      '/api/sessions/ses-abc': {
        sessionId: 'ses-abc',
        projectId: 'proj-1',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:05:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        model: 'MiniMax-M2.7',
        permissionMode: 'BYPASS',
        planMode: false,
        buildMode: true,
        availableModels: ['MiniMax-M2.7'],
        modelModeSupported: true,
        availableModelModes: ['default'],
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
      },
      '/api/files/tree': {
        path: '.',
        children: [],
      },
    });

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('toolbar-file-toggle')).toBeTruthy());

    fireEvent.click(screen.getByTestId('toolbar-file-toggle'));
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());
    expect(screen.getByTestId('toolbar-file-toggle')).toHaveAccessibleName('Close file explorer');

    fireEvent.click(screen.getByTestId('toolbar-file-toggle'));
    await waitFor(() => expect(screen.queryByTestId('file-explorer')).toBeNull());
    expect(screen.getByTestId('toolbar-file-toggle')).toHaveAccessibleName('Open file explorer');
  });

  it('switches from context to files from the toolbar before closing on repeat', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: 'proj-1',
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [{
          id: 'default',
          name: 'default',
          path: '/tmp/proj-1',
          defaultWorktree: true,
          managed: false,
          createdAt: '2026-04-07T10:00:00Z',
        }],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          model: 'MiniMax-M2.7',
          permissionMode: 'BYPASS',
          planMode: false,
          buildMode: true,
          availableModels: ['MiniMax-M2.7'],
          modelModeSupported: true,
          availableModelModes: ['default'],
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
        }],
      },
      '/api/sessions/ses-abc': {
        sessionId: 'ses-abc',
        projectId: 'proj-1',
        createdAt: '2026-04-07T10:00:00Z',
        updatedAt: '2026-04-07T10:05:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        model: 'MiniMax-M2.7',
        permissionMode: 'BYPASS',
        planMode: false,
        buildMode: true,
        availableModels: ['MiniMax-M2.7'],
        modelModeSupported: true,
        availableModelModes: ['default'],
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
      },
      '/api/sessions/ses-abc/context': {
        summary: {
          sessionId: 'ses-abc',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
        },
        rawAiHistory: [],
      },
      '/api/files/tree': {
        path: '.',
        children: [],
      },
    });

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('chat-session-context-btn')).toBeTruthy());

    fireEvent.click(screen.getByTestId('chat-session-context-btn'));
    await waitFor(() => expect(screen.getByTestId('session-context-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('toolbar-file-toggle'));
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());
    expect(screen.queryByTestId('session-context-panel')).toBeNull();

    fireEvent.click(screen.getByTestId('toolbar-file-toggle'));
    await waitFor(() => expect(screen.queryByTestId('file-explorer')).toBeNull());
  });

  it('polls task completions and renders a badge for unseen project notifications', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: null,
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
        }],
      },
      '/api/tasks/completions': {
        completions: [{
          completionId: 'task-1',
          taskId: 'task-1',
          description: 'Background task',
          projectId: 'proj-1',
          sessionId: 'ses-other',
          completedAt: '2026-04-12T00:00:00Z',
        }],
      },
    });

    renderWithRouter('/sessions');

    await waitFor(() => expect(screen.getByTestId('rail-avatar-badge-proj-1')).toHaveTextContent('1'));
    expect(playMock).toHaveBeenCalledTimes(1);
  });

  it('swallows blocked task completion audio playback errors', async () => {
    playMock.mockImplementationOnce(() => Promise.reject(new Error('blocked')));
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: null,
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
        }],
      },
      '/api/tasks/completions': {
        completions: [{
          completionId: 'task-1',
          taskId: 'task-1',
          description: 'Background task',
          projectId: 'proj-1',
          sessionId: 'ses-other',
          completedAt: '2026-04-12T00:00:00Z',
        }],
      },
    });

    renderWithRouter('/sessions');

    await waitFor(() => expect(screen.getByTestId('rail-avatar-badge-proj-1')).toHaveTextContent('1'));
    expect(playMock).toHaveBeenCalledTimes(1);
  });

  it('polls AI run completions and deduplicates by runId', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: null,
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
        }],
      },
      '/api/tasks/completions': {
        completions: [
          {
            completionId: 'run-1',
            taskId: 'run-1',
            runId: 'run-1',
            description: 'AI run completed',
            projectId: 'proj-1',
            sessionId: 'ses-other',
            completedAt: '2026-04-12T00:00:00Z',
          },
          {
            completionId: 'run-1-duplicate',
            taskId: 'run-1-duplicate',
            runId: 'run-1',
            description: 'AI run completed',
            projectId: 'proj-1',
            sessionId: 'ses-other',
            completedAt: '2026-04-12T00:00:01Z',
          },
        ],
      },
    });

    renderWithRouter('/sessions');

    await waitFor(() => expect(screen.getByTestId('rail-avatar-badge-proj-1')).toHaveTextContent('1'));
    expect(playMock).toHaveBeenCalledTimes(1);
  });

  it('does not badge AI completion for the active project session', async () => {
    localStorage.setItem(
      'coderhino-multi-project',
      JSON.stringify({
        openProjectIds: ['proj-1'],
        lastActiveSessionByProject: { 'proj-1': 'ses-abc' },
        recentSessionOrder: ['ses-abc'],
        activeProjectId: 'proj-1',
      }),
    );

    mockFetchResponses({
      '/api/projects/proj-1': {
        id: 'proj-1',
        name: 'Project One',
        path: '/tmp/proj-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: false,
        worktrees: [],
      },
      '/api/sessions?projectId=proj-1': {
        sessions: [{
          sessionId: 'ses-abc',
          projectId: 'proj-1',
          createdAt: '2026-04-07T10:00:00Z',
          updatedAt: '2026-04-07T10:05:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
        }],
      },
      '/api/tasks/completions': {
        completions: [{
          completionId: 'run-2',
          taskId: 'run-2',
          runId: 'run-2',
          description: 'AI run completed',
          projectId: 'proj-1',
          sessionId: 'ses-abc',
          completedAt: '2026-04-12T00:00:00Z',
        }],
      },
      '/api/sessions/ses-abc': {
        sessionId: 'ses-abc',
        projectId: 'proj-1',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        createdAt: new Date().toISOString(),
        updatedAt: new Date().toISOString(),
      },
    });

    renderWithRouter('/projects/proj-1/sessions/ses-abc');

    await waitFor(() => expect(screen.getByTestId('chat-session-context-btn')).toBeTruthy());
    expect(screen.queryByTestId('rail-avatar-badge-proj-1')).toBeNull();
    expect(playMock).toHaveBeenCalledTimes(1);
  });
});
