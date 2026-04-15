import { renderHook, act, waitFor } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { useStreamingSession, reducer } from '../hooks/useStreamingSession';

const mockSession = {
  sessionId: 'ses-stream',
  createdAt: '2026-04-07T10:00:00Z',
  updatedAt: '2026-04-07T10:00:00Z',
  status: 'ACTIVE',
  activeRun: null,
  messages: [],
  model: 'MiniMax-M2.7',
  providerId: 'provider-1',
  availableProviders: [
    { id: 'provider-1', name: 'Anthropic', models: ['MiniMax-M2.7'], unavailable: false },
  ],
  planMode: false,
  buildMode: true,
  availableModels: ['MiniMax-M2.7'],
  modelModeSupported: true,
  availableModelModes: ['default', 'think'],
  modelMode: 'default',
};

const mockSessionWithMessages = {
  ...mockSession,
  messages: [
    { type: 'user', content: 'Previous question', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0 },
    { type: 'assistant', content: 'Previous answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
  ],
};

type EventHandler = (event: MessageEvent) => void;

class MockEventSource {
  static instances: MockEventSource[] = [];
  url: string;
  onerror: (() => void) | null = null;
  private listeners: Record<string, EventHandler[]> = {};

  constructor(url: string) {
    this.url = url;
    MockEventSource.instances.push(this);
  }

  addEventListener(type: string, handler: EventHandler) {
    if (!this.listeners[type]) this.listeners[type] = [];
    this.listeners[type].push(handler);
  }

  emit(type: string, data: string) {
    const handlers = this.listeners[type] ?? [];
    for (const h of handlers) {
      h(new MessageEvent(type, { data }));
    }
  }

  close() {}

  static reset() {
    MockEventSource.instances = [];
  }
}

describe('useStreamingSession', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    MockEventSource.reset();
    (globalThis as Record<string, unknown>)['EventSource'] = MockEventSource;
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('loads session on mount', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.session?.sessionId).toBe('ses-stream');
    expect(result.current.error).toBeNull();
  });

  it('preserves persisted message metadata from the session payload', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSessionWithMessages,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.messages).toEqual([
      { role: 'user', content: 'Previous question', timestamp: '2026-04-07T10:01:00Z', rollbackIndex: 0, activityTimeline: null, fileSummary: null },
      { role: 'assistant', content: 'Previous answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null, activityTimeline: null, fileSummary: null },
    ]);
  });

  it('hydrates active run transcript from session replay state after refresh', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        ...mockSession,
        activeRun: { runId: 'run-replay', status: 'RUNNING' },
        activeRunState: {
          runId: 'run-replay',
          transcript: [
            { kind: 'assistant', content: 'Thinking' },
            { kind: 'tool', toolName: 'glob', toolUseId: 'tool-1', argumentsJson: '{"pattern":"*.ts"}', output: 'src/index.ts' },
          ],
          usage: {
            inputTokens: 10,
            outputTokens: 5,
            cacheCreationTokens: 0,
            cacheReadTokens: 0,
            toolUses: 1,
            contextLength: 200,
          },
          lastSequence: 4,
        },
      }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.runTranscript).toEqual([
      { kind: 'assistant', content: 'Thinking' },
      { kind: 'tool', toolName: 'glob', toolUseId: 'tool-1', input: { pattern: '*.ts' }, output: 'src/index.ts' },
    ]);
    expect(result.current.usage).toEqual({
      inputTokens: 10,
      outputTokens: 5,
      cacheCreationTokens: 0,
      cacheReadTokens: 0,
      toolUses: 1,
      contextLength: 200,
    });
  });

  it('hydrates thinking and tool-input replay items from active run state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        ...mockSession,
        activeRun: { runId: 'run-rich', status: 'RUNNING' },
        activeRunState: {
          runId: 'run-rich',
          transcript: [
            { kind: 'thinking', content: 'Plan carefully' },
            { kind: 'tool-input', toolName: 'glob', toolUseId: 'tool-2', argumentsJson: '{"pattern":"*.java"}' },
          ],
          lastSequence: 2,
        },
      }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.runTranscript).toEqual([
      { kind: 'thinking', content: 'Plan carefully' },
      { kind: 'tool-input', toolName: 'glob', toolUseId: 'tool-2', partialJson: '{"pattern":"*.java"}' },
    ]);
  });

  it('hydrates retry status transcript items from active run state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        ...mockSession,
        activeRun: { runId: 'run-retry', status: 'RUNNING' },
        activeRunState: {
          runId: 'run-retry',
          transcript: [
            { kind: 'status', content: 'Retrying LLM request: attempt 3 of 5 after service overloaded' },
            { kind: 'thinking', content: 'Trying alternate path' },
          ],
          lastSequence: 2,
        },
      }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.runTranscript).toEqual([
      { kind: 'status', content: 'Retrying LLM request: attempt 3 of 5 after service overloaded' },
      { kind: 'thinking', content: 'Trying alternate path' },
    ]);
  });

  it('hydrates persisted completed-turn activity onto assistant messages', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        ...mockSession,
        messages: [
          {
            type: 'assistant',
            content: 'Completed answer',
            timestamp: '2026-04-07T10:01:05Z',
            rollbackIndex: null,
            activityTimeline: [
              { kind: 'thinking', content: 'Plan carefully' },
              { kind: 'tool', toolName: 'glob', toolUseId: 'tool-1', argumentsJson: '{"pattern":"*.java"}', output: 'src/Main.java' },
            ],
            fileSummary: {
              totalChanges: 1,
              created: [],
              modified: ['src/Main.java'],
              deleted: [],
            },
          },
        ],
      }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.messages[0]).toEqual({
      role: 'assistant',
      content: 'Completed answer',
      timestamp: '2026-04-07T10:01:05Z',
      rollbackIndex: null,
      activityTimeline: [
        { kind: 'thinking', content: 'Plan carefully' },
        { kind: 'tool', toolName: 'glob', toolUseId: 'tool-1', input: { pattern: '*.java' }, output: 'src/Main.java' },
      ],
      fileSummary: {
        totalChanges: 1,
        created: [],
        modified: ['src/Main.java'],
        deleted: [],
      },
    });
  });

  it('hydrates pending question from active run state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        ...mockSession,
        activeRun: { runId: 'run-question', status: 'WAITING_FOR_USER' },
        activeRunState: {
          runId: 'run-question',
          transcript: [],
          pendingQuestion: {
            toolUseId: 'tool-q-1',
            question: 'Which guidance files?',
            choices: ['Project CLAUDE.md', 'Personal CLAUDE.local.md'],
          },
        },
      }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    expect(result.current.pendingQuestion).toEqual({
      toolUseId: 'tool-q-1',
      question: 'Which guidance files?',
      choices: ['Project CLAUDE.md', 'Personal CLAUDE.local.md'],
    });
  });

  it('sets error on failed session fetch', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 404,
      json: async () => ({}),
    });

    const { result } = renderHook(() => useStreamingSession('ses-missing'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    expect(result.current.error).toBeTruthy();
  });

  it('accumulates text-chunk events into a single assistant transcript item', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Hello' }));
    });
    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: ' world' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'assistant', content: 'Hello world' },
    ]);
    expect(result.current.chunkCount).toBe(2);
  });

  it('finalizes transcript text into messages on completed event', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Done!' }));
    });
    act(() => {
      es.emit('completed', JSON.stringify({ finalText: 'Done!' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'assistant', content: 'Done!' },
    ]);
    expect(result.current.messages).toHaveLength(1);
    expect(result.current.messages[0]!.content).toBe('Done!');
    expect(result.current.messages[0]!.role).toBe('assistant');
  });

  it('tracks tool-call and tool-result events', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('tool-call', JSON.stringify({ toolName: 'glob', argumentsJson: '{"pattern":"*.ts"}' }));
    });
    expect(result.current.runTranscript).toHaveLength(1);
    expect(result.current.runTranscript[0]).toEqual({
      kind: 'tool',
      toolName: 'glob',
      input: { pattern: '*.ts' },
    });

    act(() => {
      es.emit('tool-result', JSON.stringify({ toolName: 'glob', result: 'src/index.ts' }));
    });
    expect(result.current.runTranscript[0]).toEqual({
      kind: 'tool',
      toolName: 'glob',
      input: { pattern: '*.ts' },
      output: 'src/index.ts',
    });
  });

  it('tracks repeated tool-call events with the same toolName separately', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('tool-call', JSON.stringify({ toolName: 'bash', argumentsJson: '{"command":"ls"}' }));
      es.emit('tool-call', JSON.stringify({ toolName: 'bash', argumentsJson: '{"command":"ls"}' }));
    });

    expect(result.current.runTranscript).toHaveLength(2);
  });

  it('accumulates thinking and tool-input SSE events into dedicated transcript items', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('thinking-delta', JSON.stringify({ runId: 'run-rich', thinking: 'Plan' }));
      es.emit('thinking-delta', JSON.stringify({ runId: 'run-rich', thinking: ' more' }));
      es.emit('tool-input-delta', JSON.stringify({ runId: 'run-rich', toolName: 'glob', toolUseId: 'tool-2', partialJson: '{"pattern":' }));
      es.emit('tool-input-delta', JSON.stringify({ runId: 'run-rich', toolName: 'glob', toolUseId: 'tool-2', partialJson: '"*.java"}' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'thinking', content: 'Plan more' },
      { kind: 'tool-input', toolName: 'glob', toolUseId: 'tool-2', partialJson: '{"pattern":"*.java"}' },
    ]);
  });

  it('tracks retry status events as transcript items', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('status', JSON.stringify({ runId: 'run-retry', status: 'Retrying LLM request: attempt 2 of 5 after service overloaded' }));
      es.emit('text-chunk', JSON.stringify({ runId: 'run-retry', chunk: 'Recovered' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'status', content: 'Retrying LLM request: attempt 2 of 5 after service overloaded' },
      { kind: 'assistant', content: 'Recovered' },
    ]);
  });

  it('clears the inline transcript when a new run starts', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSession })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ runId: 'run-new', status: 'RUNNING', visiblePrompt: 'new message' }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'partial' }));
      es.emit('tool-call', JSON.stringify({ toolName: 'grep', argumentsJson: '{}' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'assistant', content: 'partial' },
      { kind: 'tool', toolName: 'grep', input: {} },
    ]);

    await act(async () => {
      await result.current.submitMessage({ message: 'new message' });
    });

    expect(result.current.runTranscript).toHaveLength(0);
  });

  it('adds user message immediately on submit', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSession })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ runId: 'run-1', status: 'RUNNING', visiblePrompt: 'Hello Agent' }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.submitMessage({ message: 'Hello Agent' });
    });

    expect(result.current.messages[0]).toEqual({ role: 'user', content: 'Hello Agent' });
  });

  it('sets activeRun to null after cancel', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSession })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ runId: 'run-cancel', status: 'RUNNING', visiblePrompt: 'do something' }),
      })
      .mockResolvedValueOnce({ ok: true, json: async () => ({}) });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.submitMessage({ message: 'do something' });
    });

    expect(result.current.activeRun?.runId).toBe('run-cancel');

    await act(async () => {
      await result.current.cancelRun();
    });

    expect(result.current.activeRun).toBeNull();
  });

  it('rolls back to a persisted message and refreshes session state', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSessionWithMessages })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ prompt: '/rewind jump 0', output: 'Rewound', success: true, commandName: 'rewind' }) })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          ...mockSession,
          messages: [],
        }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.rollbackToMessage(0);
    });

    expect(result.current.messages).toEqual([]);
    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[1]?.[0]).toBe('/api/commands/execute');
    expect((globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[2]?.[0]).toBe('/api/sessions/ses-stream');
  });

  it('submits composer payload fields', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSession })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({ runId: 'run-fields', status: 'RUNNING', visiblePrompt: 'with config' }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.submitMessage({
        message: 'with config',
        model: 'MiniMax-M2.5',
        providerId: 'provider-1',
        buildMode: false,
        planMode: true,
        modelMode: 'think',
      });
    });

    const [, request] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[1] as [string, RequestInit];
    expect(request.body).toBe(JSON.stringify({
      message: 'with config',
      model: 'MiniMax-M2.5',
      providerId: 'provider-1',
      buildMode: false,
      planMode: true,
      modelMode: 'think',
    }));
  });

  it('replaces the optimistic user message when the server returns a resolved visible prompt', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockSession })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          runId: 'run-visible',
          status: 'RUNNING',
          visiblePrompt: 'this is a propose command, user want you to query weather for : shanghai',
        }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    await act(async () => {
      await result.current.submitMessage({
        message: '/query-weather shanghai',
        visiblePrompt: '/query-weather shanghai',
      });
    });

    expect(result.current.messages[0]).toEqual({
      role: 'user',
      content: 'this is a propose command, user want you to query weather for : shanghai',
    });
  });

  it('uses completed finalText when present', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({
        ok: true,
        json: async () => mockSession,
      })
      .mockResolvedValueOnce({
        ok: true,
        json: async () => ({
          ...mockSession,
          messages: [
            { type: 'assistant', content: 'Persisted final answer', timestamp: '2026-04-07T10:01:05Z', rollbackIndex: null },
          ],
        }),
      });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('completed', JSON.stringify({ finalText: 'Persisted final answer' }));
    });

    await waitFor(() => expect(result.current.messages).toHaveLength(1));
    expect(result.current.messages[0]).toEqual({
      role: 'assistant',
      content: 'Persisted final answer',
      timestamp: '2026-04-07T10:01:05Z',
      rollbackIndex: null,
      activityTimeline: null,
      fileSummary: null,
    });
  });

  it('handles failed events as explicit run errors', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'partial output' }));
      es.emit('failed', JSON.stringify({ error: 'Model crashed' }));
    });

    expect(result.current.runTranscript).toHaveLength(0);
    expect(result.current.runStatus).toBe('error');
    expect(result.current.error).toBe('Model crashed');
  });

  it('renders mixed text and tool events in chronological order', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('text-chunk', JSON.stringify({ chunk: 'Checking files...' }));
      es.emit('tool-call', JSON.stringify({ toolName: 'glob', argumentsJson: '{"pattern":"*.ts"}' }));
      es.emit('tool-result', JSON.stringify({ toolName: 'glob', result: 'src/index.ts' }));
      es.emit('text-chunk', JSON.stringify({ chunk: 'Done.' }));
    });

    expect(result.current.runTranscript).toEqual([
      { kind: 'assistant', content: 'Checking files...' },
      { kind: 'tool', toolName: 'glob', input: { pattern: '*.ts' }, output: 'src/index.ts' },
      { kind: 'assistant', content: 'Done.' },
    ]);
  });

  it('handles cancelled events from SSE', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ ...mockSession, activeRun: { runId: 'run-1', status: 'RUNNING' } }),
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('cancelled', JSON.stringify({ status: 'CANCELLED' }));
    });

    expect(result.current.activeRun).toBeNull();
    expect(result.current.runStatus).toBe('idle');
  });

  it('preserves zero-valued usage metrics from SSE updates', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockSession,
    });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));

    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('usage', JSON.stringify({
        inputTokens: 0,
        outputTokens: 0,
        cacheCreationTokens: 0,
        cacheReadTokens: 0,
        toolUses: 0,
        contextLength: 0,
      }));
    });

    expect(result.current.usage).toEqual({
      inputTokens: 0,
      outputTokens: 0,
      cacheCreationTokens: 0,
      cacheReadTokens: 0,
      toolUses: 0,
      contextLength: 0,
    });
  });

  it('updates pending question from SSE and clears it after answer submission', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => ({ ...mockSession, activeRun: { runId: 'run-q', status: 'RUNNING' } }) })
      .mockResolvedValueOnce({ ok: true, json: async () => ({ runId: 'run-q', status: 'RUNNING' }) });

    const { result } = renderHook(() => useStreamingSession('ses-stream'));
    await waitFor(() => expect(result.current.loading).toBe(false));
    const es = MockEventSource.instances[MockEventSource.instances.length - 1]!;

    act(() => {
      es.emit('ask-user-question', JSON.stringify({
        runId: 'run-q',
        toolUseId: 'tool-q-2',
        question: 'Pick one',
        choices: ['A', 'B'],
      }));
    });

    expect(result.current.pendingQuestion?.toolUseId).toBe('tool-q-2');

    await act(async () => {
      await result.current.answerPendingQuestion('tool-q-2', 'B');
    });

    expect(result.current.pendingQuestion).toBeNull();
  });

  it('clears stale usage when a refreshed session has no replay usage snapshot', () => {
    const stateWithUsage = reducer({
      session: {
        ...mockSession,
        activeRun: { runId: 'run-a', status: 'RUNNING' },
      },
      messages: [],
      runTranscript: [],
      activeRun: { runId: 'run-a', status: 'RUNNING' },
      runStatus: 'running',
      error: null,
      loading: false,
      chunkCount: 0,
      completedRunIds: new Set<string>(),
      lastEventSequenceByRun: {},
      fileSummary: null,
      usage: {
        inputTokens: 123,
        outputTokens: 45,
        cacheCreationTokens: 6,
        cacheReadTokens: 7,
        toolUses: 2,
        contextLength: 181,
      },
      pendingQuestion: null,
    }, {
      type: 'SESSION_REFRESHED',
      session: {
        ...mockSession,
        sessionId: 'ses-other',
        activeRun: null,
        activeRunState: null,
      },
    });

    expect(stateWithUsage.usage).toBeNull();
  });

  it('ignores replayed duplicate events when a run already has an applied sequence', () => {
    const base = reducer({
      session: {
        ...mockSession,
        activeRun: { runId: 'run-replay', status: 'RUNNING' },
        activeRunState: {
          runId: 'run-replay',
          transcript: [{ kind: 'assistant', content: 'Thinking' }],
          lastSequence: 2,
        },
      },
      messages: [],
      runTranscript: [{ kind: 'assistant', content: 'Thinking' }],
      activeRun: { runId: 'run-replay', status: 'RUNNING' },
      runStatus: 'running',
      error: null,
      loading: false,
      chunkCount: 0,
      completedRunIds: new Set<string>(),
      lastEventSequenceByRun: { 'run-replay': 2 },
      fileSummary: null,
      usage: null,
      pendingQuestion: null,
    }, {
      type: 'TEXT_CHUNK',
      runId: 'run-replay',
      text: ' duplicate',
      sequence: 2,
    });

    const afterFreshChunk = reducer(base, {
      type: 'TEXT_CHUNK',
      runId: 'run-replay',
      text: ' more',
      sequence: 3,
    });

    expect(afterFreshChunk.runTranscript).toEqual([
      { kind: 'assistant', content: 'Thinking more' },
    ]);
    expect(afterFreshChunk.lastEventSequenceByRun['run-replay']).toBe(3);
  });
});

