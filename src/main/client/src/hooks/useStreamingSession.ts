import { useEffect, useCallback, useRef, useReducer } from 'react';
import { api } from '../api/client';
import type {
  ActiveRunStateDto,
  ActiveRunTranscriptItemDto,
  ComposerSubmitRequest,
  PendingQuestionDto,
  SessionDto,
  SessionMessageDto,
  RunDto,
} from '../types/api';

export interface ToolActivity {
  toolName: string;
  toolUseId?: string;
  input: Record<string, unknown>;
  output?: string;
}

export interface AssistantTranscriptItem {
  kind: 'assistant';
  content: string;
}

export interface ThinkingTranscriptItem {
  kind: 'thinking';
  content: string;
}

export interface StatusTranscriptItem {
  kind: 'status';
  content: string;
}

export interface ToolInputTranscriptItem {
  kind: 'tool-input';
  toolName: string;
  toolUseId?: string;
  partialJson: string;
}

export interface ToolTranscriptItem extends ToolActivity {
  kind: 'tool';
}

export type RunTranscriptItem = AssistantTranscriptItem | ThinkingTranscriptItem | StatusTranscriptItem | ToolInputTranscriptItem | ToolTranscriptItem;

export interface StreamingMessage {
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp?: string;
  rollbackIndex?: number | null;
  activityTimeline?: RunTranscriptItem[] | null;
  fileSummary?: FileChangeSummary | null;
}

export interface FileChangeSummary {
  totalChanges: number;
  created: string[];
  modified: string[];
  deleted: string[];
}

export interface UsageMetrics {
  inputTokens: number;
  outputTokens: number;
  cacheCreationTokens: number;
  cacheReadTokens: number;
  toolUses: number;
  contextLength: number;
}

export interface PendingQuestion {
  toolUseId: string;
  question: string;
  choices: string[];
}

export interface StreamingState {
  session: SessionDto | null;
  messages: StreamingMessage[];
  runTranscript: RunTranscriptItem[];
  activeRun: RunDto | null;
  runStatus: 'idle' | 'running' | 'completed' | 'error';
  error: string | null;
  loading: boolean;
  chunkCount: number;
  completedRunIds: Set<string>;
  lastEventSequenceByRun: Record<string, number>;
  fileSummary: FileChangeSummary | null;
  usage: UsageMetrics | null;
  pendingQuestion: PendingQuestion | null;
}

type Action =
  | { type: 'SESSION_LOADED'; session: SessionDto }
  | { type: 'SESSION_REFRESHED'; session: SessionDto }
  | { type: 'SESSION_ERROR'; error: string }
  | { type: 'RUN_STARTED'; runId: string }
  | { type: 'TEXT_CHUNK'; runId?: string; text: string; sequence?: number }
  | { type: 'STATUS'; runId?: string; message: string; sequence?: number }
  | { type: 'THINKING_DELTA'; runId?: string; thinking: string; sequence?: number }
  | { type: 'TOOL_INPUT_DELTA'; runId?: string; toolName: string; toolUseId?: string; partialJson: string; sequence?: number }
  | { type: 'TOOL_CALL'; runId?: string; toolName: string; toolUseId?: string; input: Record<string, unknown>; sequence?: number }
  | { type: 'TOOL_RESULT'; runId?: string; toolName: string; toolUseId?: string; output: string; sequence?: number }
  | { type: 'RUN_COMPLETED'; runId?: string; finalText?: string; fileSummary?: FileChangeSummary; sequence?: number }
  | { type: 'RUN_CANCELLED'; runId?: string; sequence?: number }
  | { type: 'RUN_FAILED'; runId?: string; error: string; sequence?: number }
  | { type: 'SSE_ERROR'; error: string }
  | { type: 'USER_MESSAGE'; content: string }
  | { type: 'USER_MESSAGE_UPDATED'; content: string }
  | { type: 'SYSTEM_MESSAGE'; content: string }
  | { type: 'ROLLBACK_COMPLETED'; session: SessionDto }
  | { type: 'USAGE_UPDATE'; usage: UsageMetrics }
  | { type: 'PENDING_QUESTION_UPDATED'; pendingQuestion: PendingQuestion | null };

