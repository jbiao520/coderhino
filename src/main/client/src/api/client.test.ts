import { describe, it, expect, vi, beforeEach } from 'vitest';
import { api } from '../api/client';

describe('api.sessions.rename', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('calls PATCH /api/sessions/:id with name', async () => {
    const mockResponse = {
      sessionId: 'ses-1',
      createdAt: '2026-04-07T10:00:00Z',
      updatedAt: '2026-04-07T10:00:00Z',
      status: 'ACTIVE',
      activeRun: null,
      messages: [],
      name: 'New Name',
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResponse,
    });

    const result = await api.sessions.rename('ses-1', 'New Name');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/ses-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'New Name' }),
    });
    expect(result.name).toBe('New Name');
  });

  it('throws on failed request', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({ error: 'Session not found' }),
    });

    await expect(api.sessions.rename('missing', 'name')).rejects.toThrow('Session not found');
  });

  it('calls PATCH /api/projects/:id with name', async () => {
    const mockResponse = {
      id: 'proj-1',
      name: 'Renamed Project',
      path: '/tmp/proj-1',
      lastOpened: '2026-04-07T10:00:00Z',
      createdAt: '2026-04-07T10:00:00Z',
      workspaceEnabled: false,
      worktrees: [],
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResponse,
    });

    const result = await api.projects.rename('proj-1', 'Renamed Project');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/projects/proj-1', {
      method: 'PATCH',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name: 'Renamed Project' }),
    });
    expect(result.name).toBe('Renamed Project');
  });

  it('loads persisted project workspace state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ openProjectIds: ['proj-1'], activeProjectId: 'proj-1' }),
    });

    const result = await api.projects.getWorkspaceState();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/projects/workspace-state', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.openProjectIds).toEqual(['proj-1']);
    expect(result.activeProjectId).toBe('proj-1');
  });

  it('updates persisted project workspace state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ openProjectIds: ['proj-1', 'proj-2'], activeProjectId: 'proj-2' }),
    });

    const result = await api.projects.updateWorkspaceState({ openProjectIds: ['proj-1', 'proj-2'], activeProjectId: 'proj-2' });

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/projects/workspace-state', {
      method: 'PUT',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ openProjectIds: ['proj-1', 'proj-2'], activeProjectId: 'proj-2' }),
    });
    expect(result.activeProjectId).toBe('proj-2');
  });

  it('treats 204 delete responses as success without parsing json', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      status: 204,
      json: vi.fn(),
    });

    await expect(api.sessions.delete('ses-1')).resolves.toBeUndefined();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/ses-1', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
    });
  });

  it('loads session git status', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }],
        unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }],
      }),
    });

    const result = await api.sessions.getGitStatus('ses-1');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/ses-1/git', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.trackedChanges[0]?.path).toBe('src/App.tsx');
    expect(result.unversionedFiles[0]?.path).toEqual('notes/todo.md');
  });

  it('loads session git diff for a tracked file', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        kind: 'tracked',
        path: 'src/App.tsx',
        diff: 'diff --git a/src/App.tsx b/src/App.tsx',
      }),
    });

    const result = await api.sessions.getGitDiff('ses-1', 'src/App.tsx');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/ses-1/git/diff?path=src%2FApp.tsx', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.path).toBe('src/App.tsx');
    expect(result.kind).toBe('tracked');
    expect(result.diff).toContain('diff --git');
  });

  it('loads session git diff for an unversioned file', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        kind: 'unversioned',
        path: 'notes/todo.md',
        diff: 'diff --git a/notes/todo.md b/notes/todo.md',
      }),
    });

    const result = await api.sessions.getGitDiff('ses-1', 'notes/todo.md');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/sessions/ses-1/git/diff?path=notes%2Ftodo.md', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.kind).toBe('unversioned');
    expect(result.path).toBe('notes/todo.md');
  });

  it('lists commands', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => [{ name: 'status', description: 'Show status', aliases: [], webCompatible: true, promptBacked: false }],
    });

    const result = await api.commands.list();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/commands', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result[0]?.name).toBe('status');
  });

  it('executes commands', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ prompt: '/status', output: 'ok', success: true, commandName: 'status', audio: null }),
    });

    const result = await api.commands.execute('status', [], 'ses-1');

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/commands/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command: 'status', arguments: [], sessionId: 'ses-1' }),
    });
    expect(result.prompt).toBe('/status');
    expect(result.commandName).toBe('status');
  });

  it('resolves prompt-backed command display text', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ commandName: 'opsx-propose', visiblePrompt: 'this is a propose command, user want you to query weather for : shanghai', promptBacked: true }),
    });

    const result = await api.commands.resolvePrompt('opsx-propose', ['shanghai']);

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/commands/resolve-prompt', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command: 'opsx-propose', arguments: ['shanghai'] }),
    });
    expect(result.visiblePrompt).toContain('query weather for : shanghai');
    expect(result.promptBacked).toBe(true);
  });

  it('releases command audio', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      status: 204,
      json: vi.fn(),
    });

    await expect(api.commands.releaseAudio('tok-1')).resolves.toBeUndefined();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/commands/audio/tok-1', {
      method: 'DELETE',
      headers: { 'Content-Type': 'application/json' },
    });
  });

  it('loads task completions with since cursor', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        completions: [{
          completionId: 'run-1',
          taskId: 'task-1',
          runId: 'run-1',
          description: 'done',
          projectId: 'proj-1',
          sessionId: 'ses-1',
          completedAt: '2026-04-12T00:00:00Z',
        }],
      }),
    });

    const result = await api.tasks.completions(1234);

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/tasks/completions?since=1234', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.completions[0]?.taskId).toBe('task-1');
    expect(result.completions[0]?.runId).toBe('run-1');
  });

  it('rolls back a session message through the rewind command', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ prompt: '/rewind jump 2', output: 'Rewound', success: true, commandName: 'rewind' }),
    });

    const result = await api.sessions.rollbackToMessage('ses-1', 2);

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/commands/execute', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ command: 'rewind', arguments: ['jump', '2'], sessionId: 'ses-1' }),
    });
    expect(result.success).toBe(true);
  });

  it('creates and lists terminals', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ terminalId: 'term-1', label: 'Terminal 1', status: 'RUNNING', cwd: '/tmp/proj-1', createdAt: '2026-04-11T00:00:00Z' }),
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ terminals: [{ terminalId: 'term-1', label: 'Terminal 1', status: 'RUNNING', cwd: '/tmp/proj-1', createdAt: '2026-04-11T00:00:00Z' }] }),
      });

    const created = await api.sessions.createTerminal('ses-1', { worktreeId: 'default' });
    const listed = await api.sessions.listTerminals('ses-1');

    expect(globalThis.fetch).toHaveBeenNthCalledWith(1, '/api/sessions/ses-1/terminals', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ worktreeId: 'default' }),
    });
    expect(globalThis.fetch).toHaveBeenNthCalledWith(2, '/api/sessions/ses-1/terminals', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(created.terminalId).toBe('term-1');
    expect(listed.terminals).toHaveLength(1);
  });

  it('lists bundled references', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        references: [{ id: 'api-guidelines', label: 'Api Guidelines', markdown: '# API Guidelines' }],
      }),
    });

    const result = await api.references.list();

    expect(globalThis.fetch).toHaveBeenCalledWith('/api/references', {
      headers: { 'Content-Type': 'application/json' },
    });
    expect(result.references[0]?.id).toBe('api-guidelines');
  });
});
