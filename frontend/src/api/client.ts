import type {
  SessionListDto,
  SessionDto,
  WebSettings,
  ApprovalRecord,
  RunDto,
  ProjectListDto,
  ProjectDto,
  ProjectWorkspaceStateDto,
  ProjectCreateRequest,
  DirectoryListing,
  FileContent,
  SearchResult,
  CredentialsDto,
  CredentialsUpdateRequest,
  McpConfigDto,
  ComposerSubmitRequest,
  CommandDto,
  CommandExecuteResponse,
  MessageSubmitResponseDto,
  TerminalDto,
  TerminalListDto,
  TerminalCreateRequest,
  SessionContextDto,
  SessionGitDiffDto,
  SessionGitFileContentCompareDto,
  SessionGitStatusDto,
  PendingQuestionAnswerRequestDto,
  TaskCompletionListDto,
  ServiceStatusDto,
  ReferenceListDto,
} from '../types/api';

async function apiFetch<T>(path: string, options?: RequestInit): Promise<T> {
  const res = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options?.headers },
    ...options,
  });
  if (!res.ok) {
    let errorMessage = `API ${options?.method ?? 'GET'} ${path} failed: ${res.status}`;
    try {
      const payload = await res.json() as { error?: string };
      if (typeof payload.error === 'string' && payload.error.trim()) {
        errorMessage = payload.error;
      }
    } catch {
    }
    throw new Error(errorMessage);
  }
  if (res.status === 204 || res.status === 205) {
    return undefined as T;
  }
  return res.json() as Promise<T>;
}

export interface ApprovalResolutionDto {
  approval: ApprovalRecord;
  run: RunDto;
}

