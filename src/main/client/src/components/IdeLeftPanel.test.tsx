import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import IdeLeftPanel from './IdeLeftPanel';
import type { ProjectDto, SessionDto, WebSettings } from '../types/api';
import { api } from '../api/client';

const navigateMock = vi.fn();
const closeProjectMock = vi.fn();
const setActiveProjectMock = vi.fn();
const setActiveSessionMock = vi.fn();
const openProjectMock = vi.fn();
const ensureProjectSessionMock = vi.fn();
const addSessionMock = vi.fn();
const updateProjectMock = vi.fn();
const removeSessionMock = vi.fn();
const refreshProjectMock = vi.fn();

const project: ProjectDto = {
  id: 'project-1',
  name: 'alpha-project',
  path: '/tmp/alpha-project',
  lastOpened: '2026-04-11T00:00:00Z',
  createdAt: '2026-04-11T00:00:00Z',
  workspaceEnabled: false,
  worktrees: [
    {
      id: 'default',
      name: 'default',
      path: '/tmp/alpha-project',
      defaultWorktree: true,
      managed: false,
      branch: 'main',
      createdAt: '2026-04-11T00:00:00Z',
    },
  ],
};

const workspaceProject: ProjectDto = {
  ...project,
  workspaceEnabled: true,
  worktrees: [
    project.worktrees[0]!,
    {
      id: 'wt-1',
      name: 'feature-a',
      path: '/tmp/worktrees/feature-a',
      defaultWorktree: false,
      managed: true,
      branch: null,
      createdAt: '2026-04-11T00:00:00Z',
    },
  ],
};

const secondProject: ProjectDto = {
  ...project,
  id: 'project-2',
  name: 'beta-project',
  path: '/tmp/beta-project',
};

const sessions: SessionDto[] = [
  {
    sessionId: 'session-1',
    createdAt: '2026-04-11T00:00:00Z',
    updatedAt: '2026-04-11T00:00:00Z',
    status: 'ACTIVE',
    activeRun: null,
    messages: [],
    projectId: 'project-1',
    branch: 'main',
    worktreeId: 'default',
    worktree: project.worktrees[0],
  },
];

const secondSession: SessionDto = {
  sessionId: 'session-2',
  createdAt: '2026-04-11T00:00:00Z',
  updatedAt: '2026-04-11T00:00:00Z',
  status: 'IDLE',
  activeRun: null,
  messages: [],
  projectId: 'project-1',
  branch: 'main',
  worktreeId: 'default',
  worktree: project.worktrees[0],
};

const mockSettings: WebSettings = {
  defaultPermissionMode: 'BYPASS',
  theme: 'dark',
  defaultModel: 'MiniMax-M2.7',
  sidebarFontFamily: 'mono',
  sidebarFontSize: 16,
  chatFontFamily: 'sans',
  chatFontSize: 13,
  referenceSourcePaths: [],
};

const mockMultiProjectState = {
  openProjectIds: ['project-1'],
  projects: { 'project-1': project } as Record<string, ProjectDto>,
  sessionsByProject: { 'project-1': sessions } as Record<string, SessionDto[]>,
  activeSessionByProject: { 'project-1': 'session-1' } as Record<string, string | null>,
  activeProjectId: 'project-1',
  recentSessionOrder: ['session-1'],
  unseenTaskCompletionCountByProject: {} as Record<string, number>,
  loading: false,
  openProject: openProjectMock,
  updateProject: updateProjectMock,
  closeProject: closeProjectMock,
  setActiveProject: setActiveProjectMock,
  setActiveSession: setActiveSessionMock,
  addSession: addSessionMock,
  removeSession: removeSessionMock,
  refreshSessions: vi.fn(),
  refreshProject: refreshProjectMock,
  ensureProjectSession: ensureProjectSessionMock,
  registerTaskCompletion: vi.fn(),
  clearTaskCompletionNotifications: vi.fn(),
  getActiveProject: vi.fn(() => project),
  getActiveProjectForSession: vi.fn(() => project),
  getSessionById: vi.fn(),
};

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useNavigate: () => navigateMock,
  };
});

vi.mock('../context/MultiProjectContext', () => ({
  useMultiProject: () => mockMultiProjectState,
}));

vi.mock('../api/client', () => ({
  api: {
    projects: {
      enableWorkspace: vi.fn(),
      create: vi.fn(),
      rename: vi.fn(),
      createWorktree: vi.fn(),
      deleteWorktree: vi.fn(),
    },
    sessions: {
      create: vi.fn(),
      delete: vi.fn(),
    },
  },
}));

