import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SessionListPage from '../pages/SessionListPage';
import { MultiProjectProvider } from '../context/MultiProjectContext';

const mockNavigate = vi.fn();

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => mockNavigate,
  };
});

const mockSessions = [
  {
    sessionId: 'ses-001',
    createdAt: '2026-04-07T10:00:00Z',
    updatedAt: '2026-04-07T10:00:00Z',
    status: 'ACTIVE',
    activeRun: null,
    messages: [],
  },
  {
    sessionId: 'ses-002',
    createdAt: '2026-04-07T11:00:00Z',
    updatedAt: '2026-04-07T11:00:00Z',
    status: 'COMPLETED',
    activeRun: null,
    messages: [],
  },
];

function mockApiRoutes(routes: Record<string, unknown>) {
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
    if (init?.method === 'POST' && url === '/api/sessions') {
      return Promise.resolve({
        ok: true,
        json: async () => ({
          sessionId: 'ses-new',
          createdAt: '2026-04-07T12:00:00Z',
          updatedAt: '2026-04-07T12:00:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          projectId: 'project-1',
        }),
      });
    }

    const data = routes[url];
    if (data !== undefined) {
      return Promise.resolve({ ok: true, json: async () => data });
    }

    return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
  });
}

function seedActiveProject(projectId = 'project-1') {
  localStorage.setItem(
    'coderhino-multi-project',
    JSON.stringify({
      openProjectIds: [projectId],
      lastActiveSessionByProject: { [projectId]: 'ses-001' },
      recentSessionOrder: ['ses-001'],
      activeProjectId: projectId,
    }),
  );
}

function renderPage() {
  return render(
    <MemoryRouter>
      <MultiProjectProvider>
        <SessionListPage />
      </MultiProjectProvider>
    </MemoryRouter>,
  );
}

