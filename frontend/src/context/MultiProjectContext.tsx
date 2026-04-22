import React, { createContext, useContext, useState, useCallback, useEffect, useRef } from 'react';
import { api } from '../api/client';
import type { ProjectDto, ProjectWorkspaceStateDto, SessionDto, TaskCompletionDto } from '../types/api';

const STORAGE_KEY = 'coderhino-multi-project';

interface PersistedState {
  lastActiveSessionByProject: Record<string, string>;
  recentSessionOrder: string[];
  openProjectIds?: string[];
  activeProjectId?: string | null;
}

interface MultiProjectState {
  openProjectIds: string[];
  projects: Record<string, ProjectDto>;
  sessionsByProject: Record<string, SessionDto[]>;
  activeSessionByProject: Record<string, string>;
  activeProjectId: string | null;
  recentSessionOrder: string[];
  unseenTaskCompletionCountByProject: Record<string, number>;
  loading: boolean;
}

interface MultiProjectActions {
  openProject: (project: ProjectDto) => void;
  updateProject: (project: ProjectDto) => void;
  closeProject: (projectId: string) => void;
  setActiveProject: (projectId: string | null) => void;
  setActiveSession: (projectId: string, sessionId: string) => void;
  addSession: (projectId: string, session: SessionDto) => void;
  removeSession: (projectId: string, sessionId: string) => void;
  refreshSessions: (projectId: string) => Promise<void>;
  refreshProject: (projectId: string) => Promise<ProjectDto | null>;
  ensureProjectSession: (projectId: string, worktreeId?: string) => Promise<SessionDto>;
  registerTaskCompletion: (completion: TaskCompletionDto, visible: boolean) => void;
  clearTaskCompletionNotifications: (projectId: string) => void;
  getActiveProject: () => ProjectDto | null;
  getActiveProjectForSession: (sessionId: string) => ProjectDto | null;
  getSessionById: (sessionId: string) => SessionDto | null;
}

type MultiProjectContextType = MultiProjectState & MultiProjectActions;

const MultiProjectContext = createContext<MultiProjectContextType | null>(null);

function keepSessionsOwnedByProject(projectId: string, sessions: SessionDto[]): SessionDto[] {
  return sessions.filter((session) => session.projectId === projectId);
}

function filterSessionsForProject(project: ProjectDto, sessions: SessionDto[]): SessionDto[] {
  const owned = keepSessionsOwnedByProject(project.id, sessions);
  const worktreeIds = new Set((project.worktrees ?? []).map((worktree) => worktree.id));
  if (worktreeIds.size === 0) {
    return owned;
  }
  return owned.filter((session) => !session.worktreeId || worktreeIds.has(session.worktreeId));
}

function loadPersistedState(): PersistedState {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) {
      return {
        lastActiveSessionByProject: {},
        recentSessionOrder: [],
        activeProjectId: null,
      };
    }
    return JSON.parse(raw) as PersistedState;
  } catch {
    return {
      lastActiveSessionByProject: {},
      recentSessionOrder: [],
      activeProjectId: null,
    };
  }
}

function savePersistedState(state: PersistedState): void {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
  }
}

function normalizeWorkspaceState(state?: ProjectWorkspaceStateDto | null): Required<ProjectWorkspaceStateDto> {
  const openProjectIds = Array.isArray(state?.openProjectIds) ? state.openProjectIds.filter((id): id is string => typeof id === 'string' && id.length > 0) : [];
  const activeProjectId = typeof state?.activeProjectId === 'string' && openProjectIds.includes(state.activeProjectId)
    ? state.activeProjectId
    : openProjectIds[0] ?? null;
  return { openProjectIds, activeProjectId };
}

async function loadProjectsByIds(ids: string[]): Promise<{ openProjectIds: string[]; projects: Record<string, ProjectDto> }> {
  const results = await Promise.all(
    ids.map(async (id) => {
      try {
        const project = await api.projects.get(id);
        return { id, project };
      } catch {
        return null;
      }
    }),
  );
  const validIds: string[] = [];
  const projectMap: Record<string, ProjectDto> = {};
  for (const result of results) {
    if (!result) {
      continue;
    }
    validIds.push(result.id);
    projectMap[result.id] = result.project;
  }
  return { openProjectIds: validIds, projects: projectMap };
}