function mapPendingQuestion(value: PendingQuestionDto | null | undefined): PendingQuestion | null {
  if (!value?.toolUseId || !value.question) {
    return null;
  }
  return {
    toolUseId: value.toolUseId,
    question: value.question,
    choices: Array.isArray(value.choices) ? value.choices : [],
  };
}

function parseEventSequence(value: unknown): number | undefined {
  return typeof value === 'number' && Number.isFinite(value) ? value : undefined;
}

function parseToolInput(argumentsJson?: string | null): Record<string, unknown> {
  if (!argumentsJson) {
    return {};
  }
  try {
    const parsed = JSON.parse(argumentsJson) as unknown;
    return parsed != null && typeof parsed === 'object' ? parsed as Record<string, unknown> : {};
  } catch {
    return {};
  }
}

function markSequence(state: StreamingState, runId: string | undefined, sequence: number | undefined): Record<string, number> {
  if (!runId || sequence == null) {
    return state.lastEventSequenceByRun;
  }
  const current = state.lastEventSequenceByRun[runId];
  if (typeof current === 'number' && current >= sequence) {
    return state.lastEventSequenceByRun;
  }
  return {
    ...state.lastEventSequenceByRun,
    [runId]: sequence,
  };
}

function shouldIgnoreSequencedEvent(state: StreamingState, runId: string | undefined, sequence: number | undefined): boolean {
  if (!runId || sequence == null) {
    return false;
  }
  const current = state.lastEventSequenceByRun[runId];
  return typeof current === 'number' && current >= sequence;
}

function appendTranscriptText(items: RunTranscriptItem[], text: string): RunTranscriptItem[] {
  if (!text) {
    return items;
  }
  const lastItem = items[items.length - 1];
  if (lastItem?.kind === 'assistant') {
    return [
      ...items.slice(0, -1),
      { ...lastItem, content: lastItem.content + text },
    ];
  }
  return [...items, { kind: 'assistant', content: text }];
}

function appendThinkingText(items: RunTranscriptItem[], thinking: string): RunTranscriptItem[] {
  if (!thinking) {
    return items;
  }
  const lastItem = items[items.length - 1];
  if (lastItem?.kind === 'thinking') {
    return [
      ...items.slice(0, -1),
      { ...lastItem, content: lastItem.content + thinking },
    ];
  }
  return [...items, { kind: 'thinking', content: thinking }];
}

function appendStatusText(items: RunTranscriptItem[], message: string): RunTranscriptItem[] {
  if (!message) {
    return items;
  }
  const lastItem = items[items.length - 1];
  if (lastItem?.kind === 'status' && lastItem.content === message) {
    return items;
  }
  return [...items, { kind: 'status', content: message }];
}

function appendToolInputDelta(items: RunTranscriptItem[], toolName: string, toolUseId: string | undefined, partialJson: string): RunTranscriptItem[] {
  if (!partialJson) {
    return items;
  }
  const lastItem = items[items.length - 1];
  if (lastItem?.kind === 'tool-input' && lastItem.toolName === toolName && lastItem.toolUseId === toolUseId) {
    return [
      ...items.slice(0, -1),
      { ...lastItem, partialJson: lastItem.partialJson + partialJson },
    ];
  }
  return [...items, { kind: 'tool-input', toolName, toolUseId, partialJson }];
}

function transcriptText(items: RunTranscriptItem[]): string {
  return items
    .filter((item): item is AssistantTranscriptItem => item.kind === 'assistant')
    .map((item) => item.content)
    .join('');
}

function mapSessionMessages(messages: SessionMessageDto[]): StreamingMessage[] {
  return messages
    .filter((message): message is SessionMessageDto & { type: 'user' | 'assistant' | 'system'; content: string } =>
      (message.type === 'user' || message.type === 'assistant' || message.type === 'system')
        && typeof message.content === 'string',
    )
    .map((message) => ({
      role: message.type,
      content: message.content,
      timestamp: message.timestamp,
      rollbackIndex: message.rollbackIndex ?? null,
      activityTimeline: message.activityTimeline ? mapReplayTranscript(message.activityTimeline) : null,
      fileSummary: message.fileSummary ? {
        totalChanges: message.fileSummary.totalChanges,
        created: message.fileSummary.created,
        modified: message.fileSummary.modified,
        deleted: message.fileSummary.deleted,
      } : null,
    }));
}