describe('SessionListPage', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    mockNavigate.mockReset();
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

  it('renders active-project scoped session list', async () => {
    seedActiveProject('project-1');
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/project-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=project-1': { sessions: mockSessions },
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('ses-001')).toBeTruthy();
      expect(screen.getByText('ses-002')).toBeTruthy();
    });
  });

  it('displays session name when available', async () => {
    seedActiveProject('project-1');
    const namedSession = {
      ...mockSessions[0],
      name: 'My Chat Session',
    };
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/project-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=project-1': { sessions: [namedSession] },
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('My Chat Session')).toBeTruthy();
    });
  });

  it('displays truncated sessionId when name is null', async () => {
    seedActiveProject('project-1');
    const unnamedSession = {
      ...mockSessions[0],
      name: null,
      sessionId: 'abcdefgh-1234-5678-9abc-def012345678',
    };
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/project-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=project-1': { sessions: [unnamedSession] },
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByText('abcdefgh')).toBeTruthy();
    });
  });

  it('shows empty state when no active project', async () => {
    mockApiRoutes({
      '/api/projects': { projects: [], count: 0 },
    });

    renderPage();
    await waitFor(() =>
      expect(screen.getByText('No sessions yet. Start a new session to begin.')).toBeTruthy(),
    );
    expect(screen.getByTestId('new-session-btn')).toBeDisabled();
    expect(screen.getByText('Open or create a project to start a session.')).toBeTruthy();
  });

  it('shows error state when project-scoped session fetch fails', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [
              {
                id: 'default',
                name: 'default',
                path: '/tmp/project-1',
                defaultWorktree: true,
                managed: false,
                createdAt: '2026-04-07T10:00:00Z',
              },
            ],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: false, status: 500, json: async () => ({}) });
      }
      return Promise.resolve({ ok: true, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() =>
      expect(screen.getByText(/API GET \/api\/sessions\?projectId=project-1 failed/)).toBeTruthy(),
    );
  });

  it('shows CTA button in empty state when active project exists', async () => {
    seedActiveProject('project-1');
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/project-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=project-1': { sessions: [] },
    });

    renderPage();
    await waitFor(() => {
      expect(screen.getByTestId('start-conversation-btn')).toBeTruthy();
    });
  });

  it('new session button creates project-scoped session', async () => {
    seedActiveProject('project-1');
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [
          {
            id: 'default',
            name: 'default',
            path: '/tmp/project-1',
            defaultWorktree: true,
            managed: false,
            createdAt: '2026-04-07T10:00:00Z',
          },
        ],
      },
      '/api/sessions?projectId=project-1': { sessions: mockSessions },
    });

    renderPage();
    await waitFor(() => screen.getByTestId('new-session-btn'));

    fireEvent.click(screen.getByTestId('new-session-btn'));

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as [string, RequestInit | undefined][];
    const postCall = calls.find(
      ([url, init]) => url === '/api/sessions' && init?.method === 'POST',
    );
    expect(postCall).toBeTruthy();
    expect(postCall?.[1]?.body).toBe(JSON.stringify({ projectId: 'project-1', worktreeId: 'default' }));
  });

  it('close button deletes an existing session without breaking the list update', async () => {
    seedActiveProject('project-1');
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
      },
      '/api/sessions?projectId=project-1': { sessions: mockSessions },
    });
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (init?.method === 'DELETE' && url === '/api/sessions/ses-001') {
        return Promise.resolve({ ok: true, json: async () => ({}) });
      }
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [
              {
                id: 'default',
                name: 'default',
                path: '/tmp/project-1',
                defaultWorktree: true,
                managed: false,
                createdAt: '2026-04-07T10:00:00Z',
              },
            ],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: mockSessions }) });
      }
      if (init?.method === 'POST' && url === '/api/sessions') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            sessionId: 'ses-new',
            createdAt: '2026-04-07T12:00:00Z',
            updatedAt: '2026-04-07T12:00:00Z',
            status: 'ACTIVE',
            activeRun: null,
            messages: [],
            projectId: 'project-1',
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('close-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('close-session-ses-001'));

    await waitFor(() => expect(screen.queryByText('ses-001')).toBeNull());
    expect(screen.getByText('ses-002')).toBeTruthy();
  });

  it('opens session context panel lazily from context button', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: mockSessions }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              name: null,
              model: 'MiniMax-M2.7',
              providerId: 'provider-1',
              permissionMode: 'BYPASS',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 2,
              currentUsage: {
                inputTokens: 10,
                outputTokens: 20,
                cacheReadTokens: 4,
                cacheWriteTokens: 2,
                toolUses: 1,
                contextLength: 36,
              },
              sessionTotals: {
                inputTokens: 10,
                outputTokens: 20,
                cacheReadTokens: 4,
                cacheWriteTokens: 2,
                toolUses: 1,
                contextLength: 36,
              },
            },
            rawAiHistory: [
              { direction: 'response', content: 'hello', timestamp: '2026-04-07T10:01:00Z' },
              { direction: 'request', content: '{"pattern":"*.ts"}', timestamp: '2026-04-07T10:01:02Z' },
            ],
          }),
        });
      }
      if (init?.method === 'POST' && url === '/api/sessions') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            sessionId: 'ses-new',
            createdAt: '2026-04-07T12:00:00Z',
            updatedAt: '2026-04-07T12:00:00Z',
            status: 'ACTIVE',
            activeRun: null,
            messages: [],
            projectId: 'project-1',
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));

    await waitFor(() => expect(screen.getByTestId('session-context-side-panel')).toBeTruthy());
    expect(screen.getByText('Context')).toBeTruthy();
    expect(screen.getByTestId('session-context-summary')).toBeTruthy();
    expect(screen.getByText('Session Usage')).toBeTruthy();
    expect(screen.queryByText('Current Usage')).toBeNull();
    expect(screen.queryByText('Session Totals')).toBeNull();
    expect(screen.queryByText('Context Length')).toBeNull();
    expect(screen.getByText('AI History')).toBeTruthy();
  });

  it('caches session context and does not refetch same session', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [mockSessions[0]] }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 0,
            },
            rawAiHistory: [],
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.getByTestId('session-context-side-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
    expect(calls.filter(([url]) => url === '/api/sessions/ses-001/context')).toHaveLength(1);
  });

  it('closes the panel when clicking the active session context button again', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [mockSessions[0]] }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 0,
            },
            rawAiHistory: [],
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.getByTestId('session-context-side-panel')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.queryByTestId('session-context-side-panel')).toBeNull());
  });

  it('context button does not navigate while row click still navigates', async () => {
    seedActiveProject('project-1');
    const sessionWithProject = { ...mockSessions[0], projectId: 'project-1' };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [sessionWithProject] }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 0,
            },
            rawAiHistory: [],
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('session-item-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.getByTestId('session-context-side-panel')).toBeTruthy());
    expect(mockNavigate).toHaveBeenCalledTimes(0);

    fireEvent.click(screen.getByTestId('session-item-ses-001'));
    expect(mockNavigate).toHaveBeenCalledWith('/projects/project-1/sessions/ses-001');
  });

  it('shows empty history message for sessions without assistant activity', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: [mockSessions[0]] }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 0,
            },
            rawAiHistory: [],
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => {
      expect(screen.getByTestId('session-context-history-empty')).toBeTruthy();
    });
  });

  it('switches open context panel between sessions', async () => {
    seedActiveProject('project-1');
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
            workspaceEnabled: true,
            worktrees: [{
              id: 'default',
              name: 'default',
              path: '/tmp/project-1',
              defaultWorktree: true,
              managed: false,
              createdAt: '2026-04-07T10:00:00Z',
            }],
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: mockSessions }) });
      }
      if (url === '/api/sessions/ses-001/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-001',
              name: 'First Session',
              status: 'IDLE',
              createdAt: '2026-04-07T10:00:00Z',
              messageCount: 1,
            },
            rawAiHistory: [{ direction: 'response', content: 'First history' }],
          }),
        });
      }
      if (url === '/api/sessions/ses-002/context') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            summary: {
              sessionId: 'ses-002',
              name: 'Second Session',
              status: 'IDLE',
              createdAt: '2026-04-07T11:00:00Z',
              messageCount: 1,
            },
            rawAiHistory: [{ direction: 'response', content: 'Second history' }],
          }),
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.getByLabelText('Expand AI history entry 1')).toBeTruthy());
    fireEvent.click(screen.getByLabelText('Expand AI history entry 1'));
    fireEvent.click(screen.getByLabelText('Expand response for AI history entry 1'));
    await waitFor(() => expect(screen.getByText('First history')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-002'));
    await waitFor(() => expect(screen.getByLabelText('Expand AI history entry 1')).toBeTruthy());
    fireEvent.click(screen.getByLabelText('Expand AI history entry 1'));
    fireEvent.click(screen.getByLabelText('Expand response for AI history entry 1'));
    await waitFor(() => expect(screen.getByText('Second history')).toBeTruthy());
  });

  it('closes the session context tab from the tab bar', async () => {
    seedActiveProject('project-1');
    mockApiRoutes({
      '/api/projects/project-1': {
        id: 'project-1',
        name: 'Project One',
        path: '/tmp/project-1',
        lastOpened: '2026-04-07T10:00:00Z',
        createdAt: '2026-04-07T10:00:00Z',
        workspaceEnabled: true,
        worktrees: [{
          id: 'default',
          name: 'default',
          path: '/tmp/project-1',
          defaultWorktree: true,
          managed: false,
          createdAt: '2026-04-07T10:00:00Z',
        }],
      },
      '/api/sessions?projectId=project-1': { sessions: [mockSessions[0]] },
      '/api/sessions/ses-001/context': {
        summary: {
          sessionId: 'ses-001',
          status: 'IDLE',
          createdAt: '2026-04-07T10:00:00Z',
          messageCount: 0,
        },
        rawAiHistory: [],
      },
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('context-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('context-session-ses-001'));
    await waitFor(() => expect(screen.getByTestId('session-context-side-panel')).toBeTruthy());

    fireEvent.click(screen.getByLabelText('Close Context'));
    await waitFor(() => expect(screen.queryByTestId('session-context-side-panel')).toBeNull());
  });

  it('creates a replacement session when deleting the last remaining session', async () => {
    seedActiveProject('project-1');
    const singleSession = [mockSessions[0]];
    let sessionListResponse = singleSession;

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockImplementation((url: string, init?: RequestInit) => {
      if (url === '/api/projects/project-1') {
        return Promise.resolve({
          ok: true,
          json: async () => ({
            id: 'project-1',
            name: 'Project One',
            path: '/tmp/project-1',
            lastOpened: '2026-04-07T10:00:00Z',
            createdAt: '2026-04-07T10:00:00Z',
          }),
        });
      }
      if (url === '/api/sessions?projectId=project-1') {
        return Promise.resolve({ ok: true, json: async () => ({ sessions: sessionListResponse }) });
      }
      if (init?.method === 'DELETE' && url === '/api/sessions/ses-001') {
        sessionListResponse = [];
        return Promise.resolve({ ok: true, json: async () => ({}) });
      }
      if (init?.method === 'POST' && url === '/api/sessions') {
        const created = {
          sessionId: 'ses-new',
          createdAt: '2026-04-07T12:00:00Z',
          updatedAt: '2026-04-07T12:00:00Z',
          status: 'ACTIVE',
          activeRun: null,
          messages: [],
          projectId: 'project-1',
        };
        sessionListResponse = [created];
        return Promise.resolve({ ok: true, json: async () => created });
      }
      return Promise.resolve({ ok: false, status: 404, json: async () => ({}) });
    });

    renderPage();
    await waitFor(() => expect(screen.getByTestId('close-session-ses-001')).toBeTruthy());

    fireEvent.click(screen.getByTestId('close-session-ses-001'));

    await waitFor(() => {
      const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls as Array<[string, RequestInit | undefined]>;
      expect(calls.some(([url, init]) => url === '/api/sessions' && init?.method === 'POST')).toBe(true);
    });
  });
});
