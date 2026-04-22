import { describe, it, expect, beforeEach, vi } from 'vitest';
import { renderHook, act, waitFor } from '@testing-library/react';
import { MultiProjectProvider, useMultiProject } from './MultiProjectContext';
import { api } from '../api/client';
import type { ProjectDto, ProjectWorkspaceStateDto, SessionDto } from '../types/api';

vi.mock('../api/client', () => ({
  api: {
    projects: {
      get: vi.fn(),
      list: vi.fn(),
      getWorkspaceState: vi.fn(),
      updateWorkspaceState: vi.fn(),
    },
    sessions: {
      list: vi.fn(),
      delete: vi.fn(),
      create: vi.fn(),
    },
  },
}));

function makeProject(id: string, name = 'Test Project'): ProjectDto {
  return {
    id,
    name,
    path: `/tmp/${id}`,
    lastOpened: '2024-01-01T00:00:00Z',
    createdAt: '2024-01-01T00:00:00Z',
    workspaceEnabled: true,
    worktrees: [
      {
        id: 'default',
        name: 'default',
        path: `/tmp/${id}`,
        defaultWorktree: true,
        managed: false,
        branch: 'main',
        createdAt: '2024-01-01T00:00:00Z',
      },
    ],
  };
}

function makeSession(id: string): SessionDto {
  return {
    sessionId: id,
    createdAt: '2024-01-01T00:00:00Z',
    updatedAt: '2024-01-01T00:00:00Z',
    status: 'ACTIVE',
    activeRun: null,
    messages: [],
    projectId: 'project-1',
  };
}

function makeSessionForProject(id: string, projectId: string): SessionDto {
  return {
    ...makeSession(id),
    projectId,
    worktreeId: 'default',
  };
}

