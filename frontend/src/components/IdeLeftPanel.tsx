import { useState, useCallback, useMemo, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMultiProject } from '../context/MultiProjectContext';
import SessionTreeItem from './SessionTreeItem';
import SearchPopup from './SearchPopup';
import { Popup } from './Popup';
import { ChevronDownIcon, IconFrame, MoreHorizontalIcon, PlusIcon, SettingsIcon } from './Icons';
import { api } from '../api/client';
import { getSidebarFontScopeStyle } from '../lib/webUiFontSettings';
import type { SessionDto, WebSettings, WorktreeDto } from '../types/api';

interface IdeLeftPanelProps {
  collapsed?: boolean;
  onOpenSettings?: () => void;
  settings?: WebSettings | null;
}

function getWorktreeLabel(worktree: WorktreeDto): string {
  if (worktree.defaultWorktree) {
    return `local: ${worktree.branch?.trim() || 'unknown'}`;
  }
  return `worktree: ${worktree.name}`;
}

function projectAvatar(name: string): {
  initials: string;
  buttonTint: string;
  outlineColor: string;
  chipBackground: string;
  chipText: string;
} {
  const parts = name.split(/[-_/s]+/).filter(Boolean);
  let initials: string;
  if (parts.length >= 2) {
    initials = parts.slice(0, 2).map((p) => p[0]!.toUpperCase()).join('');
  } else {
    initials = name.slice(0, 2).toUpperCase();
  }
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash);
  }
  const hue = Math.abs(hash) % 360;
  return {
    initials,
    buttonTint: `hsla(${hue}, 70%, 60%, 0.16)`,
    outlineColor: `hsl(${hue}, 62%, 64%)`,
    chipBackground: `hsl(${hue}, 70%, 78%)`,
    chipText: `hsl(${hue}, 40%, 22%)`,
  };
}

