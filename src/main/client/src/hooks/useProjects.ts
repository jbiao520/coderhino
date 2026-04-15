import { useState, useEffect, useCallback } from 'react';
import { api } from '../api/client';
import type { ProjectDto } from '../types/api';

/**
 * @deprecated Active project state is now managed by MultiProjectContext.
 * This hook is retained for project listing/persistence logic only.
 */
interface UseProjectsResult {
  projects: ProjectDto[];
  recentProjects: ProjectDto[];
  activeProject: ProjectDto | null;
  loading: boolean;
  error: string | null;
  setActiveProject: (project: ProjectDto) => void;
  clearActiveProject: () => void;
  createProject: (path: string) => Promise<ProjectDto>;
  removeProject: (id: string) => Promise<void>;
  reload: () => void;
}

export function useProjects(): UseProjectsResult {
  const [projects, setProjects] = useState<ProjectDto[]>([]);
  const [activeProject, setActiveProjectState] = useState<ProjectDto | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api.projects
      .list()
      .then((data) => {
        if (!cancelled) setProjects(data.projects);
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : 'Failed to load projects');
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);

  const recentProjects = projects
    .slice()
    .sort((a, b) => new Date(b.lastOpened).getTime() - new Date(a.lastOpened).getTime())
    .slice(0, 5);

  const setActiveProject = useCallback((project: ProjectDto) => {
    setActiveProjectState(project);
  }, []);

  const clearActiveProject = useCallback(() => {
    setActiveProjectState(null);
  }, []);

  const createProject = useCallback(
    async (path: string): Promise<ProjectDto> => {
      const project = await api.projects.create({ path });
      reload();
      return project;
    },
    [reload],
  );

  const removeProject = useCallback(
    async (id: string): Promise<void> => {
      await api.projects.remove(id);
      setActiveProjectState((prev) => (prev?.id === id ? null : prev));
      reload();
    },
    [reload],
  );

  return {
    projects,
    recentProjects,
    activeProject,
    loading,
    error,
    setActiveProject,
    clearActiveProject,
    createProject,
    removeProject,
    reload,
  };
}
