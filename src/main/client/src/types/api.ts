// TypeScript types matching backend DTOs

export interface RunDto {
  runId: string;
  status: string;
}

export interface SessionMessageDto {
  type: string;
  content: string;
  timestamp?: string;
  rollbackIndex?: number | null;
  activityTimeline?: ActiveRunTranscriptItemDto[] | null;
  fileSummary?: ActiveRunFileSummaryDto | null;
}

export interface ActiveRunTranscriptItemDto {
  kind: 'assistant' | 'tool' | 'thinking' | 'tool-input' | 'status';
  content?: string | null;
  toolName?: string | null;
  toolUseId?: string | null;
  argumentsJson?: string | null;
  output?: string | null;
}

export interface PendingQuestionDto {
  runId?: string | null;
  toolUseId: string;
  question: string;
  choices: string[];
}

export interface ActiveRunUsageDto {
  inputTokens: number;
  outputTokens: number;
  cacheCreationTokens: number;
  cacheReadTokens: number;
  toolUses: number;
  contextLength: number;
}

export interface ActiveRunFileSummaryDto {
  totalChanges: number;
  created: string[];
  modified: string[];
  deleted: string[];
}

export interface ActiveRunStateDto {
  runId: string;
  transcript: ActiveRunTranscriptItemDto[];
  usage?: ActiveRunUsageDto | null;
  lastSequence?: number | null;
  terminalStatus?: string | null;
  finalText?: string | null;
  error?: string | null;
  fileSummary?: ActiveRunFileSummaryDto | null;
  pendingQuestion?: PendingQuestionDto | null;
}

export interface SessionDto {
  sessionId: string;
  createdAt: string;   // ISO timestamp
  updatedAt: string;   // ISO timestamp
  status: string;      // e.g. "ACTIVE"
  activeRun: RunDto | null;
  activeRunState?: ActiveRunStateDto | null;
  messages: SessionMessageDto[];
  model?: string;
  permissionMode?: string;
  projectId?: string;
  name?: string | null;
  branch?: string | null;
  providerId?: string | null;
  availableProviders?: ProviderOptionDto[];
  worktreeId?: string | null;
  worktree?: WorktreeDto | null;
  planMode?: boolean;
  buildMode?: boolean;
  availableModels?: string[];
  modelMode?: string | null;
  modelModeSupported?: boolean;
  availableModelModes?: string[];
}

export interface SessionUsageSummaryDto {
  inputTokens?: number | null;
  outputTokens?: number | null;
  cacheReadTokens?: number | null;
  cacheWriteTokens?: number | null;
  toolUses?: number | null;
  contextLength?: number | null;
}

export interface SessionContextSummaryDto {
  sessionId: string;
  name?: string | null;
  model?: string | null;
  providerId?: string | null;
  permissionMode?: string | null;
  status: string;
  createdAt: string;
  messageCount: number;
  currentUsage?: SessionUsageSummaryDto | null;
  sessionTotals?: SessionUsageSummaryDto | null;
  activeRun?: RunDto | null;
}

export interface SessionContextRawAiHistoryEntryDto {
  direction: 'request' | 'response' | string;
  timestamp?: string;
  content?: string | null;
}

export interface SessionContextDto {
  summary: SessionContextSummaryDto;
  rawAiHistory: SessionContextRawAiHistoryEntryDto[];
}

export type SessionGitEntryKind = 'tracked' | 'unversioned' | string;

export interface SessionGitEntryDto {
  kind: SessionGitEntryKind;
  path: string;
  status?: string | null;
}

export interface SessionGitStatusDto {
  trackedChanges: SessionGitEntryDto[];
  unversionedFiles: SessionGitEntryDto[];
}

export interface SessionGitDiffDto {
  kind: SessionGitEntryKind;
  path: string;
  diff: string;
}

export interface SessionGitFileContentCompareDto {
  path: string;
  previousContent: string | null;
  currentContent: string | null;
}

export interface TerminalDto {
  terminalId: string;
  label: string;
  status: string;
  cwd: string;
  worktreeId?: string | null;
  createdAt: string;
  exitCode?: number | null;
  message?: string | null;
}

export interface TerminalListDto {
  terminals: TerminalDto[];
}

export interface TerminalCreateRequest {
  label?: string;
  worktreeId?: string | null;
}

export interface CommandDto {
  name: string;
  description: string;
  aliases: string[];
  webCompatible: boolean;
  promptBacked: boolean;
}

export interface ReferenceDto {
  id: string;
  label: string;
  filename: string;
  source?: string | null;
  markdown: string;
}

export interface ReferenceListDto {
  references: ReferenceDto[];
}

export interface CommandExecuteResponse {
  prompt: string;
  output: string;
  success: boolean;
  commandName: string;
  audio?: {
    token: string;
    url: string;
  } | null;
}