export default function IdeLeftPanel({ collapsed = false, onOpenSettings, settings }: IdeLeftPanelProps) {
  const navigate = useNavigate();
  const {
    openProjectIds,
    projects,
    sessionsByProject,
    activeSessionByProject,
    activeProjectId,
    unseenTaskCompletionCountByProject,
    openProject,
    closeProject,
    setActiveProject,
    setActiveSession,
    addSession,
    updateProject,
    refreshProject,
    ensureProjectSession,
    removeSession,
  } = useMultiProject();

  const [showSearchPopup, setShowSearchPopup] = useState(false);
  const [hoveredRailIdx, setHoveredRailIdx] = useState<number | null>(null);
  const [hoveredFooterBtn, setHoveredFooterBtn] = useState<string | null>(null);
  const [isProjectMenuOpen, setIsProjectMenuOpen] = useState(false);
  const [isWorkspaceModalOpen, setIsWorkspaceModalOpen] = useState(false);
  const [newWorkspaceName, setNewWorkspaceName] = useState('');
  const [workspaceError, setWorkspaceError] = useState<string | null>(null);
  const [workspaceSubmitting, setWorkspaceSubmitting] = useState(false);
  const [isRenamingProject, setIsRenamingProject] = useState(false);
  const [projectNameInput, setProjectNameInput] = useState('');
  const [projectRenameError, setProjectRenameError] = useState<string | null>(null);
  const [collapsedWorktrees, setCollapsedWorktrees] = useState<Set<string>>(new Set());
  const [openWorktreeMenuId, setOpenWorktreeMenuId] = useState<string | null>(null);
  const [worktreeActionError, setWorktreeActionError] = useState<string | null>(null);
  const [pendingDeleteSessionId, setPendingDeleteSessionId] = useState<string | null>(null);
  const [pendingDeleteWorktreeId, setPendingDeleteWorktreeId] = useState<string | null>(null);
  const projectHeaderRef = useRef<HTMLDivElement | null>(null);
  const worktreeMenuRef = useRef<HTMLDivElement | null>(null);
  const projectNameInputRef = useRef<HTMLInputElement | null>(null);

  const firstProjectId = openProjectIds.length > 0 ? openProjectIds[0]! : null;
  const currentProjectId = activeProjectId && openProjectIds.includes(activeProjectId)
    ? activeProjectId
    : firstProjectId;

  const handleSearchSelect = useCallback(
    async (result: { path: string }) => {
      try {
        const project = await api.projects.create({ path: result.path });
        openProject(project);
        const session = await ensureProjectSession(project.id);
        navigate(`/projects/${project.id}/sessions/${session.sessionId}`);
      } catch {}
      setShowSearchPopup(false);
    },
    [openProject, ensureProjectSession, navigate],
  );

  const handleNewSession = useCallback(async () => {
    if (!currentProjectId) return;
    try {
      const newSession = await api.sessions.create({ projectId: currentProjectId });
      addSession(currentProjectId, newSession);
      setActiveSession(currentProjectId, newSession.sessionId);
      navigate(`/projects/${currentProjectId}/sessions/${newSession.sessionId}`);
    } catch {}
  }, [currentProjectId, addSession, setActiveSession, navigate]);

  const handleCloseProject = useCallback(() => {
    if (!currentProjectId) return;
    closeProject(currentProjectId);
  }, [currentProjectId, closeProject]);

  const handleOpenSettings = useCallback(() => {
    if (onOpenSettings) {
      onOpenSettings();
      return;
    }
    navigate('/settings');
  }, [navigate, onOpenSettings]);

  const currentProject = currentProjectId ? projects[currentProjectId] : null;
  const workspaceEnabled = currentProject?.workspaceEnabled ?? false;

  const handleWorkspaceEnable = useCallback(async () => {
    if (!currentProjectId) return;
    setIsProjectMenuOpen(false);
    try {
      const project = await api.projects.enableWorkspace(currentProjectId);
      openProject(project);
      await refreshProject(currentProjectId);
    } catch {
    }
  }, [currentProjectId, openProject, refreshProject]);

  const handleProjectMenuClose = useCallback(() => {
    setIsProjectMenuOpen(false);
  }, []);

  const handleProjectMenuToggle = useCallback(() => {
    setIsProjectMenuOpen((prev) => !prev);
  }, []);

  const handleProjectCloseAction = useCallback(() => {
    handleProjectMenuClose();
    handleCloseProject();
  }, [handleCloseProject, handleProjectMenuClose]);

  const handleProjectRenameStart = useCallback(() => {
    if (!currentProject) {
      return;
    }
    setProjectNameInput(currentProject.name);
    setProjectRenameError(null);
    setIsProjectMenuOpen(false);
    setIsRenamingProject(true);
    setTimeout(() => projectNameInputRef.current?.focus(), 0);
  }, [currentProject]);

  const handleProjectRenameCancel = useCallback(() => {
    setIsRenamingProject(false);
    setProjectRenameError(null);
    setProjectNameInput('');
  }, []);

  const handleProjectRenameSubmit = useCallback(async () => {
    if (!currentProjectId || !currentProject) {
      setIsRenamingProject(false);
      return;
    }
    const trimmedName = projectNameInput.trim();
    if (!trimmedName) {
      setProjectRenameError('Project name is required');
      return;
    }
    if (trimmedName === currentProject.name) {
      handleProjectRenameCancel();
      return;
    }
    try {
      const updated = await api.projects.rename(currentProjectId, trimmedName);
      updateProject(updated);
      setIsRenamingProject(false);
      setProjectRenameError(null);
    } catch (error) {
      setProjectRenameError(error instanceof Error ? error.message : 'Failed to rename project');
    }
  }, [currentProjectId, currentProject, projectNameInput, updateProject, handleProjectRenameCancel]);

  useEffect(() => {
    setIsProjectMenuOpen(false);
    setOpenWorktreeMenuId(null);
    setWorktreeActionError(null);
    setIsRenamingProject(false);
    setProjectRenameError(null);
  }, [currentProjectId]);

  useEffect(() => {
    if (isRenamingProject) {
      return;
    }
    setProjectRenameError(null);
  }, [isRenamingProject]);

  useEffect(() => {
    if (!isProjectMenuOpen) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!projectHeaderRef.current?.contains(event.target as Node)) {
        setIsProjectMenuOpen(false);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setIsProjectMenuOpen(false);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [isProjectMenuOpen]);

  useEffect(() => {
    if (!openWorktreeMenuId) {
      return;
    }

    const handlePointerDown = (event: MouseEvent) => {
      if (!worktreeMenuRef.current?.contains(event.target as Node)) {
        setOpenWorktreeMenuId(null);
      }
    };

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') {
        setOpenWorktreeMenuId(null);
      }
    };

    document.addEventListener('mousedown', handlePointerDown);
    document.addEventListener('keydown', handleKeyDown);
    return () => {
      document.removeEventListener('mousedown', handlePointerDown);
      document.removeEventListener('keydown', handleKeyDown);
    };
  }, [openWorktreeMenuId]);

  const sessions = currentProjectId ? (sessionsByProject[currentProjectId] ?? []) : [];
  const activeSessionId = currentProjectId ? activeSessionByProject[currentProjectId] : null;
  const projectName = currentProjectId ? (projects[currentProjectId]?.name ?? currentProjectId) : null;
  const worktrees = currentProject?.worktrees ?? [];

  const groupedByWorktree = useMemo(() => {
    const map = new Map<string, { worktree: WorktreeDto; sessions: SessionDto[] }>();
    for (const worktree of worktrees) {
      map.set(worktree.id, { worktree, sessions: [] });
    }
    for (const session of sessions) {
      const worktree = session.worktree ?? worktrees.find((item) => item.id === session.worktreeId);
      if (!worktree) {
        continue;
      }
      const existing = map.get(worktree.id);
      if (existing) {
        existing.sessions.push(session);
      } else {
        map.set(worktree.id, { worktree, sessions: [session] });
      }
    }
    return Array.from(map.values()).sort((a, b) => {
      if (a.worktree.defaultWorktree) return -1;
      if (b.worktree.defaultWorktree) return 1;
      return a.worktree.name.localeCompare(b.worktree.name);
    });
  }, [sessions, worktrees]);

  const toggleWorktree = useCallback((worktreeId: string) => {
    setCollapsedWorktrees((prev) => {
      const next = new Set(prev);
      if (next.has(worktreeId)) {
        next.delete(worktreeId);
      } else {
        next.add(worktreeId);
      }
      return next;
    });
  }, []);

  const handleCreateWorktree = useCallback(async () => {
    if (!currentProjectId || !newWorkspaceName.trim()) {
      setWorkspaceError('Workspace name is required');
      return;
    }
    setWorkspaceSubmitting(true);
    setWorkspaceError(null);
    try {
      const project = await api.projects.createWorktree(currentProjectId, newWorkspaceName.trim());
      openProject(project);
      await refreshProject(currentProjectId);
      setNewWorkspaceName('');
      setIsWorkspaceModalOpen(false);
    } catch (error) {
      setWorkspaceError(error instanceof Error ? error.message : 'Failed to create workspace');
    } finally {
      setWorkspaceSubmitting(false);
    }
  }, [currentProjectId, newWorkspaceName, openProject, refreshProject]);

  const handleNewSessionForWorktree = useCallback(async (worktreeId: string) => {
    if (!currentProjectId) return;
    try {
      const newSession = await api.sessions.create({ projectId: currentProjectId, worktreeId });
      addSession(currentProjectId, newSession);
      setActiveSession(currentProjectId, newSession.sessionId);
      navigate(`/projects/${currentProjectId}/sessions/${newSession.sessionId}`);
    } catch {
    }
  }, [currentProjectId, addSession, setActiveSession, navigate]);

  const handleDeleteSession = useCallback(async (sessionId: string) => {
    if (!currentProjectId) {
      return;
    }
    setPendingDeleteSessionId(sessionId);
  }, [currentProjectId]);

  const confirmDeleteSession = useCallback(async (sessionId: string) => {
    if (!currentProjectId) {
      return;
    }
    setPendingDeleteSessionId(null);

    try {
      await api.sessions.delete(sessionId);
      removeSession(currentProjectId, sessionId);

      const remainingSessions = (sessionsByProject[currentProjectId] ?? []).filter(
        (session) => session.sessionId !== sessionId,
      );
      const deletedActiveSession = activeSessionId === sessionId;

      if (!deletedActiveSession) {
        return;
      }

      const replacementSession = remainingSessions[0] ?? await ensureProjectSession(currentProjectId);
      setActiveSession(currentProjectId, replacementSession.sessionId);
      navigate(`/projects/${currentProjectId}/sessions/${replacementSession.sessionId}`);
    } catch {
    }
  }, [
    currentProjectId,
    removeSession,
    sessionsByProject,
    activeSessionId,
    ensureProjectSession,
    setActiveSession,
    navigate,
  ]);

  const cancelDeleteSession = useCallback(() => {
    setPendingDeleteSessionId(null);
  }, []);

  const handleWorktreeMenuToggle = useCallback((worktreeId: string) => {
    setWorktreeActionError(null);
    setOpenWorktreeMenuId((prev) => (prev === worktreeId ? null : worktreeId));
  }, []);

  const handleDeleteWorktree = useCallback(async (worktreeId: string) => {
    if (!currentProjectId) {
      return;
    }
    setPendingDeleteWorktreeId(worktreeId);
  }, [currentProjectId]);

  const confirmDeleteWorktree = useCallback(async (worktreeId: string) => {
    if (!currentProjectId) {
      return;
    }
    setPendingDeleteWorktreeId(null);
    try {
      const project = await api.projects.deleteWorktree(currentProjectId, worktreeId);
      updateProject(project);
      setOpenWorktreeMenuId(null);
      setWorktreeActionError(null);
    } catch (error) {
      setWorktreeActionError(error instanceof Error ? error.message : 'Failed to delete workspace');
    }
  }, [currentProjectId, updateProject]);

  const cancelDeleteWorktree = useCallback(() => {
    setPendingDeleteWorktreeId(null);
  }, []);

  const sidebarFontScopeStyle = getSidebarFontScopeStyle(settings);

  return (
    <div style={{ ...styles.outer, ...sidebarFontScopeStyle }} data-testid="sidebar-font-scope">
      <div style={styles.iconRail}>
        <div style={styles.railBody}>
          {openProjectIds.map((id, i) => {
            const name = projects[id]?.name ?? id;
            const { initials, buttonTint, outlineColor, chipBackground, chipText } = projectAvatar(name);
            const isActive = id === activeProjectId;
            const isHovered = hoveredRailIdx === i;
            const unseenCompletionCount = unseenTaskCompletionCountByProject[id] ?? 0;
            return (
              <button
                key={id}
                title={name}
                onClick={async () => {
                  setActiveProject(id);
                  try {
                    const session = await ensureProjectSession(id);
                    navigate(`/projects/${id}/sessions/${session.sessionId}`);
                  } catch {
                    navigate('/sessions');
                  }
                }}
                onMouseEnter={() => setHoveredRailIdx(i)}
                onMouseLeave={() => setHoveredRailIdx(null)}
                className="btn"
                style={{
                  ...styles.avatarBtn,
                  background: isActive
                    ? `linear-gradient(180deg, var(--bg) 0%, ${buttonTint} 100%)`
                    : isHovered
                      ? 'var(--bg)'
                      : 'transparent',
                  borderColor: isActive ? outlineColor : isHovered ? 'var(--border)' : 'transparent',
                }}
                data-testid={`rail-avatar-${id}`}
                >
                  <span
                  style={{
                    ...styles.avatarInitialsMuted,
                    width: isActive ? 28 : 30,
                    height: isActive ? 28 : 30,
                    borderRadius: isActive ? 'var(--radius-sm)' : 'var(--radius-md)',
                    background: chipBackground,
                    color: chipText,
                  }}
                  data-testid={`rail-avatar-chip-${id}`}
                  >
                    {initials}
                  </span>
                  {unseenCompletionCount > 0 && (
                    <span
                      style={styles.projectBadge}
                      data-testid={`rail-avatar-badge-${id}`}
                      aria-label={`${unseenCompletionCount} unseen completion notifications`}
                    >
                      {unseenCompletionCount > 9 ? '9+' : unseenCompletionCount}
                    </span>
                  )}
                </button>
            );
          })}
          <button
            title="Open project"
            onClick={() => setShowSearchPopup(true)}
            onMouseEnter={() => setHoveredFooterBtn('add')}
            onMouseLeave={() => setHoveredFooterBtn(null)}
            className="btn"
            style={{
              ...styles.footerBtn,
              background: hoveredFooterBtn === 'add' ? 'var(--surface)' : 'transparent',
              borderColor: hoveredFooterBtn === 'add' ? 'var(--border)' : 'transparent',
              opacity: hoveredFooterBtn === 'add' ? 0.88 : 0.72,
            }}
            data-testid="rail-add-project"
          >
            <IconFrame size={16}><PlusIcon size={16} /></IconFrame>
          </button>
        </div>
        <div style={styles.railFooter}>
          <button
            title="Settings"
            onClick={handleOpenSettings}
            onMouseEnter={() => setHoveredFooterBtn('settings')}
            onMouseLeave={() => setHoveredFooterBtn(null)}
            className="btn"
            style={{
              ...styles.footerBtn,
              background: hoveredFooterBtn === 'settings' ? 'var(--surface)' : 'transparent',
              borderColor: hoveredFooterBtn === 'settings' ? 'var(--border)' : 'transparent',
              opacity: hoveredFooterBtn === 'settings' ? 0.88 : 0.72,
            }}
            data-testid="rail-settings"
          >
            <IconFrame size={16}><SettingsIcon size={16} /></IconFrame>
          </button>
        </div>
        <SearchPopup
          isOpen={showSearchPopup}
          onClose={() => setShowSearchPopup(false)}
          onSelect={handleSearchSelect}
        />
      </div>
      <div style={styles.detailPanel}>
        {!currentProjectId ? (
          <div style={styles.emptyState}>
            <p style={styles.emptyText}>No projects open.</p>
            <p style={styles.emptyHint}>Click + to open one.</p>
          </div>
        ) : (
          <>
              <div
                ref={projectHeaderRef}
                style={{
                  ...styles.projectHeader,
                  justifyContent: collapsed ? 'center' : 'space-between',
                  padding: collapsed ? '10px 8px 6px' : '10px 12px 6px',
                }}
              >
                {!collapsed && (
                  <div style={styles.projectHeaderText}>
                    {isRenamingProject ? (
                      <>
                        <input
                          ref={projectNameInputRef}
                          className="input-field"
                          style={styles.projectNameInput}
                          value={projectNameInput}
                          onChange={(event) => {
                            setProjectNameInput(event.target.value);
                            if (projectRenameError) {
                              setProjectRenameError(null);
                            }
                          }}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter') {
                              void handleProjectRenameSubmit();
                            } else if (event.key === 'Escape') {
                              handleProjectRenameCancel();
                            }
                          }}
                          onBlur={() => {
                            void handleProjectRenameSubmit();
                          }}
                          data-testid="project-name-input"
                        />
                        {projectRenameError && (
                          <span style={styles.projectRenameError} data-testid="project-rename-error">
                            {projectRenameError}
                          </span>
                        )}
                      </>
                    ) : (
                      <span style={styles.projectName}>{projectName}</span>
                    )}
                    {currentProject?.path && (
                      <span
                        style={styles.projectPath}
                        title={currentProject.path}
                        data-testid="active-project-path"
                      >
                        {currentProject.path}
                      </span>
                    )}
                  </div>
                )}
                <div style={styles.projectMenuContainer}>
                 <button
                    type="button"
                    title="Project actions"
                    aria-label="Project actions"
                    aria-expanded={isProjectMenuOpen}
                    onClick={handleProjectMenuToggle}
                    className="btn btn-ghost"
                    style={styles.menuTriggerBtn}
                    disabled={isRenamingProject}
                   data-testid="project-menu-trigger"
                  >
                    <IconFrame size={16}><MoreHorizontalIcon size={16} /></IconFrame>
                  </button>
                 {isProjectMenuOpen && (
                   <div style={styles.projectMenu} data-testid="project-menu">
                      <button
                        type="button"
                        className="btn"
                        style={styles.projectMenuItem}
                        onClick={handleProjectRenameStart}
                        data-testid="project-menu-rename"
                      >
                        Rename
                      </button>
                       {!workspaceEnabled && (
                          <button
                            type="button"
                            className="btn"
                           style={styles.projectMenuItem}
                          onClick={handleWorkspaceEnable}
                          data-testid="project-menu-workspace-toggle"
                        >
                          Enable workspace
                        </button>
                      )}
                     <button
                       type="button"
                       className="btn"
                       style={{
                         ...styles.projectMenuItem,
                        ...styles.projectMenuDangerItem,
                      }}
                      onClick={handleProjectCloseAction}
                      data-testid="project-menu-close"
                    >
                      Close
                    </button>
                  </div>
                )}
              </div>
            </div>
              {!collapsed && <div style={styles.sessionList} data-testid="sidebar-session-panel">
                 {!workspaceEnabled && (
                   <div style={styles.newSessionRow}>
                     <button
                       className="btn btn-ghost"
                      style={{
                        ...styles.newSessionBtn,
                        opacity: currentProjectId ? 1 : 0.4,
                        cursor: currentProjectId ? 'pointer' : 'not-allowed',
                       }}
                       onClick={handleNewSession}
                       disabled={!currentProjectId}
                       title="new session"
                       aria-label="new session"
                       data-testid="sidebar-new-session-btn"
                     >
                       + New Session
                    </button>
                  </div>
                )}
                {workspaceEnabled && (
                  <div style={styles.newSessionRow}>
                     <button
                       className="btn btn-secondary"
                       style={styles.newSessionBtn}
                      onClick={() => {
                        setWorkspaceError(null);
                        setNewWorkspaceName('');
                        setIsWorkspaceModalOpen(true);
                      }}
                      data-testid="sidebar-new-workspace-btn"
                    >
                      + New workspace
                    </button>
                  </div>
                )}
                {workspaceEnabled ? groupedByWorktree.map(({ worktree, sessions: worktreeSessions }) => {
                  const isCollapsed = collapsedWorktrees.has(worktree.id);
                  const isMenuOpen = openWorktreeMenuId === worktree.id;
                  return (
                    <div key={worktree.id} data-testid={`worktree-group-${worktree.id}`}>
                      <div style={styles.worktreeHeaderRow}>
                        <button
                         className="btn"
                          style={{ ...styles.branchHeader, flex: 1 }}
                         onClick={() => toggleWorktree(worktree.id)}
                         data-testid={`worktree-toggle-${worktree.id}`}
                       >
                         <span style={{
                           ...styles.chevron,
                            transform: isCollapsed ? 'rotate(-90deg)' : 'rotate(0deg)',
                           }}>
                             <IconFrame size={14}><ChevronDownIcon size={14} /></IconFrame>
                           </span>
                          <span style={styles.branchLabel}>{getWorktreeLabel(worktree)}</span>
                          <span style={styles.branchCount}>{worktreeSessions.length}</span>
                        </button>
                        <div style={styles.worktreeActions} ref={isMenuOpen ? worktreeMenuRef : null}>
                           <button
                             type="button"
                             className="btn btn-ghost"
                             style={styles.inlineMenuBtn}
                            onClick={() => handleWorktreeMenuToggle(worktree.id)}
                            data-testid={`worktree-menu-trigger-${worktree.id}`}
                            aria-expanded={isMenuOpen}
                            title="Worktree actions"
                           >
                             <IconFrame size={14}><MoreHorizontalIcon size={14} /></IconFrame>
                           </button>
                           {isMenuOpen && worktree.managed && (
                             <div style={styles.worktreeMenu} data-testid={`worktree-menu-${worktree.id}`}>
                               {pendingDeleteWorktreeId === worktree.id ? (
                                 <div style={styles.worktreeDeleteConfirm}>
                                   <span style={styles.worktreeDeleteLabel}>Delete?</span>
                                   <button
                                     type="button"
                                     className="btn"
                                     style={{
                                       ...styles.projectMenuItem,
                                       ...styles.projectMenuDangerItem,
                                     }}
                                     onClick={() => {
                                       void confirmDeleteWorktree(worktree.id);
                                     }}
                                     data-testid={`worktree-confirm-delete-${worktree.id}`}
                                   >
                                     Delete
                                   </button>
                                   <button
                                     type="button"
                                     className="btn"
                                     style={styles.projectMenuItem}
                                     onClick={() => {
                                       cancelDeleteWorktree();
                                     }}
                                     data-testid={`worktree-cancel-delete-${worktree.id}`}
                                   >
                                     Cancel
                                   </button>
                                 </div>
                               ) : (
                                 <button
                                   type="button"
                                   className="btn"
                                   style={{
                                     ...styles.projectMenuItem,
                                    ...styles.projectMenuDangerItem,
                                  }}
                                  onClick={() => {
                                    void handleDeleteWorktree(worktree.id);
                                  }}
                                  data-testid={`worktree-delete-${worktree.id}`}
                                >
                                  Delete
                                </button>
                               )}
                             </div>
                           )}
                           <button
                             type="button"
                             className="btn btn-ghost"
                             style={styles.inlineNewSessionBtn}
                            onClick={() => {
                              void handleNewSessionForWorktree(worktree.id);
                            }}
                            data-testid={`worktree-new-session-${worktree.id}`}
                          >
                             <IconFrame size={12}><PlusIcon size={12} /></IconFrame>
                           </button>
                        </div>
                      </div>
                      {!isCollapsed && worktreeActionError && openWorktreeMenuId === worktree.id && (
                        <div style={styles.worktreeError} data-testid={`worktree-error-${worktree.id}`}>
                          {worktreeActionError}
                        </div>
                      )}
                      {!isCollapsed && worktreeSessions.map((session) => (
                        <SessionTreeItem
                          key={session.sessionId}
                          sessionId={session.sessionId}
                          label={session.name || session.sessionId.slice(0, 8)}
                          status={session.status}
                          isActive={session.sessionId === activeSessionId}
                          isPendingDelete={pendingDeleteSessionId === session.sessionId}
                          onDelete={() => {
                            void handleDeleteSession(session.sessionId);
                          }}
                          onDeleteConfirm={() => {
                            void confirmDeleteSession(session.sessionId);
                          }}
                          onDeleteCancel={() => {
                            cancelDeleteSession();
                          }}
                          onClick={() => {
                           setActiveSession(currentProjectId, session.sessionId);
                           navigate(`/projects/${currentProjectId}/sessions/${session.sessionId}`);
                          }}
                       />
                     ))}
                     {!isCollapsed && worktreeSessions.length === 0 && (
                       <div style={styles.emptyWorktree} data-testid={`worktree-empty-${worktree.id}`}>
                         No sessions yet.
                       </div>
                     )}
                   </div>
                 );
                }) : sessions.map((session) => (
                   <SessionTreeItem
                     key={session.sessionId}
                     sessionId={session.sessionId}
                     label={session.name || session.sessionId.slice(0, 8)}
                     status={session.status}
                     isActive={session.sessionId === activeSessionId}
                     isPendingDelete={pendingDeleteSessionId === session.sessionId}
                     onDelete={() => {
                       void handleDeleteSession(session.sessionId);
                     }}
                     onDeleteConfirm={() => {
                       void confirmDeleteSession(session.sessionId);
                     }}
                     onDeleteCancel={() => {
                       cancelDeleteSession();
                     }}
                     onClick={() => {
                       setActiveSession(currentProjectId, session.sessionId);
                       navigate(`/projects/${currentProjectId}/sessions/${session.sessionId}`);
                     }}
                   />
                 ))}
              </div>}
            </>
          )}
        </div>
      <Popup
        isOpen={isWorkspaceModalOpen}
        onClose={() => setIsWorkspaceModalOpen(false)}
        title="New workspace"
      >
        <div style={styles.workspaceModalBody}>
          <input
            className="input-field"
            style={styles.workspaceInput}
            value={newWorkspaceName}
            onChange={(event) => setNewWorkspaceName(event.target.value)}
            placeholder="Workspace name"
            data-testid="new-workspace-input"
            data-autofocus="true"
            autoFocus
          />
          {workspaceError && <div style={styles.workspaceError} data-testid="new-workspace-error">{workspaceError}</div>}
          <div style={styles.workspaceActions}>
            <button className="btn btn-secondary" style={styles.workspaceSecondaryBtn} onClick={() => setIsWorkspaceModalOpen(false)}>
              Cancel
            </button>
            <button
              className="btn btn-primary"
              style={styles.workspacePrimaryBtn}
              onClick={() => void handleCreateWorktree()}
              disabled={workspaceSubmitting}
              data-testid="new-workspace-submit"
            >
              Create
            </button>
          </div>
        </div>
      </Popup>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  outer: {
    display: 'flex',
    width: '100%',
    height: '100%',
    overflow: 'hidden',
  },
  iconRail: {
    width: 48,
    flexShrink: 0,
    display: 'flex',
    flexDirection: 'column',
    background: 'var(--surface)',
    borderRight: '1px solid var(--border)',
    position: 'relative',
  },
  railBody: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: '8px 4px',
    gap: 4,
    flex: 1,
    overflowY: 'auto',
  },
  avatarBtn: {
    width: 40,
    height: 40,
    borderRadius: 'var(--radius-sm)',
    borderStyle: 'solid',
    borderWidth: '1.5px',
    borderColor: 'transparent',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 'var(--sidebar-font-size)',
    fontWeight: 700,
    fontFamily: 'var(--font-sans)',
    transition: 'background 0.15s, border-color 0.15s',
    outline: 'none',
    boxSizing: 'border-box' as const,
    position: 'relative',
  },
  projectBadge: {
    position: 'absolute',
    top: 2,
    right: 1,
    minWidth: 16,
    height: 16,
    padding: '0 4px',
    borderRadius: 999,
    background: 'var(--red)',
    color: 'white',
    fontSize: 'calc(var(--sidebar-font-size) - 3px)',
    fontWeight: 700,
    fontFamily: 'var(--font-sans)',
    lineHeight: '16px',
    textAlign: 'center' as const,
    boxShadow: '0 0 0 2px var(--surface), 0 0 0 1px color-mix(in oklab, var(--red) 70%, black)',
  },
  avatarInitialsMuted: {
    width: 30,
    height: 30,
    borderRadius: 'var(--radius-md)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 'calc(var(--sidebar-font-size) - 1px)',
    fontWeight: 700,
    fontFamily: 'var(--font-sans)',
  },
  railFooter: {
    marginTop: 'auto',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    padding: '8px 4px 12px',
    gap: 4,
    borderTop: '1px solid var(--border)',
  },
  footerBtn: {
    width: 40,
    height: 40,
    borderRadius: 'var(--radius-sm)',
    borderStyle: 'solid',
    borderWidth: '1px',
    borderColor: 'transparent',
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontFamily: 'var(--font-sans)',
    color: 'var(--text-muted)',
    transition: 'background 0.15s, border-color 0.15s, opacity 0.15s',
    outline: 'none',
  },
  detailPanel: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    background: 'var(--surface)',
    overflow: 'hidden',
  },
  emptyState: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    padding: 24,
  },
  emptyText: {
    fontSize: 'var(--sidebar-font-size)',
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text-muted)',
    margin: 0,
    marginBottom: 4,
  },
  emptyHint: {
    fontSize: 'calc(var(--sidebar-font-size) - 1px)',
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text-muted)',
    margin: 0,
    opacity: 0.7,
  },
  projectHeader: {
     display: 'flex',
     alignItems: 'flex-start',
     padding: '10px 12px 6px',
     gap: 6,
     position: 'relative',
   },
   projectHeaderText: {
     display: 'flex',
     flexDirection: 'column',
     minWidth: 0,
     flex: 1,
   },
     projectName: {
      fontSize: 'var(--sidebar-font-size)',
      fontWeight: 600,
       fontFamily: 'var(--sidebar-font-family)',
       color: 'var(--text)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
      whiteSpace: 'nowrap',
      flex: 1,
    },
     projectNameInput: {
      width: '100%',
      minWidth: 0,
      borderRadius: 'var(--radius-sm)',
      padding: '6px 8px',
      fontSize: 'var(--sidebar-font-size)',
      fontWeight: 600,
      fontFamily: 'var(--sidebar-font-family)',
      color: 'var(--text)',
      boxSizing: 'border-box' as const,
    },
     projectRenameError: {
      fontSize: 'calc(var(--sidebar-font-size) - 2px)',
      color: 'var(--red)',
      fontFamily: 'var(--sidebar-font-family)',
      marginTop: 4,
    },
    projectPath: {
      fontSize: 'calc(var(--sidebar-font-size) - 2px)',
        color: 'var(--text-muted)',
      fontFamily: 'var(--sidebar-font-family)',
     overflow: 'hidden',
     textOverflow: 'ellipsis',
     whiteSpace: 'nowrap',
     marginTop: 2,
   },
   projectMenuContainer: {
     position: 'relative',
     flexShrink: 0,
   },
  menuTriggerBtn: {
    background: 'transparent',
    borderColor: 'var(--border)',
    color: 'var(--text-muted)',
    padding: '0 6px',
    lineHeight: 1,
    borderRadius: 'var(--radius-sm)',
    fontFamily: 'var(--font-sans)',
    outline: 'none',
    height: 24,
    minWidth: 24,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  projectMenu: {
    position: 'absolute',
    top: 'calc(100% + 6px)',
    right: 0,
    minWidth: 160,
    display: 'flex',
    flexDirection: 'column',
    padding: 4,
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.14)',
    zIndex: 2,
  },
  projectMenuItem: {
    background: 'transparent',
    border: 'none',
    borderRadius: 'var(--radius-sm)',
    color: 'var(--text)',
    fontSize: 'calc(var(--sidebar-font-size) - 1px)',
    fontFamily: 'var(--sidebar-font-family)',
    textAlign: 'left' as const,
    padding: '7px 10px',
  },
  projectMenuDangerItem: {
    color: 'var(--red)',
  },
  sessionList: {
    flex: 1,
    overflowY: 'auto',
    padding: '4px 0',
  },
  newSessionRow: {
    padding: '4px 8px 4px 12px',
  },
  newSessionBtn: {
    width: '100%',
    padding: '5px 8px',
    background: 'transparent',
    border: '1px dashed var(--border)',
    borderRadius: 'var(--radius-sm)',
    color: 'var(--text-muted)',
    fontSize: 'calc(var(--sidebar-font-size) - 2px)',
    fontWeight: 500,
    fontFamily: 'var(--sidebar-font-family)',
    textAlign: 'left' as const,
    transition: 'background 0.12s, border-color 0.12s',
  },
  branchHeader: {
    display: 'flex',
    alignItems: 'center',
    width: '100%',
    padding: '4px 8px 4px 12px',
    background: 'transparent',
    border: 'none',
    cursor: 'pointer',
    textAlign: 'left' as const,
    transition: 'background 0.12s',
    gap: 4,
  },
  chevron: {
    color: 'var(--text-muted)',
    flexShrink: 0,
    width: 14,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center' as const,
    transition: 'transform 0.15s',
    lineHeight: 1,
  },
  branchLabel: {
    fontSize: 'calc(var(--sidebar-font-size) - 2px)',
    fontWeight: 600,
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text-muted)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    flex: 1,
  },
  branchCount: {
    fontSize: 'calc(var(--sidebar-font-size) - 3px)',
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text-muted)',
    opacity: 0.7,
    flexShrink: 0,
  },
  worktreeHeaderRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    paddingRight: 8,
  },
  worktreeActions: {
    position: 'relative',
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    flexShrink: 0,
  },
  inlineMenuBtn: {
    border: '1px solid var(--border)',
    background: 'transparent',
    color: 'var(--text-muted)',
    borderRadius: 'var(--radius-sm)',
    lineHeight: 1,
    padding: '1px 6px',
    minWidth: 24,
  },
  worktreeMenu: {
    position: 'absolute',
    top: 'calc(100% + 6px)',
    right: 32,
    minWidth: 120,
    display: 'flex',
    flexDirection: 'column',
    padding: 4,
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    boxShadow: '0 8px 24px rgba(0, 0, 0, 0.14)',
    zIndex: 2,
  },
  inlineNewSessionBtn: {
    border: '1px solid var(--border)',
    background: 'transparent',
    color: 'var(--text-muted)',
    borderRadius: 'var(--radius-sm)',
    lineHeight: 1,
    padding: '1px 6px',
    marginRight: 4,
  },
  emptyWorktree: {
    padding: '6px 8px 6px 28px',
    color: 'var(--text-muted)',
    fontSize: 'calc(var(--sidebar-font-size) - 2px)',
    fontFamily: 'var(--sidebar-font-family)',
  },
  worktreeError: {
    padding: '0 8px 6px 28px',
    color: 'var(--red)',
    fontSize: 'calc(var(--sidebar-font-size) - 2px)',
    fontFamily: 'var(--sidebar-font-family)',
  },
  worktreeDeleteConfirm: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    padding: '2px 4px',
  },
  worktreeDeleteLabel: {
    fontSize: 'calc(var(--sidebar-font-size) - 2px)',
    fontWeight: 600,
    color: 'var(--text-muted)',
    fontFamily: 'var(--sidebar-font-family)',
    flex: 1,
  },
  workspaceModalBody: {
    display: 'flex',
    flexDirection: 'column',
    gap: 12,
  },
  workspaceInput: {
    width: '100%',
    borderRadius: 'var(--radius-sm)',
    padding: '8px 10px',
    fontSize: 'var(--sidebar-font-size)',
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text)',
    boxSizing: 'border-box' as const,
  },
  workspaceError: {
    color: 'var(--red)',
    fontSize: 'calc(var(--sidebar-font-size) - 1px)',
    fontFamily: 'var(--sidebar-font-family)',
  },
  workspaceActions: {
    display: 'flex',
    justifyContent: 'flex-end',
    gap: 8,
  },
  workspaceSecondaryBtn: {
    padding: '6px 10px',
  },
  workspacePrimaryBtn: {
    padding: '6px 12px',
  },
};