export function MultiProjectProvider({ children }: { children: React.ReactNode }) {
  const persisted = useRef(loadPersistedState());
  const workspaceStateLoadedRef = useRef(false);
  const workspaceStateDirtyRef = useRef(false);
  const openProjectIdsRef = useRef<string[]>([]);
  const activeProjectIdRef = useRef<string | null>(null);
  const pendingWorkspaceStateRef = useRef<Required<ProjectWorkspaceStateDto> | null>(null);

  const [openProjectIds, setOpenProjectIds] = useState<string[]>([]);
  const [projects, setProjects] = useState<Record<string, ProjectDto>>({});
  const [sessionsByProject, setSessionsByProject] = useState<Record<string, SessionDto[]>>({});
  const [activeSessionByProject, setActiveSessionByProject] = useState<Record<string, string>>(
    persisted.current.lastActiveSessionByProject,
  );
  const [activeProjectId, setActiveProjectId] = useState<string | null>(null);
  const [recentSessionOrder, setRecentSessionOrder] = useState<string[]>(
    persisted.current.recentSessionOrder,
  );
  const [unseenTaskCompletionCountByProject, setUnseenTaskCompletionCountByProject] = useState<Record<string, number>>({});
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    openProjectIdsRef.current = openProjectIds;
    activeProjectIdRef.current = activeProjectId;
  }, [openProjectIds, activeProjectId]);

  const updatePendingWorkspaceState = useCallback(
    (updater: (state: Required<ProjectWorkspaceStateDto>) => ProjectWorkspaceStateDto | Required<ProjectWorkspaceStateDto>) => {
      if (workspaceStateLoadedRef.current) {
        return;
      }
      workspaceStateDirtyRef.current = true;
      const currentState = pendingWorkspaceStateRef.current ?? normalizeWorkspaceState({
        openProjectIds: openProjectIdsRef.current,
        activeProjectId: activeProjectIdRef.current,
      });
      pendingWorkspaceStateRef.current = normalizeWorkspaceState(updater(currentState));
    },
    [],
  );

  useEffect(() => {
    const state: PersistedState = {
      lastActiveSessionByProject: activeSessionByProject,
      recentSessionOrder,
    };
    savePersistedState(state);
  }, [activeSessionByProject, recentSessionOrder]);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      let workspaceState = normalizeWorkspaceState();
      try {
        workspaceState = normalizeWorkspaceState(await api.projects.getWorkspaceState());
      } catch {
      }

      let restoredIds = workspaceState.openProjectIds;
      let restoredProjects: Record<string, ProjectDto> = {};

      if (restoredIds.length > 0) {
        const loaded = await loadProjectsByIds(restoredIds);
        restoredIds = loaded.openProjectIds;
        restoredProjects = loaded.projects;
      }

      let restoredActiveProjectId = workspaceState.activeProjectId;
      if (restoredActiveProjectId && !restoredIds.includes(restoredActiveProjectId)) {
        restoredActiveProjectId = restoredIds[0] ?? null;
      }

      if (restoredIds.length === 0 && (persisted.current.openProjectIds?.length ?? 0) > 0) {
        const legacyLoaded = await loadProjectsByIds(persisted.current.openProjectIds ?? []);
        restoredIds = legacyLoaded.openProjectIds;
        restoredProjects = legacyLoaded.projects;
        restoredActiveProjectId = persisted.current.activeProjectId && restoredIds.includes(persisted.current.activeProjectId)
          ? persisted.current.activeProjectId
          : restoredIds[0] ?? null;
        if (restoredIds.length > 0) {
          api.projects.updateWorkspaceState({
            openProjectIds: restoredIds,
            activeProjectId: restoredActiveProjectId,
          }).catch(() => {});
        }
      }

      if (cancelled) {
        return;
      }

      if (workspaceStateDirtyRef.current) {
        const pendingWorkspaceState = pendingWorkspaceStateRef.current ?? normalizeWorkspaceState({
          openProjectIds: openProjectIdsRef.current,
          activeProjectId: activeProjectIdRef.current,
        });
        const mergedOpenProjectIds = Array.from(new Set([
          ...restoredIds,
          ...pendingWorkspaceState.openProjectIds,
        ]));
        const mergedWorkspaceState = normalizeWorkspaceState({
          openProjectIds: mergedOpenProjectIds,
          activeProjectId: pendingWorkspaceState.activeProjectId ?? restoredActiveProjectId,
        });
        const mergedProjects = { ...restoredProjects, ...projects };
        pendingWorkspaceStateRef.current = null;
        workspaceStateLoadedRef.current = true;
        setOpenProjectIds(mergedWorkspaceState.openProjectIds);
        setProjects(mergedProjects);
        setActiveProjectId(mergedWorkspaceState.activeProjectId);
        api.projects.updateWorkspaceState(mergedWorkspaceState).catch(() => {});
        setLoading(false);
        return;
      }

      setOpenProjectIds(restoredIds);
      setProjects(restoredProjects);
      setActiveProjectId(restoredActiveProjectId ?? null);
      workspaceStateLoadedRef.current = true;
      setLoading(false);
    })();

    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (!workspaceStateLoadedRef.current) {
      return;
    }
    api.projects.updateWorkspaceState({
      openProjectIds,
      activeProjectId,
    }).catch(() => {});
  }, [openProjectIds, activeProjectId]);

  useEffect(() => {
    if (!workspaceStateLoadedRef.current) {
      return;
    }
    if (openProjectIds.length === 0) {
      setSessionsByProject({});
      setActiveSessionByProject((prev) => {
        const next = Object.fromEntries(Object.entries(prev).filter(([projectId]) => openProjectIds.includes(projectId)));
        return Object.keys(next).length === Object.keys(prev).length ? prev : next;
      });
      setRecentSessionOrder([]);
      return;
    }
    let cancelled = false;

    Promise.all(
      openProjectIds.map(async (projectId) => {
        try {
          const data = await api.sessions.list(projectId);
          return { projectId, sessions: keepSessionsOwnedByProject(projectId, data.sessions) };
        } catch {
          return { projectId, sessions: [] as SessionDto[] };
        }
      }),
    ).then((results) => {
      if (cancelled) return;
      const map: Record<string, SessionDto[]> = {};
      const validSessionIds = new Set<string>();
      for (const r of results) {
        map[r.projectId] = r.sessions;
        for (const session of r.sessions) {
          validSessionIds.add(session.sessionId);
        }
      }
      setSessionsByProject(map);
      setActiveSessionByProject((prev) => {
        let changed = false;
        const next: Record<string, string> = {};
        for (const [projectId, sessionId] of Object.entries(prev)) {
          const sessions = map[projectId] ?? [];
          if (sessions.some((session) => session.sessionId === sessionId)) {
            next[projectId] = sessionId;
          } else {
            changed = true;
          }
        }
        return changed ? next : prev;
      });
      setRecentSessionOrder((prev) => prev.filter((sessionId) => validSessionIds.has(sessionId)));
    });

    return () => {
      cancelled = true;
    };
  }, [openProjectIds]);

  const openProject = useCallback((project: ProjectDto) => {
    updatePendingWorkspaceState((state) => {
      if (state.openProjectIds.includes(project.id)) {
        return {
          openProjectIds: state.openProjectIds,
          activeProjectId: project.id,
        };
      }
      return {
        openProjectIds: [...state.openProjectIds, project.id],
        activeProjectId: project.id,
      };
    });
    setOpenProjectIds((prev) => {
      if (prev.includes(project.id)) return prev;
      return [...prev, project.id];
    });
    setProjects((prev) => ({ ...prev, [project.id]: project }));
    setActiveProjectId(project.id);
    api.sessions.list(project.id).then((data) => {
      setSessionsByProject((prev) => ({
        ...prev,
        [project.id]: filterSessionsForProject(project, data.sessions),
      }));
    }).catch(() => {});
  }, [updatePendingWorkspaceState]);

  const updateProject = useCallback((project: ProjectDto) => {
    setProjects((prev) => ({
      ...prev,
      [project.id]: project,
    }));
    setSessionsByProject((prev) => ({
      ...prev,
      [project.id]: filterSessionsForProject(project, prev[project.id] ?? []),
    }));
  }, []);

  const closeProject = useCallback((projectId: string) => {
    updatePendingWorkspaceState((state) => {
      const openIds = state.openProjectIds.filter((id) => id !== projectId);
      return {
        openProjectIds: openIds,
        activeProjectId: state.activeProjectId === projectId ? (openIds[0] ?? null) : state.activeProjectId,
      };
    });
    setOpenProjectIds((prev) => prev.filter((id) => id !== projectId));
    setProjects((prev) => {
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setSessionsByProject((prev) => {
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setActiveSessionByProject((prev) => {
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setRecentSessionOrder((prev) => {
      const sessionsOfProject = new Set(
        Object.entries(activeSessionByProject)
          .filter(([pid]) => pid === projectId)
          .map(([, sid]) => sid),
        );
      return prev.filter((sid) => !sessionsOfProject.has(sid));
    });
    setUnseenTaskCompletionCountByProject((prev) => {
      if (!(projectId in prev)) {
        return prev;
      }
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setActiveProjectId((prev) => {
      if (prev !== projectId) {
        return prev;
      }
      const remaining = openProjectIds.filter((id) => id !== projectId);
      return remaining[0] ?? null;
    });
  }, [activeSessionByProject, openProjectIds, updatePendingWorkspaceState]);

  const setActiveProject = useCallback((projectId: string | null) => {
    updatePendingWorkspaceState((state) => ({
      openProjectIds: state.openProjectIds,
      activeProjectId: projectId,
    }));
    setActiveProjectId(projectId);
    if (projectId) {
      setUnseenTaskCompletionCountByProject((prev) => {
        if (!(projectId in prev)) {
          return prev;
        }
        const next = { ...prev };
        delete next[projectId];
        return next;
      });
    }
  }, [updatePendingWorkspaceState]);

  const setActiveSession = useCallback((projectId: string, sessionId: string) => {
    updatePendingWorkspaceState((state) => ({
      openProjectIds: state.openProjectIds,
      activeProjectId: projectId,
    }));
    setActiveProjectId(projectId);
    setActiveSessionByProject((prev) => ({ ...prev, [projectId]: sessionId }));
    setUnseenTaskCompletionCountByProject((prev) => {
      if (!(projectId in prev)) {
        return prev;
      }
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setRecentSessionOrder((prev) => {
      const filtered = prev.filter((s) => s !== sessionId);
      return [sessionId, ...filtered].slice(0, 20);
    });
  }, [updatePendingWorkspaceState]);

  const addSession = useCallback((projectId: string, session: SessionDto) => {
    setSessionsByProject((prev) => {
      const current = prev[projectId] ?? [];
      const next = current.filter((item) => item.sessionId !== session.sessionId);
      return {
        ...prev,
        [projectId]: [session, ...next],
      };
    });
  }, []);

  const removeSession = useCallback((projectId: string, sessionId: string) => {
    setSessionsByProject((prev) => ({
      ...prev,
      [projectId]: (prev[projectId] ?? []).filter((session) => session.sessionId !== sessionId),
    }));
    setActiveSessionByProject((prev) => {
      if (prev[projectId] !== sessionId) {
        return prev;
      }
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
    setRecentSessionOrder((prev) => prev.filter((sid) => sid !== sessionId));
  }, []);

  const refreshSessions = useCallback(async (projectId: string) => {
    try {
      const data = await api.sessions.list(projectId);
      const sessions = keepSessionsOwnedByProject(projectId, data.sessions);
      setSessionsByProject((prev) => ({
        ...prev,
        [projectId]: sessions,
      }));
      setActiveSessionByProject((prev) => {
        const activeSessionId = prev[projectId];
        if (!activeSessionId || sessions.some((session) => session.sessionId === activeSessionId)) {
          return prev;
        }
        const next = { ...prev };
        delete next[projectId];
        return next;
      });
    } catch {
    }
  }, []);

  const refreshProject = useCallback(async (projectId: string): Promise<ProjectDto | null> => {
    try {
      const project = await api.projects.get(projectId);
      updateProject(project);
      return project;
    } catch {
      return null;
    }
  }, [updateProject]);

  const ensureProjectSession = useCallback(async (projectId: string, worktreeId?: string): Promise<SessionDto> => {
    setActiveProjectId(projectId);
    const data = await api.sessions.list(projectId);
    const sessions = keepSessionsOwnedByProject(projectId, data.sessions);
    setSessionsByProject((prev) => ({
      ...prev,
      [projectId]: sessions,
    }));

    const activeSessionId = activeSessionByProject[projectId];
    const sessionsForWorktree = worktreeId
      ? sessions.filter((session) => session.worktreeId === worktreeId)
      : sessions;
    const existingSession = sessionsForWorktree.find((session) => session.sessionId === activeSessionId) ?? sessionsForWorktree[0];
    if (existingSession) {
      setActiveSession(projectId, existingSession.sessionId);
      return existingSession;
    }

    const createdSession = await api.sessions.create(worktreeId ? { projectId, worktreeId } : { projectId });
    setSessionsByProject((prev) => ({
      ...prev,
      [projectId]: [createdSession, ...(prev[projectId] ?? [])],
    }));
    setActiveSession(projectId, createdSession.sessionId);
    return createdSession;
  }, [activeSessionByProject, setActiveSession]);

  const registerTaskCompletion = useCallback((completion: TaskCompletionDto, visible: boolean) => {
    const projectId = completion.projectId ?? undefined;
    if (!projectId || visible || !openProjectIds.includes(projectId)) {
      return;
    }
    setUnseenTaskCompletionCountByProject((prev) => ({
      ...prev,
      [projectId]: (prev[projectId] ?? 0) + 1,
    }));
  }, [openProjectIds]);

  const clearTaskCompletionNotifications = useCallback((projectId: string) => {
    setUnseenTaskCompletionCountByProject((prev) => {
      if (!(projectId in prev)) {
        return prev;
      }
      const next = { ...prev };
      delete next[projectId];
      return next;
    });
  }, []);

  const getActiveProject = useCallback((): ProjectDto | null => {
    if (activeProjectId && openProjectIds.includes(activeProjectId)) {
      return projects[activeProjectId] ?? null;
    }
    if (recentSessionOrder.length === 0) return null;
    const lastSessionId = recentSessionOrder[0];
    for (const pid of openProjectIds) {
      if (activeSessionByProject[pid] === lastSessionId) {
        return projects[pid] ?? null;
      }
    }
    return null;
  }, [activeProjectId, recentSessionOrder, openProjectIds, activeSessionByProject, projects]);

  const getActiveProjectForSession = useCallback(
    (sessionId: string): ProjectDto | null => {
      for (const pid of openProjectIds) {
        if (activeSessionByProject[pid] === sessionId) {
          return projects[pid] ?? null;
        }
        const sessions = sessionsByProject[pid] ?? [];
        if (sessions.some((s) => s.sessionId === sessionId)) {
          return projects[pid] ?? null;
        }
      }
      return null;
    },
    [openProjectIds, activeSessionByProject, sessionsByProject, projects],
  );

  const getSessionById = useCallback(
    (sessionId: string): SessionDto | null => {
      for (const projectId of openProjectIds) {
        const session = (sessionsByProject[projectId] ?? []).find((item) => item.sessionId === sessionId);
        if (session) {
          return session;
        }
      }
      return null;
    },
    [openProjectIds, sessionsByProject],
  );

  const value: MultiProjectContextType = {
    openProjectIds,
    projects,
    sessionsByProject,
    activeSessionByProject,
    activeProjectId,
    recentSessionOrder,
    unseenTaskCompletionCountByProject,
    loading,
    openProject,
    updateProject,
    closeProject,
    setActiveProject,
    setActiveSession,
    addSession,
    removeSession,
    refreshSessions,
    refreshProject,
    ensureProjectSession,
    registerTaskCompletion,
    clearTaskCompletionNotifications,
    getActiveProject,
    getActiveProjectForSession,
    getSessionById,
  };

  return <MultiProjectContext.Provider value={value}>{children}</MultiProjectContext.Provider>;
}

export function useMultiProject(): MultiProjectContextType {
  const ctx = useContext(MultiProjectContext);
  if (!ctx) {
    throw new Error('useMultiProject must be used within a MultiProjectProvider');
  }
  return ctx;
}