function mapReplayTranscript(items: ActiveRunTranscriptItemDto[] | undefined): RunTranscriptItem[] {
  if (!items) {
    return [];
  }
  const transcript: RunTranscriptItem[] = [];
  for (const item of items) {
    if (item.kind === 'assistant') {
      if (item.content) {
        transcript.push({ kind: 'assistant', content: item.content });
      }
      continue;
    }
    if (item.kind === 'thinking') {
      if (item.content) {
        transcript.push({ kind: 'thinking', content: item.content });
      }
      continue;
    }
    if (item.kind === 'status') {
      if (item.content) {
        transcript.push({ kind: 'status', content: item.content });
      }
      continue;
    }
    if (item.kind === 'tool-input') {
      if (!item.toolName || !item.argumentsJson) {
        continue;
      }
      transcript.push({
        kind: 'tool-input',
        toolName: item.toolName,
        toolUseId: item.toolUseId ?? undefined,
        partialJson: item.argumentsJson,
      });
      continue;
    }
    if (!item.toolName) {
      continue;
    }
    transcript.push({
      kind: 'tool',
      toolName: item.toolName,
      toolUseId: item.toolUseId ?? undefined,
      input: parseToolInput(item.argumentsJson),
      output: item.output ?? undefined,
    });
  }
  return transcript;
}

function mapReplayUsage(replayState: ActiveRunStateDto | null | undefined): UsageMetrics | null {
  if (!replayState?.usage) {
    return null;
  }
  return {
    inputTokens: replayState.usage.inputTokens,
    outputTokens: replayState.usage.outputTokens,
    cacheCreationTokens: replayState.usage.cacheCreationTokens,
    cacheReadTokens: replayState.usage.cacheReadTokens,
    toolUses: replayState.usage.toolUses,
    contextLength: replayState.usage.contextLength,
  };
}

function mapReplayFileSummary(replayState: ActiveRunStateDto | null | undefined): FileChangeSummary | null {
  if (!replayState?.fileSummary) {
    return null;
  }
  return {
    totalChanges: replayState.fileSummary.totalChanges,
    created: replayState.fileSummary.created,
    modified: replayState.fileSummary.modified,
    deleted: replayState.fileSummary.deleted,
  };
}

function applySessionSnapshot(base: StreamingState, session: SessionDto, loading: boolean): StreamingState {
  const replayState = session.activeRunState ?? null;
  const runId = replayState?.runId ?? session.activeRun?.runId;
  const nextSequences = { ...base.lastEventSequenceByRun };
  if (runId && replayState?.lastSequence != null) {
    nextSequences[runId] = replayState.lastSequence;
  }
  return {
    ...base,
    session,
    messages: mapSessionMessages(session.messages),
    activeRun: session.activeRun,
    runTranscript: mapReplayTranscript(replayState?.transcript),
    runStatus:
      replayState?.terminalStatus === 'FAILED'
        ? 'error'
        : replayState?.terminalStatus === 'COMPLETED'
          ? 'completed'
          : session.activeRun
            ? 'running'
            : 'idle',
    loading,
    error: replayState?.terminalStatus === 'FAILED' ? replayState.error ?? 'Run failed' : null,
    fileSummary: mapReplayFileSummary(replayState),
    usage: mapReplayUsage(replayState),
    pendingQuestion: mapPendingQuestion(replayState?.pendingQuestion),
    lastEventSequenceByRun: nextSequences,
  };
}