export interface ComposerSubmitRequest {
  message: string;
  visiblePrompt?: string;
  model?: string;
  providerId?: string | null;
  buildMode?: boolean;
  planMode?: boolean;
  modelMode?: string | null;
}

export interface MessageSubmitResponseDto {
  runId: string;
  approvalId?: string | null;
  visiblePrompt?: string | null;
}

export interface PendingQuestionAnswerRequestDto {
  toolUseId: string;
  answer: string;
}

export interface ProviderOptionDto {
  id: string;
  name: string;
  models?: string[];
  modelOptions?: ModelOptionDto[];
  unavailable: boolean;
}

export interface ModelOptionDto {
  id: string;
  label: string;
  modelModeSupported: boolean;
  availableModelModes: string[];
}

export interface SessionListDto {
  sessions: SessionDto[];
}

export interface TaskCompletionDto {
  completionId?: string;
  taskId: string;
  runId?: string | null;
  description: string;
  projectId?: string | null;
  sessionId?: string | null;
  completedAt: string;
}

export interface TaskCompletionListDto {
  completions: TaskCompletionDto[];
}

export interface WebSettings {
  defaultPermissionMode: string; // "BYPASS" | "AUTO"
  theme: string;                 // "system" | "dark" | "light"
  defaultModel: string;          // e.g. "MiniMax-M2.7"
  sidebarFontFamily?: string | null;
  sidebarFontSize?: number | null;
  chatFontFamily?: string | null;
  chatFontSize?: number | null;
  referenceSourcePaths?: string[] | null;
}

export interface ApprovalRecord {
  approvalId: string;
  sessionId: string;
  runId: string;
  action: string;
  summary: string;
  status: string;          // "PENDING" | "APPROVED" | "DENIED"
  createdAt: string;
  resolvedAt: string | null;
}

// Project types

export interface ProjectDto {
  id: string;
  name: string;
  path: string;
  lastOpened: string;
  createdAt: string;
  workspaceEnabled: boolean;
  worktrees: WorktreeDto[];
}

export interface WorktreeDto {
  id: string;
  name: string;
  path: string;
  defaultWorktree: boolean;
  managed: boolean;
  branch?: string | null;
  createdAt: string;
}

export interface ProjectListDto {
  projects: ProjectDto[];
  count: number;
}

export interface ProjectWorkspaceStateDto {
  openProjectIds: string[];
  activeProjectId?: string | null;
}

export interface ProjectCreateRequest {
  path: string;
}

// File explorer types

export interface FileNode {
  name: string;
  path: string;
  isDirectory: boolean;
  size: number;
  lastModified: string;
}

export interface DirectoryListing {
  path: string;
  children: FileNode[];
}

export interface FileContent {
  name: string;
  path: string;
  content: string | null;
  size: number;
  truncated: boolean;
  binary: boolean;
}

// Search types

export interface SearchResult {
  path: string;
  name: string;
  matchType: 'EXACT' | 'CONTAINS' | 'STARTS_WITH';
}

// Credentials types

export interface CredentialsDto {
  defaultProviderId: string | null;
  providers: CredentialProviderDto[];
}

export interface CredentialProviderDto {
  id: string;
  name: string;
  apiKeyMasked: string | null;
  apiBaseUrl: string | null;
  models: CredentialProviderModelDto[];
  apiType: 'CLAUDE_CODE' | 'OPENAI';
  hasApiKey: boolean;
}

export interface CredentialProviderModelDto {
  id: string;
  contextWindow: number;
}

export interface CredentialsUpdateRequest {
  defaultProviderId?: string | null;
  providers: CredentialProviderUpdate[];
}

export interface CredentialProviderUpdate {
  id: string;
  name: string;
  apiKey?: string;
  apiBaseUrl?: string | null;
  models: CredentialProviderModelDto[];
  apiType: 'CLAUDE_CODE' | 'OPENAI';
}

export interface McpConfigDto {
  content: string;
}

export interface McpServerStatusDto {
  name: string;
  enabled: boolean;
  connected: boolean;
  status: string;
  command: string;
  commandLine: string[];
  processId?: number | null;
  lastStartedAt?: string | null;
}

export interface LspServerStatusDto {
  language: string;
  enabled: boolean;
  connected: boolean;
  status: string;
  command: string;
  commandLine: string[];
  processId?: number | null;
  lastStartedAt?: string | null;
}

export interface PluginStatusDto {
  id: string;
  name: string;
  version?: string | null;
  description?: string | null;
  status: string;
}

export interface ServiceStatusDto {
  mcpServers: McpServerStatusDto[];
  lspServers: LspServerStatusDto[];
  plugins: PluginStatusDto[];
}