describe('reducer: out-of-order event hardening', () => {
  const initialState: import('../hooks/useStreamingSession').StreamingState = {
    session: null,
    messages: [],
    runTranscript: [],
    activeRun: null,
    runStatus: 'idle',
    error: null,
    loading: false,
    chunkCount: 0,
    completedRunIds: new Set<string>(),
    lastEventSequenceByRun: {},
    fileSummary: null,
    usage: null,
    pendingQuestion: null,
  };

  it('ignores RUN_STARTED after RUN_COMPLETED for the same runId', () => {
    const runId = 'run-stale-1';
    const afterStarted = reducer(initialState, { type: 'RUN_STARTED', runId });
    expect(afterStarted.runStatus).toBe('running');
    expect(afterStarted.activeRun?.runId).toBe(runId);

    const afterCompleted = reducer(afterStarted, {
      type: 'RUN_COMPLETED',
      runId,
      finalText: 'done',
    });
    expect(afterCompleted.runStatus).toBe('completed');
    expect(afterCompleted.activeRun).toBeNull();
    expect(afterCompleted.completedRunIds.has(runId)).toBe(true);

    const afterStaleStarted = reducer(afterCompleted, { type: 'RUN_STARTED', runId });
    expect(afterStaleStarted.runStatus).toBe('completed');
    expect(afterStaleStarted.activeRun).toBeNull();
  });

  it('ignores RUN_STARTED after RUN_FAILED for the same runId', () => {
    const runId = 'run-fail-1';
    const afterStarted = reducer(initialState, { type: 'RUN_STARTED', runId });
    const afterFailed = reducer(afterStarted, {
      type: 'RUN_FAILED',
      runId,
      error: 'boom',
    });
    expect(afterFailed.runStatus).toBe('error');
    expect(afterFailed.completedRunIds.has(runId)).toBe(true);

    const afterStaleStarted = reducer(afterFailed, { type: 'RUN_STARTED', runId });
    expect(afterStaleStarted.runStatus).toBe('error');
    expect(afterStaleStarted.activeRun).toBeNull();
  });

  it('clears completedRunIds on USER_MESSAGE', () => {
    const runId = 'run-clear-1';
    const afterStarted = reducer(initialState, { type: 'RUN_STARTED', runId });
    const afterCompleted = reducer(afterStarted, {
      type: 'RUN_COMPLETED',
      runId,
      finalText: 'done',
    });
    expect(afterCompleted.completedRunIds.has(runId)).toBe(true);

    const afterMessage = reducer(afterCompleted, {
      type: 'USER_MESSAGE',
      content: 'next prompt',
    });
    expect(afterMessage.completedRunIds.size).toBe(0);
  });

  it('allows RUN_STARTED after USER_MESSAGE clears completedRunIds', () => {
    const runId = 'run-new-1';
    const afterStarted = reducer(initialState, { type: 'RUN_STARTED', runId });
    const afterCompleted = reducer(afterStarted, {
      type: 'RUN_COMPLETED',
      runId,
      finalText: 'done',
    });
    const afterMessage = reducer(afterCompleted, {
      type: 'USER_MESSAGE',
      content: 'next',
    });
    const afterNewStarted = reducer(afterMessage, { type: 'RUN_STARTED', runId });
    expect(afterNewStarted.runStatus).toBe('running');
    expect(afterNewStarted.activeRun?.runId).toBe(runId);
  });

  it('matches tool results to the earliest unfinished matching tool entry', () => {
    const afterCalls = reducer(initialState, { type: 'TOOL_CALL', toolName: 'bash', input: { command: 'ls' } });
    const afterSecondCall = reducer(afterCalls, { type: 'TOOL_CALL', toolName: 'bash', input: { command: 'pwd' } });
    const afterFirstResult = reducer(afterSecondCall, { type: 'TOOL_RESULT', toolName: 'bash', output: 'file-a' });
    const afterSecondResult = reducer(afterFirstResult, { type: 'TOOL_RESULT', toolName: 'bash', output: '/tmp' });

    expect(afterSecondResult.runTranscript).toEqual([
      { kind: 'tool', toolName: 'bash', input: { command: 'ls' }, output: 'file-a' },
      { kind: 'tool', toolName: 'bash', input: { command: 'pwd' }, output: '/tmp' },
    ]);
  });
});
