import { renderHook, waitFor, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { useProjects } from '../hooks/useProjects';

const mockProjects = [
  { id: '1', name: 'test-project', path: '/tmp/test-project', lastOpened: '2026-04-08T00:00:00Z', createdAt: '2026-04-08T00:00:00Z' },
  { id: '2', name: 'old-project', path: '/tmp/old-project', lastOpened: '2026-04-01T00:00:00Z', createdAt: '2026-03-01T00:00:00Z' },
  { id: '3', name: 'another-project', path: '/tmp/another', lastOpened: '2026-04-07T00:00:00Z', createdAt: '2026-04-01T00:00:00Z' },
];

const mockProjectList = { projects: mockProjects, count: mockProjects.length };

describe('useProjects', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('fetches projects on mount', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockProjectList,
    });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.projects).toHaveLength(3);
    expect(result.current.projects[0]?.name).toBe('test-project');
    expect(result.current.error).toBeNull();
  });

  it('sets error on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
  });

  it('createProject calls POST /api/projects and reloads', async () => {
    const newProject = { id: '4', name: 'new-proj', path: '/tmp/new-proj', lastOpened: '2026-04-08T12:00:00Z', createdAt: '2026-04-08T12:00:00Z' };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockProjectList })
      .mockResolvedValueOnce({ ok: true, json: async () => newProject })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ projects: [...mockProjects, newProject], count: 4 }) });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));

    let created: typeof newProject;
    await act(async () => {
      created = await result.current.createProject('/tmp/new-proj');
    });

    expect(created!).toEqual(newProject);

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const postCall = calls.find(
      (c: unknown[]) => Array.isArray(c) && c[1] && (c[1] as RequestInit).method === 'POST',
    );
    expect(postCall).toBeTruthy();
    expect((postCall![0] as string)).toBe('/api/projects');
  });

  it('removeProject calls DELETE and clears active if removed', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockProjectList })
      .mockResolvedValueOnce({ ok: true, json: async () => undefined })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ projects: mockProjects.slice(1), count: 2 }) });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setActiveProject(mockProjects[0]!);
    });
    expect(result.current.activeProject?.id).toBe('1');

    await act(async () => {
      await result.current.removeProject('1');
    });

    expect(result.current.activeProject).toBeNull();

    const calls = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls;
    const deleteCall = calls.find(
      (c: unknown[]) => Array.isArray(c) && c[1] && (c[1] as RequestInit).method === 'DELETE',
    );
    expect(deleteCall).toBeTruthy();
    expect((deleteCall![0] as string)).toBe('/api/projects/1');
  });

  it('setActiveProject and clearActiveProject work', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockProjectList,
    });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));

    act(() => {
      result.current.setActiveProject(mockProjects[0]!);
    });
    expect(result.current.activeProject?.id).toBe('1');

    act(() => {
      result.current.clearActiveProject();
    });
    expect(result.current.activeProject).toBeNull();
  });

  it('recentProjects is sorted by lastOpened desc, max 5', async () => {
    const manyProjects = Array.from({ length: 7 }, (_, i) => ({
      id: String(i + 1),
      name: `proj-${i + 1}`,
      path: `/tmp/proj-${i + 1}`,
      lastOpened: new Date(Date.now() - i * 86400000).toISOString(),
      createdAt: '2026-01-01T00:00:00Z',
    }));

    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ projects: manyProjects, count: 7 }),
    });

    const { result } = renderHook(() => useProjects());
    await waitFor(() => expect(result.current.loading).toBe(false));

    const recent = result.current.recentProjects;
    expect(recent).toHaveLength(5);
    expect(recent[0]?.id).toBe('1');
    expect(recent[4]?.id).toBe('5');
  });
});