describe('MultiProjectContext', () => {
  let workspaceState: ProjectWorkspaceStateDto;

  beforeEach(() => {
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

    vi.clearAllMocks();
    workspaceState = { openProjectIds: [], activeProjectId: null };
    vi.mocked(api.sessions.list).mockResolvedValue({ sessions: [] });
    vi.mocked(api.sessions.create).mockImplementation(async (body?: Record<string, unknown>) => {
      const projectId = typeof body?.projectId === 'string' ? body.projectId : undefined;
      return {
        sessionId: `${projectId ?? 'project'}-new-session`,
        createdAt: '2024-01-01T00:00:00Z',
        updatedAt: '2024-01-01T00:00:00Z',
        status: 'ACTIVE',
        activeRun: null,
        messages: [],
        projectId,
      };
    });
    vi.mocked(api.projects.get).mockImplementation(async (id: string) => makeProject(id));
    vi.mocked(api.projects.getWorkspaceState).mockImplementation(async () => workspaceState);
    vi.mocked(api.projects.updateWorkspaceState).mockImplementation(async (next: ProjectWorkspaceStateDto) => {
      workspaceState = {
        openProjectIds: [...next.openProjectIds],
        activeProjectId: next.activeProjectId ?? null,
      };
      return workspaceState;
    });
  });

  describe('useMultiProject hook', () => {
    it('throws when used outside provider', () => {
      const consoleSpy = vi.spyOn(console, 'error').mockImplementation(() => {});

      expect(() => {
        renderHook(() => useMultiProject());
      }).toThrow('useMultiProject must be used within a MultiProjectProvider');

      consoleSpy.mockRestore();
    });

    it('provides initial state', async () => {
      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(result.current.openProjectIds).toEqual([]);
      expect(result.current.projects).toEqual({});
      expect(result.current.sessionsByProject).toEqual({});
      expect(result.current.activeSessionByProject).toEqual({});
      expect(result.current.activeProjectId).toBeNull();
      expect(result.current.recentSessionOrder).toEqual([]);
      expect(result.current.unseenTaskCompletionCountByProject).toEqual({});
    });
  });

  describe('task completion notifications', () => {
    it('increments unseen count for open projects when completion is not visible', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
      });

      act(() => {
        result.current.registerTaskCompletion(
          {
            completionId: 'task-1',
            taskId: 'task-1',
            description: 'Background task',
            projectId: 'project-1',
            sessionId: 'session-1',
            completedAt: '2026-04-12T00:00:00Z',
          },
          false,
        );
      });

      expect(result.current.unseenTaskCompletionCountByProject).toEqual({ 'project-1': 1 });
    });

    it('clears unseen count when project becomes active', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
      });

      act(() => {
        result.current.registerTaskCompletion(
          {
            completionId: 'task-1',
            taskId: 'task-1',
            description: 'Background task',
            projectId: 'project-1',
            sessionId: 'session-1',
            completedAt: '2026-04-12T00:00:00Z',
          },
          false,
        );
      });

      await waitFor(() => {
        expect(result.current.unseenTaskCompletionCountByProject).toEqual({ 'project-1': 1 });
      });

      act(() => {
        result.current.setActiveProject('project-1');
      });

      expect(result.current.unseenTaskCompletionCountByProject).toEqual({});
    });

    it('counts AI run completions using run metadata', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
      });

      act(() => {
        result.current.registerTaskCompletion(
          {
            completionId: 'run-1',
            taskId: 'run-1',
            runId: 'run-1',
            description: 'AI run completed',
            projectId: 'project-1',
            sessionId: 'session-2',
            completedAt: '2026-04-12T00:00:00Z',
          },
          false,
        );
      });

      expect(result.current.unseenTaskCompletionCountByProject).toEqual({ 'project-1': 1 });
    });
  });

  describe('openProject', () => {
    it('adds project and loads sessions', async () => {
      const project = makeProject('project-1');
      const sessions = [makeSessionForProject('session-1', 'project-1')];
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
        expect(result.current.projects['project-1']).toEqual(project);
        expect(result.current.sessionsByProject['project-1']).toEqual(sessions);
        expect(result.current.activeProjectId).toBe('project-1');
      });
    });

    it('does not duplicate project id', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
      });
    });

    it('persists session state to localStorage and open state to backend', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        const raw = localStorage.getItem('coderhino-multi-project');
        const stored = JSON.parse(raw || '{}');
        expect(stored.lastActiveSessionByProject).toEqual({});
        expect(stored.recentSessionOrder).toEqual([]);
      });

      expect(api.projects.updateWorkspaceState).toHaveBeenCalledWith({
        openProjectIds: ['project-1'],
        activeProjectId: 'project-1',
      });
    });

    it('persists a project opened before workspace bootstrap finishes', async () => {
      const project = makeProject('project-1');
      let resolveWorkspaceState: ((value: ProjectWorkspaceStateDto) => void) | undefined;
      vi.mocked(api.projects.getWorkspaceState).mockImplementation(
        () => new Promise<ProjectWorkspaceStateDto>((resolve) => {
          resolveWorkspaceState = resolve;
        }),
      );

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-1']);
      });

      await act(async () => {
        resolveWorkspaceState?.({ openProjectIds: [], activeProjectId: null });
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
      });

      expect(api.projects.updateWorkspaceState).toHaveBeenCalledWith({
        openProjectIds: ['project-1'],
        activeProjectId: 'project-1',
      });
    });
  });

  describe('closeProject', () => {
    it('removes project from open list and active session map', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
      });

      act(() => {
        result.current.closeProject('project-1');
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual([]);
        expect(result.current.activeSessionByProject['project-1']).toBeUndefined();
      });
    });

    it('persists close changes to backend workspace state', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.closeProject('project-1');
      });

      await waitFor(() => {
        expect(api.projects.updateWorkspaceState).toHaveBeenLastCalledWith({
          openProjectIds: [],
          activeProjectId: null,
        });
      });
    });
  });

  describe('setActiveSession', () => {
    it('sets active session and recent ordering', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
      });

      await waitFor(() => {
        expect(result.current.activeSessionByProject['project-1']).toBe('session-1');
        expect(result.current.recentSessionOrder).toEqual(['session-1']);
      });
    });

    it('moves most recent session to the front', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
        result.current.setActiveSession('project-1', 'session-2');
      });

      await waitFor(() => {
        expect(result.current.recentSessionOrder).toEqual(['session-2', 'session-1']);
      });
    });

    it('persists active-session state', async () => {
      const project = makeProject('project-1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });
      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
      });

      await waitFor(() => {
        const raw = localStorage.getItem('coderhino-multi-project');
        const stored = JSON.parse(raw || '{}');
        expect(stored.lastActiveSessionByProject).toEqual({ 'project-1': 'session-1' });
        expect(stored.recentSessionOrder).toEqual(['session-1']);
      });
    });
  });

  describe('getActiveProject', () => {
    it('returns explicitly active project even when no session is active', async () => {
      const project = makeProject('project-1', 'Project 1');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.getActiveProject()?.id).toBe('project-1');
      });
    });

    it('returns most recently active project object', async () => {
      const project1 = makeProject('project-1', 'Project 1');
      const project2 = makeProject('project-2', 'Project 2');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project1);
        result.current.openProject(project2);
      });
      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
        result.current.setActiveSession('project-2', 'session-2');
      });

      await waitFor(() => {
        const activeProject = result.current.getActiveProject();
        expect(activeProject?.id).toBe('project-2');
      });
    });

    it('returns null when no sessions are active', () => {
      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      expect(result.current.getActiveProject()).toBeNull();
    });
  });

  describe('getActiveProjectForSession', () => {
    it('returns project for a known session', async () => {
      const project = makeProject('project-1');
      const sessions = [makeSessionForProject('session-1', 'project-1')];
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        const owningProject = result.current.getActiveProjectForSession('session-1');
        expect(owningProject?.id).toBe('project-1');
      });
    });

    it('returns null for unknown session', () => {
      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      expect(result.current.getActiveProjectForSession('unknown-session')).toBeNull();
    });
  });

  describe('refreshSessions', () => {
    it('reloads sessions for a specific project', async () => {
      const project = makeProject('project-1');
      const initialSessions = [makeSessionForProject('session-1', 'project-1')];
      const updatedSessions = [
        makeSessionForProject('session-1', 'project-1'),
        makeSessionForProject('session-2', 'project-1'),
      ];

      vi.mocked(api.sessions.list)
        .mockResolvedValueOnce({ sessions: initialSessions })
        .mockResolvedValueOnce({ sessions: updatedSessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await act(async () => {
        await result.current.refreshSessions('project-1');
      });

      await waitFor(() => {
        const sessions = result.current.sessionsByProject['project-1'] ?? [];
        expect(sessions).toHaveLength(2);
        expect(sessions[1]?.sessionId).toBe('session-2');
      });
    });
  });

  describe('updateProject', () => {
    it('drops sessions that reference removed worktrees', async () => {
      const project = makeProject('project-1');
      const sessions = [
        makeSessionForProject('session-default', 'project-1'),
        {
          ...makeSessionForProject('session-managed', 'project-1'),
          worktreeId: 'wt-1',
        },
      ];
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject({
          ...project,
          worktrees: [
            project.worktrees[0]!,
            {
              id: 'wt-1',
              name: 'feature-a',
              path: '/tmp/project-1-feature-a',
              defaultWorktree: false,
              managed: true,
              branch: null,
              createdAt: '2024-01-01T00:00:00Z',
            },
          ],
        });
      });

      await waitFor(() => {
        expect(result.current.sessionsByProject['project-1']).toHaveLength(2);
      });

      act(() => {
        result.current.updateProject(project);
      });

      expect(result.current.sessionsByProject['project-1']).toEqual([
        expect.objectContaining({ sessionId: 'session-default' }),
      ]);
    });
  });

  describe('addSession', () => {
    it('prepends a newly created session to project state', async () => {
      const project = makeProject('project-1');
      const existing = makeSessionForProject('session-1', 'project-1');

      vi.mocked(api.sessions.list).mockResolvedValue({ sessions: [existing] });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.sessionsByProject['project-1']).toEqual([existing]);
      });

      const created = makeSessionForProject('session-2', 'project-1');
      act(() => {
        result.current.addSession('project-1', created);
      });

      expect(result.current.sessionsByProject['project-1']).toEqual([created, existing]);
    });
  });

  describe('ensureProjectSession', () => {
    it('returns existing active session when still valid', async () => {
      const project = makeProject('project-1');
      const sessions = [makeSessionForProject('session-1', 'project-1')];
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
        result.current.setActiveSession('project-1', 'session-1');
      });

      let ensured: SessionDto | undefined;
      await act(async () => {
        ensured = await result.current.ensureProjectSession('project-1');
      });

      expect(ensured?.sessionId).toBe('session-1');
      expect(api.sessions.create).not.toHaveBeenCalled();
    });

    it('creates a new session when project has none', async () => {
      const project = makeProject('project-1');
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions: [] });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.sessionsByProject['project-1']).toEqual([]);
      });

      let ensured: SessionDto | undefined;
      await act(async () => {
        ensured = await result.current.ensureProjectSession('project-1');
      });

      expect(ensured?.sessionId).toBe('project-1-new-session');
      expect(result.current.activeSessionByProject['project-1']).toBe('project-1-new-session');
    });
  });

  describe('removeSession', () => {
    it('removes the session from project state and recent ordering', async () => {
      const project = makeProject('project-1');
      const sessions = [makeSessionForProject('session-1', 'project-1')];
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project);
      });

      await waitFor(() => {
        expect(result.current.sessionsByProject['project-1']).toEqual(sessions);
      });

      act(() => {
        result.current.setActiveSession('project-1', 'session-1');
        result.current.removeSession('project-1', 'session-1');
      });

      await waitFor(() => {
        expect(result.current.sessionsByProject['project-1']).toEqual([]);
        expect(result.current.activeSessionByProject['project-1']).toBeUndefined();
        expect(result.current.recentSessionOrder).toEqual([]);
      });
    });
  });

  describe('localStorage persistence', () => {
    it('restores state from storage on mount', async () => {
      localStorage.setItem(
        'coderhino-multi-project',
        JSON.stringify({
          lastActiveSessionByProject: { 'project-1': 'session-1' },
          recentSessionOrder: ['session-1'],
        }),
      );
      workspaceState = { openProjectIds: ['project-1'], activeProjectId: 'project-1' };

      vi.mocked(api.projects.get).mockResolvedValue(makeProject('project-1'));
      vi.mocked(api.sessions.list).mockResolvedValue({
        sessions: [makeSessionForProject('session-1', 'project-1')],
      });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.openProjectIds).toEqual(['project-1']);
        expect(result.current.activeSessionByProject).toEqual({ 'project-1': 'session-1' });
        expect(result.current.activeProjectId).toBe('project-1');
        expect(result.current.recentSessionOrder).toEqual(['session-1']);
      });

      expect(api.projects.getWorkspaceState).toHaveBeenCalled();
    });

    it('keeps open projects across remount when backend still recognizes them', async () => {
      localStorage.setItem(
        'coderhino-multi-project',
        JSON.stringify({
          lastActiveSessionByProject: { 'project-1': 'session-1', 'project-2': 'session-2' },
          recentSessionOrder: ['session-2', 'session-1'],
        }),
      );
      workspaceState = { openProjectIds: ['project-1', 'project-2'], activeProjectId: 'project-2' };

      vi.mocked(api.projects.get).mockImplementation(async (id: string) => makeProject(id));
      vi.mocked(api.sessions.list).mockImplementation(async (projectId?: string) => {
        if (projectId === 'project-1') {
          return { sessions: [makeSessionForProject('session-1', 'project-1')] };
        }
        if (projectId === 'project-2') {
          return { sessions: [makeSessionForProject('session-2', 'project-2')] };
        }
        return { sessions: [] };
      });

      const firstMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(firstMount.result.current.loading).toBe(false);
        expect(firstMount.result.current.openProjectIds).toEqual(['project-1', 'project-2']);
      });

      firstMount.unmount();

      const secondMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(secondMount.result.current.loading).toBe(false);
        expect(secondMount.result.current.openProjectIds).toEqual(['project-1', 'project-2']);
        expect(secondMount.result.current.activeProjectId).toBe('project-2');
        expect(secondMount.result.current.activeSessionByProject).toEqual({
          'project-1': 'session-1',
          'project-2': 'session-2',
        });
      });

      expect(api.projects.getWorkspaceState).toHaveBeenCalledTimes(2);
    });

    it('restores a project that was opened before the first bootstrap completed', async () => {
      const project = makeProject('project-1');
      let resolveWorkspaceState: ((value: ProjectWorkspaceStateDto) => void) | undefined;
      vi.mocked(api.projects.getWorkspaceState).mockImplementationOnce(
        () => new Promise<ProjectWorkspaceStateDto>((resolve) => {
          resolveWorkspaceState = resolve;
        }),
      );
      vi.mocked(api.projects.get).mockResolvedValue(project);
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions: [makeSessionForProject('session-1', 'project-1')] });

      const firstMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        firstMount.result.current.openProject(project);
        firstMount.result.current.setActiveSession('project-1', 'session-1');
      });

      await waitFor(() => {
        expect(firstMount.result.current.openProjectIds).toEqual(['project-1']);
      });

      await act(async () => {
        resolveWorkspaceState?.({ openProjectIds: [], activeProjectId: null });
      });

      await waitFor(() => {
        expect(firstMount.result.current.loading).toBe(false);
      });

      firstMount.unmount();

      const secondMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(secondMount.result.current.loading).toBe(false);
        expect(secondMount.result.current.openProjectIds).toEqual(['project-1']);
        expect(secondMount.result.current.activeProjectId).toBe('project-1');
        expect(secondMount.result.current.activeSessionByProject).toEqual({ 'project-1': 'session-1' });
      });
    });

    it('preserves other restored open projects when a routed project opens before bootstrap finishes', async () => {
      const project1 = makeProject('project-1');
      const project2 = makeProject('project-2');
      let resolveWorkspaceState: ((value: ProjectWorkspaceStateDto) => void) | undefined;
      vi.mocked(api.projects.getWorkspaceState).mockImplementationOnce(
        () => new Promise<ProjectWorkspaceStateDto>((resolve) => {
          resolveWorkspaceState = resolve;
        }),
      );
      vi.mocked(api.projects.get).mockImplementation(async (id: string) => makeProject(id));
      vi.mocked(api.sessions.list).mockImplementation(async (projectId?: string) => {
        if (projectId === 'project-1') {
          return { sessions: [makeSessionForProject('session-1', 'project-1')] };
        }
        if (projectId === 'project-2') {
          return { sessions: [makeSessionForProject('session-2', 'project-2')] };
        }
        return { sessions: [] };
      });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      act(() => {
        result.current.openProject(project2);
        result.current.setActiveSession('project-2', 'session-2');
      });

      await waitFor(() => {
        expect(result.current.openProjectIds).toEqual(['project-2']);
      });

      await act(async () => {
        resolveWorkspaceState?.({ openProjectIds: ['project-1', 'project-2'], activeProjectId: 'project-1' });
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.openProjectIds).toEqual(['project-1', 'project-2']);
        expect(result.current.activeProjectId).toBe('project-2');
        expect(result.current.projects['project-1']).toEqual(project1);
        expect(result.current.projects['project-2']).toEqual(project2);
      });

      expect(api.projects.updateWorkspaceState).toHaveBeenCalledWith({
        openProjectIds: ['project-1', 'project-2'],
        activeProjectId: 'project-2',
      });
    });

    it('seeds empty server state from legacy localStorage project ids', async () => {
      localStorage.setItem(
        'coderhino-multi-project',
        JSON.stringify({
          openProjectIds: ['stale-1', 'stale-2'],
          lastActiveSessionByProject: {},
          recentSessionOrder: [],
          activeProjectId: 'stale-1',
        }),
      );

      vi.mocked(api.projects.get).mockImplementation(async (id: string) => {
        if (id === 'project-1' || id === 'project-2') {
          return makeProject(id);
        }
        throw new Error('404');
      });
      vi.mocked(api.sessions.list).mockResolvedValue({ sessions: [] });

      localStorage.setItem(
        'coderhino-multi-project',
        JSON.stringify({
          openProjectIds: ['project-1', 'project-2'],
          lastActiveSessionByProject: {},
          recentSessionOrder: [],
          activeProjectId: 'project-2',
        }),
      );

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.openProjectIds).toEqual(['project-1', 'project-2']);
        expect(result.current.activeProjectId).toBe('project-2');
      });

      expect(api.projects.updateWorkspaceState).toHaveBeenCalledWith({
        openProjectIds: ['project-1', 'project-2'],
        activeProjectId: 'project-2',
      });
    });

    it('does not reopen a project after it was explicitly closed', async () => {
      localStorage.setItem(
        'coderhino-multi-project',
        JSON.stringify({
          lastActiveSessionByProject: { 'project-1': 'session-1' },
          recentSessionOrder: ['session-1'],
        }),
      );
      workspaceState = { openProjectIds: ['project-1'], activeProjectId: 'project-1' };

      vi.mocked(api.projects.get).mockResolvedValue(makeProject('project-1'));
      vi.mocked(api.sessions.list).mockResolvedValue({
        sessions: [makeSessionForProject('session-1', 'project-1')],
      });

      const firstMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(firstMount.result.current.loading).toBe(false);
        expect(firstMount.result.current.openProjectIds).toEqual(['project-1']);
      });

      act(() => {
        firstMount.result.current.closeProject('project-1');
      });

      await waitFor(() => {
        expect(firstMount.result.current.openProjectIds).toEqual([]);
      });

      firstMount.unmount();

      const secondMount = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(secondMount.result.current.loading).toBe(false);
        expect(secondMount.result.current.openProjectIds).toEqual([]);
        expect(secondMount.result.current.activeProjectId).toBeNull();
      });

      expect(api.projects.getWorkspaceState).toHaveBeenCalledTimes(2);
    });

    it('handles corrupted localStorage data gracefully', () => {
      localStorage.setItem('coderhino-multi-project', 'invalid-json');

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      expect(result.current.openProjectIds).toEqual([]);
      expect(result.current.activeSessionByProject).toEqual({});
      expect(result.current.activeProjectId).toBeNull();
      expect(result.current.recentSessionOrder).toEqual([]);
    });

    it('drops stale project ids returned by the backend workspace state', async () => {
      workspaceState = { openProjectIds: ['missing-project', 'project-2'], activeProjectId: 'missing-project' };
      vi.mocked(api.projects.get).mockImplementation(async (id: string) => {
        if (id === 'project-2') {
          return makeProject('project-2');
        }
        throw new Error('404');
      });

      const { result } = renderHook(() => useMultiProject(), {
        wrapper: MultiProjectProvider,
      });

      await waitFor(() => {
        expect(result.current.loading).toBe(false);
        expect(result.current.openProjectIds).toEqual(['project-2']);
        expect(result.current.activeProjectId).toBe('project-2');
      });

      expect(api.projects.updateWorkspaceState).toHaveBeenCalledWith({
        openProjectIds: ['project-2'],
        activeProjectId: 'project-2',
      });
    });
  });
});