export const api = {
  sessions: {
    list: (projectId?: string) => {
      const query = projectId
        ? `?projectId=${encodeURIComponent(projectId)}`
        : '';
      return apiFetch<SessionListDto>(`/api/sessions${query}`);
    },
    create: (body?: Record<string, unknown>) =>
      apiFetch<SessionDto>('/api/sessions', {
        method: 'POST',
        ...(body ? { body: JSON.stringify(body) } : {}),
      }),
    get: (id: string) => apiFetch<SessionDto>(`/api/sessions/${id}`),
    rollbackToMessage: async (id: string, rollbackIndex: number) => {
      const response = await apiFetch<CommandExecuteResponse>('/api/commands/execute', {
        method: 'POST',
        body: JSON.stringify({
          command: 'rewind',
          arguments: ['jump', String(rollbackIndex)],
          sessionId: id,
        }),
      });
      if (!response.success) {
        throw new Error(response.output || 'Failed to roll back session');
      }
      return response;
    },
    getContext: (id: string) => apiFetch<SessionContextDto>(`/api/sessions/${id}/context`),
    getGitStatus: (id: string) => apiFetch<SessionGitStatusDto>(`/api/sessions/${id}/git`),
    getGitDiff: (id: string, path: string) => {
      const query = new URLSearchParams({ path });
      return apiFetch<SessionGitDiffDto>(`/api/sessions/${id}/git/diff?${query.toString()}`);
    },
    getGitFileContent: (id: string, path: string, compare: boolean = false) => {
      const query = new URLSearchParams({ path, compare: String(compare) });
      return apiFetch<SessionGitFileContentCompareDto>(`/api/sessions/${id}/git/file-content?${query.toString()}`);
    },
    submitRun: (id: string, body: ComposerSubmitRequest) =>
      apiFetch<MessageSubmitResponseDto>(`/api/sessions/${id}/runs`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    answerPendingQuestion: (id: string, runId: string, body: PendingQuestionAnswerRequestDto) =>
      apiFetch<RunDto>(`/api/sessions/${id}/runs/${runId}/answer`, {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    delete: (id: string) =>
      apiFetch<void>(`/api/sessions/${id}`, {
        method: 'DELETE',
      }),
    rename: (id: string, name: string) =>
      apiFetch<SessionDto>(`/api/sessions/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ name }),
      }),
    approvals: (id: string) => apiFetch<ApprovalRecord[]>(`/api/sessions/${id}/approvals`),
    approveApproval: (sessionId: string, approvalId: string) =>
      apiFetch<ApprovalResolutionDto>(
        `/api/sessions/${sessionId}/approvals/${approvalId}/approve`,
        { method: 'POST' },
      ),
    denyApproval: (sessionId: string, approvalId: string) =>
      apiFetch<ApprovalResolutionDto>(
        `/api/sessions/${sessionId}/approvals/${approvalId}/deny`,
        { method: 'POST' },
      ),
    listTerminals: (id: string) => apiFetch<TerminalListDto>(`/api/sessions/${id}/terminals`),
    createTerminal: (id: string, body?: TerminalCreateRequest) =>
      apiFetch<TerminalDto>(`/api/sessions/${id}/terminals`, {
        method: 'POST',
        ...(body ? { body: JSON.stringify(body) } : {}),
      }),
    closeTerminal: (id: string, terminalId: string) =>
      apiFetch<void>(`/api/sessions/${id}/terminals/${terminalId}`, {
        method: 'DELETE',
      }),
  },
  settings: {
    get: () => apiFetch<WebSettings>('/api/settings'),
    update: (body: Partial<WebSettings>) =>
      apiFetch<WebSettings>('/api/settings', {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
  },
  projects: {
    list: () => apiFetch<ProjectListDto>('/api/projects'),
    create: (body: ProjectCreateRequest) =>
      apiFetch<ProjectDto>('/api/projects', {
        method: 'POST',
        body: JSON.stringify(body),
      }),
    get: (id: string) => apiFetch<ProjectDto>(`/api/projects/${id}`),
    getWorkspaceState: () => apiFetch<ProjectWorkspaceStateDto>('/api/projects/workspace-state'),
    updateWorkspaceState: (body: ProjectWorkspaceStateDto) =>
      apiFetch<ProjectWorkspaceStateDto>('/api/projects/workspace-state', {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
    rename: (id: string, name: string) =>
      apiFetch<ProjectDto>(`/api/projects/${id}`, {
        method: 'PATCH',
        body: JSON.stringify({ name }),
      }),
    enableWorkspace: (id: string) =>
      apiFetch<ProjectDto>(`/api/projects/${id}/workspace/enable`, {
        method: 'POST',
      }),
    createWorktree: (id: string, name: string) =>
      apiFetch<ProjectDto>(`/api/projects/${id}/worktrees`, {
        method: 'POST',
        body: JSON.stringify({ name }),
      }),
    deleteWorktree: (id: string, worktreeId: string) =>
      apiFetch<ProjectDto>(`/api/projects/${id}/worktrees/${worktreeId}`, {
        method: 'DELETE',
      }),
    remove: (id: string) =>
      apiFetch<void>(`/api/projects/${id}`, { method: 'DELETE' }),
  },
  files: {
    tree: (projectPath: string, dirPath: string) =>
      apiFetch<DirectoryListing>(
        `/api/files/tree?projectPath=${encodeURIComponent(projectPath)}&dirPath=${encodeURIComponent(dirPath)}`,
      ),
    content: (projectPath: string, filePath: string) =>
      apiFetch<FileContent>(
        `/api/files/content?projectPath=${encodeURIComponent(projectPath)}&filePath=${encodeURIComponent(filePath)}`,
      ),
  },
  search: {
    directories: (query: string) =>
      apiFetch<SearchResult[]>(
        `/api/search/directories?query=${encodeURIComponent(query)}`,
      ),
  },
  credentials: {
    get: () => apiFetch<CredentialsDto>('/api/credentials'),
    update: (body: CredentialsUpdateRequest) =>
      apiFetch<CredentialsDto>('/api/credentials', {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
  },
  mcpConfig: {
    get: () => apiFetch<McpConfigDto>('/api/mcp-config'),
    update: (body: McpConfigDto) =>
      apiFetch<McpConfigDto>('/api/mcp-config', {
        method: 'PUT',
        body: JSON.stringify(body),
      }),
  },
  commands: {
    list: () => apiFetch<CommandDto[]>('/api/commands'),
    resolvePrompt: (command: string, args: string[]) =>
      apiFetch<{ commandName: string; visiblePrompt: string; promptBacked: boolean }>('/api/commands/resolve-prompt', {
        method: 'POST',
        body: JSON.stringify({
          command,
          arguments: args,
        }),
      }),
    execute: (command: string, args: string[], sessionId?: string) =>
      apiFetch<CommandExecuteResponse>('/api/commands/execute', {
        method: 'POST',
        body: JSON.stringify({
          command,
          arguments: args,
          sessionId: sessionId ?? null,
        }),
      }),
    releaseAudio: (token: string) =>
      apiFetch<void>(`/api/commands/audio/${encodeURIComponent(token)}`, {
        method: 'DELETE',
      }),
  },
  tasks: {
    completions: (since?: number) => {
      const query = typeof since === 'number' ? `?since=${encodeURIComponent(String(since))}` : '';
      return apiFetch<TaskCompletionListDto>(`/api/tasks/completions${query}`);
    },
  },
  system: {
    status: () => apiFetch<ServiceStatusDto>('/api/system/status'),
  },
  references: {
    list: () => apiFetch<ReferenceListDto>('/api/references'),
  },
};