export function reducer(state: StreamingState, action: Action): StreamingState {
  switch (action.type) {
    case 'SESSION_LOADED':
      return applySessionSnapshot(state, action.session, false);

    case 'SESSION_REFRESHED':
      return applySessionSnapshot(state, action.session, state.loading);

    case 'SESSION_ERROR':
      return { ...state, loading: false, error: action.error };

    case 'ROLLBACK_COMPLETED':
      return {
        ...applySessionSnapshot(state, action.session, state.loading),
        runTranscript: [],
        runStatus: action.session.activeRun ? 'running' : 'idle',
        error: null,
        fileSummary: null,
        pendingQuestion: null,
        completedRunIds: new Set<string>(),
        lastEventSequenceByRun: {},
      };

    case 'RUN_STARTED':
      if (state.completedRunIds.has(action.runId)) {
        return state;
      }
      return {
        ...state,
        activeRun: { runId: action.runId, status: 'RUNNING' },
        runStatus: 'running',
        runTranscript: [],
        chunkCount: 0,
        error: null,
        usage: null,
        fileSummary: null,
        pendingQuestion: null,
      };

    case 'TEXT_CHUNK':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: appendTranscriptText(state.runTranscript, action.text),
        chunkCount: state.chunkCount + 1,
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'STATUS':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: appendStatusText(state.runTranscript, action.message),
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'THINKING_DELTA':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: appendThinkingText(state.runTranscript, action.thinking),
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'TOOL_INPUT_DELTA':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: appendToolInputDelta(state.runTranscript, action.toolName, action.toolUseId, action.partialJson),
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'TOOL_CALL':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: [
          ...state.runTranscript,
          { kind: 'tool', toolName: action.toolName, toolUseId: action.toolUseId, input: action.input },
        ],
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'TOOL_RESULT': {
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      const toolIndex = state.runTranscript.findIndex((item) =>
        item.kind === 'tool'
          && item.output === undefined
          && ((action.toolUseId && item.toolUseId === action.toolUseId) || (!action.toolUseId && item.toolName === action.toolName)),
      );
      if (toolIndex < 0) {
        return {
          ...state,
          lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
        };
      }
      return {
        ...state,
        runTranscript: state.runTranscript.map((item, index) =>
          index === toolIndex && item.kind === 'tool'
            ? { ...item, output: action.output }
            : item,
        ),
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };
    }

    case 'RUN_COMPLETED': {
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      const newMessages: StreamingMessage[] = [...state.messages];
      const finalText = action.finalText ?? transcriptText(state.runTranscript);
      const runTranscript = state.runTranscript.length === 0 && finalText
        ? appendTranscriptText([], finalText)
        : state.runTranscript;
      if (finalText) {
        newMessages.push({ role: 'assistant', content: finalText });
      }
      const completedRunIds = new Set(state.completedRunIds);
      if (action.runId) {
        completedRunIds.add(action.runId);
      }
      return {
        ...state,
        messages: newMessages,
        runTranscript,
        activeRun: null,
        runStatus: 'completed',
        chunkCount: 0,
        error: null,
        completedRunIds,
        fileSummary: action.fileSummary ?? null,
        pendingQuestion: null,
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };
    }

    case 'RUN_CANCELLED':
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      return {
        ...state,
        runTranscript: [],
        activeRun: null,
        runStatus: 'idle',
        chunkCount: 0,
        error: null,
        fileSummary: null,
        pendingQuestion: null,
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };

    case 'RUN_FAILED': {
      if (shouldIgnoreSequencedEvent(state, action.runId, action.sequence)) {
        return state;
      }
      const completedRunIds = new Set(state.completedRunIds);
      if (action.runId) {
        completedRunIds.add(action.runId);
      }
      return {
        ...state,
        runTranscript: [],
        activeRun: null,
        runStatus: 'error',
        chunkCount: 0,
        error: action.error,
        pendingQuestion: null,
        completedRunIds,
        lastEventSequenceByRun: markSequence(state, action.runId, action.sequence),
      };
    }

    case 'SSE_ERROR':
      return { ...state, error: action.error };

    case 'USER_MESSAGE':
      return {
        ...state,
        messages: [...state.messages, { role: 'user', content: action.content }],
        completedRunIds: new Set<string>(),
        lastEventSequenceByRun: {},
      };

    case 'USER_MESSAGE_UPDATED': {
      const nextMessages = [...state.messages];
      const lastMessage = nextMessages[nextMessages.length - 1];
      if (lastMessage?.role !== 'user') {
        return state;
      }
      nextMessages[nextMessages.length - 1] = { ...lastMessage, content: action.content };
      return {
        ...state,
        messages: nextMessages,
      };
    }

    case 'SYSTEM_MESSAGE':
      return {
        ...state,
        messages: [...state.messages, { role: 'system', content: action.content }],
      };

    case 'USAGE_UPDATE':
      return { ...state, usage: action.usage };

    case 'PENDING_QUESTION_UPDATED':
      return { ...state, pendingQuestion: action.pendingQuestion };

    default:
      return state;
  }
}