describe('IdeLeftPanel', () => {
  beforeEach(() => {
    navigateMock.mockReset();
    closeProjectMock.mockReset();
    setActiveProjectMock.mockReset();
    setActiveSessionMock.mockReset();
    openProjectMock.mockReset();
    ensureProjectSessionMock.mockReset();
    addSessionMock.mockReset();
    updateProjectMock.mockReset();
    removeSessionMock.mockReset();
    refreshProjectMock.mockReset();
    vi.mocked(api.projects.enableWorkspace).mockReset();
    vi.mocked(api.projects.rename).mockReset();
    vi.mocked(api.projects.createWorktree).mockReset();
    vi.mocked(api.projects.deleteWorktree).mockReset();
    vi.mocked(api.sessions.create).mockReset();
    vi.mocked(api.sessions.delete).mockReset();
    mockMultiProjectState.projects = { 'project-1': project };
    mockMultiProjectState.openProjectIds = ['project-1'];
    mockMultiProjectState.sessionsByProject = { 'project-1': sessions };
    mockMultiProjectState.activeSessionByProject = { 'project-1': 'session-1' };
    mockMultiProjectState.activeProjectId = 'project-1';
    mockMultiProjectState.unseenTaskCompletionCountByProject = {};
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    vi.mocked(api.projects.enableWorkspace).mockResolvedValue({
      ...project,
      workspaceEnabled: true,
    });
    vi.mocked(api.projects.rename).mockResolvedValue(project);
    vi.mocked(api.projects.createWorktree).mockResolvedValue(workspaceProject);
    vi.mocked(api.sessions.delete).mockResolvedValue(undefined);
  });

  it('renders the add-project button after the project icons and shows the active project path', () => {
    render(<IdeLeftPanel />);

    const avatar = screen.getByTestId('rail-avatar-project-1');
    const addButton = screen.getByTestId('rail-add-project');
    const path = screen.getByTestId('active-project-path');

    expect(avatar.compareDocumentPosition(addButton) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(path).toHaveTextContent('/tmp/alpha-project');
    expect(path).toHaveAttribute('title', '/tmp/alpha-project');
  });

  it('renders the active project as a selected desktop-style tile', () => {
    render(<IdeLeftPanel />);

    const avatar = screen.getByTestId('rail-avatar-project-1');
    const chip = screen.getByTestId('rail-avatar-chip-project-1');

    expect(avatar.style.borderColor).not.toBe('transparent');
    expect(avatar.style.background).not.toBe('transparent');
    expect(chip.style.width).toBe('28px');
    expect(chip.style.height).toBe('28px');
    expect(chip.style.borderRadius).toBe('var(--radius-sm)');
    expect(chip).toBeInTheDocument();
  });

  it('renders inactive projects with a lighter avatar chip instead of a filled tile', () => {
    mockMultiProjectState.openProjectIds = ['project-1', 'project-2'];
    mockMultiProjectState.projects = {
      'project-1': project,
      'project-2': secondProject,
    };

    render(<IdeLeftPanel />);

    const inactiveAvatar = screen.getByTestId('rail-avatar-project-2');
    const inactiveChip = screen.getByTestId('rail-avatar-chip-project-2');

    expect(inactiveAvatar.style.background).toBe('transparent');
    expect(inactiveAvatar.style.borderColor).toBe('transparent');
    expect(inactiveChip.style.width).toBe('30px');
    expect(inactiveChip.style.height).toBe('30px');
    expect(inactiveChip.style.background).not.toBe('');
    expect(inactiveChip.style.color).not.toBe('');
  });

  it('renders footer utility buttons as quieter secondary controls', () => {
    render(<IdeLeftPanel />);

    const addButton = screen.getByTestId('rail-add-project');
    const settingsButton = screen.getByTestId('rail-settings');

    expect(addButton.style.opacity).toBe('0.72');
    expect(addButton.style.borderColor).toBe('transparent');
    expect(settingsButton.style.opacity).toBe('0.72');
    expect(settingsButton.style.borderColor).toBe('transparent');
  });

  it('renders a task completion badge on projects with unseen notifications', () => {
    mockMultiProjectState.unseenTaskCompletionCountByProject = { 'project-1': 2 };

    render(<IdeLeftPanel />);

    expect(screen.getByTestId('rail-avatar-badge-project-1')).toHaveTextContent('2');
  });

  it('renders a kebab trigger and opens the project menu', () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));

    expect(screen.getByTestId('project-menu')).toBeInTheDocument();
    expect(screen.getByText('Enable workspace')).toBeInTheDocument();
    expect(screen.getByText('Close')).toBeInTheDocument();
  });

  it('opens settings through the provided callback', () => {
    const onOpenSettings = vi.fn();

    render(<IdeLeftPanel onOpenSettings={onOpenSettings} />);

    fireEvent.click(screen.getByTestId('rail-settings'));

    expect(onOpenSettings).toHaveBeenCalledTimes(1);
    expect(navigateMock).not.toHaveBeenCalledWith('/settings');
  });

  it('enables workspace through the backend API and closes the menu after selection', async () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));
    fireEvent.click(screen.getByTestId('project-menu-workspace-toggle'));

    expect(screen.queryByTestId('project-menu')).not.toBeInTheDocument();
    expect(api.projects.enableWorkspace).toHaveBeenCalledWith('project-1');
  });

  it('closes the active project through the menu action', () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));
    fireEvent.click(screen.getByTestId('project-menu-close'));

    expect(closeProjectMock).toHaveBeenCalledWith('project-1');
    expect(screen.queryByTestId('project-menu')).not.toBeInTheDocument();
  });

  it('renames the active project through the menu action', async () => {
    vi.mocked(api.projects.rename).mockResolvedValue({
      ...project,
      name: 'renamed-project',
    });

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));
    fireEvent.click(screen.getByTestId('project-menu-rename'));
    fireEvent.change(screen.getByTestId('project-name-input'), { target: { value: 'renamed-project' } });
    fireEvent.keyDown(screen.getByTestId('project-name-input'), { key: 'Enter' });

    await waitFor(() => {
      expect(api.projects.rename).toHaveBeenCalledWith('project-1', 'renamed-project');
      expect(updateProjectMock).toHaveBeenCalledWith(expect.objectContaining({ name: 'renamed-project' }));
    });
  });

  it('cancels project rename on Escape', () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));
    fireEvent.click(screen.getByTestId('project-menu-rename'));
    fireEvent.keyDown(screen.getByTestId('project-name-input'), { key: 'Escape' });

    expect(screen.queryByTestId('project-name-input')).not.toBeInTheDocument();
    expect(api.projects.rename).not.toHaveBeenCalled();
  });

  it('shows an error when project rename is empty', async () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('project-menu-trigger'));
    fireEvent.click(screen.getByTestId('project-menu-rename'));
    fireEvent.change(screen.getByTestId('project-name-input'), { target: { value: '   ' } });
    fireEvent.keyDown(screen.getByTestId('project-name-input'), { key: 'Enter' });

    expect(await screen.findByTestId('project-rename-error')).toHaveTextContent('Project name is required');
    expect(api.projects.rename).not.toHaveBeenCalled();
  });

  it('adds a newly created project session to the sidebar immediately', async () => {
    vi.mocked(api.sessions.create).mockResolvedValue({
      ...sessions[0]!,
      sessionId: 'session-2',
      updatedAt: '2026-04-12T00:00:00Z',
    });

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-new-session-btn'));

    await waitFor(() => {
      expect(api.sessions.create).toHaveBeenCalledWith({ projectId: 'project-1' });
      expect(addSessionMock).toHaveBeenCalledWith('project-1', expect.objectContaining({ sessionId: 'session-2' }));
      expect(setActiveSessionMock).toHaveBeenCalledWith('project-1', 'session-2');
      expect(navigateMock).toHaveBeenCalledWith('/projects/project-1/sessions/session-2');
    });
  });

  it('renders worktree groups and supports creating sessions in a worktree', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    mockMultiProjectState.sessionsByProject = {
      'project-1': [
        {
          ...sessions[0]!,
          worktreeId: 'default',
          worktree: workspaceProject.worktrees[0]!,
        },
      ],
    };
    vi.mocked(api.sessions.create).mockResolvedValue({
      ...sessions[0]!,
      sessionId: 'session-2',
      worktreeId: 'wt-1',
      worktree: workspaceProject.worktrees[1]!,
    });

    render(<IdeLeftPanel />);

    expect(screen.getByTestId('worktree-group-default')).toBeInTheDocument();
    expect(screen.getByTestId('worktree-group-wt-1')).toBeInTheDocument();
    expect(screen.getByTestId('worktree-empty-wt-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('worktree-new-session-wt-1'));

    expect(api.sessions.create).toHaveBeenCalledWith({ projectId: 'project-1', worktreeId: 'wt-1' });
    await waitFor(() => {
      expect(addSessionMock).toHaveBeenCalledWith('project-1', expect.objectContaining({ sessionId: 'session-2' }));
    });
  });

  it('hides the project-level new session button when workspace mode is enabled', () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };

    render(<IdeLeftPanel />);

    expect(screen.queryByTestId('sidebar-new-session-btn')).not.toBeInTheDocument();
    expect(screen.getByTestId('sidebar-new-workspace-btn')).toBeInTheDocument();
  });

  it('renders the default worktree as the local branch label', () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };

    render(<IdeLeftPanel />);

    expect(screen.getByText('local: main')).toBeInTheDocument();
    expect(screen.getByText('worktree: feature-a')).toBeInTheDocument();
  });

  it('renders a trailing delete action for plain session rows', () => {
    render(<IdeLeftPanel />);

    expect(screen.getByTestId('sidebar-session-delete-session-1')).toBeInTheDocument();
  });

  it('renders a trailing delete action for worktree session rows', () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    mockMultiProjectState.sessionsByProject = {
      'project-1': [
        {
          ...sessions[0]!,
          worktreeId: 'default',
          worktree: workspaceProject.worktrees[0]!,
        },
      ],
    };

    render(<IdeLeftPanel />);

    expect(screen.getByTestId('sidebar-session-delete-session-1')).toBeInTheDocument();
  });

  it('does not delete or navigate when confirmation is cancelled', () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-session-delete-session-1'));
    expect(screen.getByTestId('sidebar-session-confirm-delete-session-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('sidebar-session-cancel-delete-session-1'));

    expect(api.sessions.delete).not.toHaveBeenCalled();
    expect(removeSessionMock).not.toHaveBeenCalled();
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it('deletes a non-active session without changing the active route', async () => {
    mockMultiProjectState.sessionsByProject = { 'project-1': [sessions[0]!, secondSession] };
    mockMultiProjectState.activeSessionByProject = { 'project-1': 'session-1' };

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-session-delete-session-2'));
    fireEvent.click(screen.getByTestId('sidebar-session-confirm-delete-session-2'));

    await waitFor(() => {
      expect(api.sessions.delete).toHaveBeenCalledWith('session-2');
      expect(removeSessionMock).toHaveBeenCalledWith('project-1', 'session-2');
    });
    expect(setActiveSessionMock).not.toHaveBeenCalled();
    expect(ensureProjectSessionMock).not.toHaveBeenCalled();
    expect(navigateMock).not.toHaveBeenCalled();
  });

  it('deletes the active session and falls back to another remaining session', async () => {
    mockMultiProjectState.sessionsByProject = { 'project-1': [sessions[0]!, secondSession] };
    mockMultiProjectState.activeSessionByProject = { 'project-1': 'session-1' };

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-session-delete-session-1'));
    fireEvent.click(screen.getByTestId('sidebar-session-confirm-delete-session-1'));

    await waitFor(() => {
      expect(api.sessions.delete).toHaveBeenCalledWith('session-1');
      expect(removeSessionMock).toHaveBeenCalledWith('project-1', 'session-1');
      expect(setActiveSessionMock).toHaveBeenCalledWith('project-1', 'session-2');
      expect(navigateMock).toHaveBeenCalledWith('/projects/project-1/sessions/session-2');
    });
    expect(ensureProjectSessionMock).not.toHaveBeenCalled();
  });

  it('deletes the last session and creates a replacement session', async () => {
    ensureProjectSessionMock.mockResolvedValueOnce({
      ...sessions[0]!,
      sessionId: 'session-replacement',
    });

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-session-delete-session-1'));
    fireEvent.click(screen.getByTestId('sidebar-session-confirm-delete-session-1'));

    await waitFor(() => {
      expect(ensureProjectSessionMock).toHaveBeenCalledWith('project-1');
    });
    expect(removeSessionMock).toHaveBeenCalledWith('project-1', 'session-1');
    expect(setActiveSessionMock).toHaveBeenCalledWith('project-1', 'session-replacement');
    expect(navigateMock).toHaveBeenCalledWith('/projects/project-1/sessions/session-replacement');
  });

  it('does not activate the session row when deleting it', () => {
    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-session-delete-session-1'));

    expect(setActiveSessionMock).not.toHaveBeenCalledWith('project-1', 'session-1');
  });

  it('opens the managed worktree menu and deletes the worktree', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    vi.mocked(api.projects.deleteWorktree).mockResolvedValue({
      ...workspaceProject,
      worktrees: [workspaceProject.worktrees[0]!],
    });

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('worktree-menu-trigger-wt-1'));
    expect(screen.getByTestId('worktree-menu-wt-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('worktree-delete-wt-1'));
    expect(screen.getByTestId('worktree-confirm-delete-wt-1')).toBeInTheDocument();

    fireEvent.click(screen.getByTestId('worktree-confirm-delete-wt-1'));

    expect(api.projects.deleteWorktree).toHaveBeenCalledWith('project-1', 'wt-1');
    await waitFor(() => {
      expect(updateProjectMock).toHaveBeenCalledWith(expect.objectContaining({ worktrees: [workspaceProject.worktrees[0]!] }));
    });
  });

  it('does not show a delete action for the local worktree menu', () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('worktree-menu-trigger-default'));

    expect(screen.queryByTestId('worktree-delete-default')).not.toBeInTheDocument();
  });

  it('shows a worktree action error when deleting fails', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    vi.mocked(api.projects.deleteWorktree).mockRejectedValueOnce(new Error('Deletion failed'));

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('worktree-menu-trigger-wt-1'));
    fireEvent.click(screen.getByTestId('worktree-delete-wt-1'));
    fireEvent.click(screen.getByTestId('worktree-confirm-delete-wt-1'));

    expect(await screen.findByTestId('worktree-error-wt-1')).toHaveTextContent('Deletion failed');
  });

  it('opens the new workspace popup and submits the requested name', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    mockMultiProjectState.sessionsByProject = { 'project-1': [] };

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-new-workspace-btn'));
    fireEvent.change(screen.getByTestId('new-workspace-input'), { target: { value: 'feature-b' } });
    fireEvent.click(screen.getByTestId('new-workspace-submit'));

    await waitFor(() => {
      expect(api.projects.createWorktree).toHaveBeenCalledWith('project-1', 'feature-b');
    });
  });

  it('keeps focus on the new workspace input while typing multiple words', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    mockMultiProjectState.sessionsByProject = { 'project-1': [] };

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-new-workspace-btn'));

    const input = screen.getByTestId('new-workspace-input');
    fireEvent.focus(input);
    fireEvent.change(input, { target: { value: 'feature branch' } });

    await waitFor(() => expect(input).toHaveFocus());
    expect(input).toHaveValue('feature branch');
  });

  it('adds a hover tooltip to the new session button', () => {
    render(<IdeLeftPanel />);

    const button = screen.getByTestId('sidebar-new-session-btn');

    expect(button).toHaveAttribute('title', 'new session');
    expect(button).toHaveAccessibleName('new session');
  });

  it('keeps project actions visible while hiding session content in collapsed mode', () => {
    render(<IdeLeftPanel collapsed />);

    expect(screen.getByTestId('project-menu-trigger')).toBeInTheDocument();
    expect(screen.getByTestId('rail-settings')).toBeInTheDocument();
    expect(screen.queryByTestId('sidebar-session-panel')).not.toBeInTheDocument();
    expect(screen.queryByTestId('active-project-path')).not.toBeInTheDocument();
  });

  it('applies sidebar font settings to the sidebar scope', () => {
    render(<IdeLeftPanel settings={mockSettings} />);

    expect(screen.getByTestId('sidebar-font-scope')).toHaveStyle({
      '--sidebar-font-family': "ui-monospace, 'SFMono-Regular', Menlo, Monaco, Consolas, 'Liberation Mono', monospace",
      '--sidebar-font-size': '16px',
    });
  });

  it('shows an error when creating a new workspace fails', async () => {
    mockMultiProjectState.projects = { 'project-1': workspaceProject };
    mockMultiProjectState.sessionsByProject = { 'project-1': [] };
    vi.mocked(api.projects.createWorktree).mockRejectedValueOnce(new Error('Workspace name is required'));

    render(<IdeLeftPanel />);

    fireEvent.click(screen.getByTestId('sidebar-new-workspace-btn'));
    fireEvent.click(screen.getByTestId('new-workspace-submit'));

    expect(await screen.findByTestId('new-workspace-error')).toHaveTextContent('Workspace name is required');
  });
});