const INITIAL_STATE: StreamingState = {
  session: null,
  messages: [],
  runTranscript: [],
  activeRun: null,
  runStatus: 'idle',
  error: null,
  loading: true,
  chunkCount: 0,
  completedRunIds: new Set<string>(),
  lastEventSequenceByRun: {},
  fileSummary: null,
  usage: null,
  pendingQuestion: null,
};

export interface UseStreamingSessionResult extends StreamingState {
  submitMessage: (request: ComposerSubmitRequest) => Promise<void>;
  answerPendingQuestion: (toolUseId: string, answer: string) => Promise<void>;
  cancelRun: () => Promise<void>;
  appendMessage: (role: 'user' | 'system', content: string) => void;
  rollbackToMessage: (rollbackIndex: number) => Promise<void>;
  refreshSession: () => Promise<SessionDto | undefined>;
}

export function useStreamingSession(sessionId: string | undefined): UseStreamingSessionResult {
  const [state, dispatch] = useReducer(reducer, INITIAL_STATE);
  const chunkCountRef = useRef(0);
  const reconnectTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const esRef = useRef<EventSource | null>(null);
  const reconnectStateRef = useRef<{ runId?: string; afterSequence?: number }>({});

  useEffect(() => {
    chunkCountRef.current = state.chunkCount;
  }, [state.chunkCount]);

  useEffect(() => {
    const runId = state.activeRun?.runId ?? state.session?.activeRunState?.runId;
    reconnectStateRef.current = {
      runId,
      afterSequence: runId ? state.lastEventSequenceByRun[runId] : undefined,
    };
  }, [state.activeRun, state.lastEventSequenceByRun, state.session]);

  const refreshSession = useCallback(async () => {
    if (!sessionId) {
      return undefined;
    }
    const session = await api.sessions.get(sessionId);
    dispatch({ type: 'SESSION_REFRESHED', session });
    return session;
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId) {
      dispatch({ type: 'SESSION_ERROR', error: 'No session ID' });
      return;
    }
    let cancelled = false;
    fetch(`/api/sessions/${sessionId}`)
      .then((res) => {
        if (!res.ok) throw new Error(`API GET /api/sessions/${sessionId} failed: ${res.status}`);
        return res.json() as Promise<SessionDto>;
      })
      .then((data) => {
        if (!cancelled) dispatch({ type: 'SESSION_LOADED', session: data });
      })
      .catch((err: unknown) => {
        if (!cancelled)
          dispatch({
            type: 'SESSION_ERROR',
            error: err instanceof Error ? err.message : 'Failed to load session',
          });
      });
    return () => {
      cancelled = true;
    };
  }, [sessionId]);

  useEffect(() => {
    if (!sessionId) return;

    function connect() {
      const params = new URLSearchParams();
      if (reconnectStateRef.current.runId) {
        params.set('runId', reconnectStateRef.current.runId);
      }
      if (reconnectStateRef.current.afterSequence != null) {
        params.set('afterSequence', String(reconnectStateRef.current.afterSequence));
      }
      const query = params.toString();
      const es = new EventSource(`/api/sessions/${sessionId}/events${query ? `?${query}` : ''}`);
      esRef.current = es;

      const handleEvent = (eventType: string, data: string) => {
        let parsed: Record<string, unknown>;
        try {
          parsed = JSON.parse(data) as Record<string, unknown>;
        } catch {
          return;
        }

        switch (eventType) {
          case 'text-chunk': {
            const text = typeof parsed['chunk'] === 'string' ? parsed['chunk'] : '';
            if (text) {
              dispatch({
                type: 'TEXT_CHUNK',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                text,
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'thinking-delta': {
            const thinking = typeof parsed['thinking'] === 'string' ? parsed['thinking'] : '';
            if (thinking) {
              dispatch({
                type: 'THINKING_DELTA',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                thinking,
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'status': {
            const message = typeof parsed['status'] === 'string' ? parsed['status'] : '';
            if (message.startsWith('Retrying LLM request: ')) {
              dispatch({
                type: 'STATUS',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                message,
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'tool-input-delta': {
            const toolName = typeof parsed['toolName'] === 'string' ? parsed['toolName'] : '';
            const partialJson = typeof parsed['partialJson'] === 'string' ? parsed['partialJson'] : '';
            if (toolName && partialJson) {
              dispatch({
                type: 'TOOL_INPUT_DELTA',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                toolName,
                toolUseId: typeof parsed['toolUseId'] === 'string' ? parsed['toolUseId'] : undefined,
                partialJson,
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'tool-call': {
            const toolName = typeof parsed['toolName'] === 'string' ? parsed['toolName'] : '';
            const argumentsJson = typeof parsed['argumentsJson'] === 'string' ? parsed['argumentsJson'] : '';
            if (toolName) {
              dispatch({
                type: 'TOOL_CALL',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                toolName,
                toolUseId: typeof parsed['toolUseId'] === 'string' ? parsed['toolUseId'] : undefined,
                input: parseToolInput(argumentsJson),
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'tool-result': {
            const toolName = typeof parsed['toolName'] === 'string' ? parsed['toolName'] : '';
            const output = typeof parsed['result'] === 'string' ? parsed['result'] : '';
            if (toolName) {
              dispatch({
                type: 'TOOL_RESULT',
                runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
                toolName,
                toolUseId: typeof parsed['toolUseId'] === 'string' ? parsed['toolUseId'] : undefined,
                output,
                sequence: parseEventSequence(parsed['sequence']),
              });
            }
            break;
          }
          case 'completed': {
            const finalText = typeof parsed['finalText'] === 'string' ? parsed['finalText'] : undefined;
            const runId = typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined;
            const rawSummary = parsed['fileSummary'] as Record<string, unknown> | undefined;
            let fileSummary: FileChangeSummary | undefined;
            if (rawSummary && typeof rawSummary === 'object') {
              const totalChanges = typeof rawSummary['totalChanges'] === 'number' ? rawSummary['totalChanges'] : 0;
              const created = Array.isArray(rawSummary['created']) ? rawSummary['created'] as string[] : [];
              const modified = Array.isArray(rawSummary['modified']) ? rawSummary['modified'] as string[] : [];
              const deleted = Array.isArray(rawSummary['deleted']) ? rawSummary['deleted'] as string[] : [];
              if (totalChanges > 0) {
                fileSummary = { totalChanges, created, modified, deleted };
              }
            }
            dispatch({ type: 'RUN_COMPLETED', runId, finalText, fileSummary, sequence: parseEventSequence(parsed['sequence']) });
            void refreshSession().catch(() => {
              // The optimistic completed state is already visible; refresh only enriches it with persisted metadata.
            });
            break;
          }
          case 'failed': {
            const error = typeof parsed['error'] === 'string' ? parsed['error'] : 'Run failed';
            const runId = typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined;
            dispatch({ type: 'RUN_FAILED', runId, error, sequence: parseEventSequence(parsed['sequence']) });
            break;
          }
          case 'cancelled':
            dispatch({
              type: 'RUN_CANCELLED',
              runId: typeof parsed['runId'] === 'string' ? parsed['runId'] : undefined,
              sequence: parseEventSequence(parsed['sequence']),
            });
            break;
          case 'usage': {
            const usage: UsageMetrics = {
              inputTokens: typeof parsed['inputTokens'] === 'number' ? parsed['inputTokens'] : 0,
              outputTokens: typeof parsed['outputTokens'] === 'number' ? parsed['outputTokens'] : 0,
              cacheCreationTokens: typeof parsed['cacheCreationTokens'] === 'number' ? parsed['cacheCreationTokens'] : 0,
              cacheReadTokens: typeof parsed['cacheReadTokens'] === 'number' ? parsed['cacheReadTokens'] : 0,
              toolUses: typeof parsed['toolUses'] === 'number' ? parsed['toolUses'] : 0,
              contextLength: typeof parsed['contextLength'] === 'number' ? parsed['contextLength'] : 0,
            };
            dispatch({ type: 'USAGE_UPDATE', usage });
            break;
          }
          case 'ask-user-question': {
            dispatch({
              type: 'PENDING_QUESTION_UPDATED',
              pendingQuestion: mapPendingQuestion({
                toolUseId: typeof parsed['toolUseId'] === 'string' ? parsed['toolUseId'] : '',
                question: typeof parsed['question'] === 'string' ? parsed['question'] : '',
                choices: Array.isArray(parsed['choices']) ? parsed['choices'] as string[] : [],
              }),
            });
            break;
          }
          case 'server-shutdown':
            es.close();
            break;
          default:
            break;
        }
      };

      const eventTypes = [
        'ready',
        'text-chunk',
        'thinking-delta',
        'tool-input-delta',
        'status',
        'tool-call',
        'tool-result',
        'usage',
        'ask-user-question',
        'completed',
        'failed',
        'cancelled',
        'server-shutdown',
      ] as const;

      for (const et of eventTypes) {
        es.addEventListener(et, (e: MessageEvent) => handleEvent(et, e.data as string));
      }

      es.onerror = () => {
        es.close();
        esRef.current = null;
        reconnectTimerRef.current = setTimeout(() => {
          if (esRef.current == null) {
            connect();
          }
        }, 2000);
      };
    }

    connect();

    return () => {
      if (reconnectTimerRef.current != null) {
        clearTimeout(reconnectTimerRef.current);
        reconnectTimerRef.current = null;
      }
      if (esRef.current) {
        esRef.current.close();
        esRef.current = null;
      }
    };
  }, [refreshSession, sessionId]);

  const submitMessage = useCallback(
    async (request: ComposerSubmitRequest) => {
      const message = request.message.trim();
      if (!sessionId || !message) return;
      const optimisticPrompt = request.visiblePrompt?.trim() || message;
      dispatch({ type: 'USER_MESSAGE', content: optimisticPrompt });
      const run = await api.sessions.submitRun(sessionId, { ...request, message });
      const resolvedVisiblePrompt = run.visiblePrompt?.trim();
      if (resolvedVisiblePrompt && resolvedVisiblePrompt !== optimisticPrompt) {
        dispatch({ type: 'USER_MESSAGE_UPDATED', content: resolvedVisiblePrompt });
      }
      dispatch({ type: 'RUN_STARTED', runId: run.runId });
    },
    [sessionId],
  );

  const cancelRun = useCallback(async () => {
    if (!sessionId || !state.activeRun) return;
    const runId = state.activeRun.runId;
    const res = await fetch(`/api/sessions/${sessionId}/runs/${runId}`, {
      method: 'DELETE',
    });
    if (res.ok) {
      dispatch({ type: 'RUN_CANCELLED' });
    }
  }, [sessionId, state.activeRun]);

  const answerPendingQuestion = useCallback(async (toolUseId: string, answer: string) => {
    if (!sessionId || !state.activeRun) {
      return;
    }
    await api.sessions.answerPendingQuestion(sessionId, state.activeRun.runId, {
      toolUseId,
      answer,
    });
    dispatch({ type: 'PENDING_QUESTION_UPDATED', pendingQuestion: null });
  }, [sessionId, state.activeRun]);

  const appendMessage = useCallback((role: 'user' | 'system', content: string) => {
    if (!content) {
      return;
    }
    dispatch({ type: role === 'user' ? 'USER_MESSAGE' : 'SYSTEM_MESSAGE', content });
  }, []);

  const rollbackToMessage = useCallback(async (rollbackIndex: number) => {
    if (!sessionId) {
      return;
    }
    await api.sessions.rollbackToMessage(sessionId, rollbackIndex);
    const refreshed = await api.sessions.get(sessionId);
    dispatch({ type: 'ROLLBACK_COMPLETED', session: refreshed });
  }, [sessionId]);

  return { ...state, submitMessage, answerPendingQuestion, cancelRun, appendMessage, rollbackToMessage, refreshSession };
}
