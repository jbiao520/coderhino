import { render, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Terminal as MockTerminal } from 'xterm';
import TerminalPanel from './TerminalPanel';
import type { TerminalDto } from '../types/api';

const mockTerminalClass = MockTerminal as unknown as {
  reset: () => void;
  instances: Array<{
    dispose: ReturnType<typeof vi.fn>;
  }>;
};

class MockWebSocket {
  static instances: MockWebSocket[] = [];
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSED = 3;

  url: string;
  readyState = MockWebSocket.CONNECTING;
  close = vi.fn(() => {
    this.readyState = MockWebSocket.CLOSED;
  });
  send = vi.fn();
  private listeners: Record<string, Array<(event: Event | MessageEvent) => void>> = {};

  constructor(url: string) {
    this.url = url;
    MockWebSocket.instances.push(this);
  }

  addEventListener(type: string, listener: (event: Event | MessageEvent) => void) {
    this.listeners[type] ??= [];
    this.listeners[type]!.push(listener);
  }

  emit(type: string, event: Event | MessageEvent) {
    for (const listener of this.listeners[type] ?? []) {
      listener(event);
    }
  }

  emitClose(reason = '') {
    this.readyState = MockWebSocket.CLOSED;
    this.emit('close', { reason } as CloseEvent);
  }

  static reset() {
    MockWebSocket.instances = [];
  }
}

class MockResizeObserver {
  static instances: MockResizeObserver[] = [];
  readonly callback: ResizeObserverCallback;
  disconnect = vi.fn();

  constructor(callback: ResizeObserverCallback) {
    this.callback = callback;
    MockResizeObserver.instances.push(this);
  }

  observe() {}

  static reset() {
    MockResizeObserver.instances = [];
  }
}

describe('TerminalPanel', () => {
  let rafQueue: FrameRequestCallback[];

  const runningTerminal: TerminalDto = {
    terminalId: 'term-running',
    label: 'Terminal 1',
    status: 'RUNNING',
    cwd: '/tmp/project',
    worktreeId: 'default',
    createdAt: '2026-04-11T00:00:00Z',
    exitCode: null,
    message: null,
  };

  beforeEach(() => {
    rafQueue = [];
    mockTerminalClass.reset();
    MockWebSocket.reset();
    MockResizeObserver.reset();

    (globalThis as Record<string, unknown>).WebSocket = MockWebSocket;
    (globalThis as Record<string, unknown>).ResizeObserver = MockResizeObserver;
    (globalThis as Record<string, unknown>).requestAnimationFrame = (cb: FrameRequestCallback) => {
      rafQueue.push(cb);
      return rafQueue.length;
    };
    (globalThis as Record<string, unknown>).cancelAnimationFrame = vi.fn((id: number) => {
      if (id > 0 && id <= rafQueue.length) {
        rafQueue[id - 1] = () => undefined;
      }
    });
  });

  function flushAnimationFrames() {
    const pending = [...rafQueue];
    rafQueue = [];
    for (const callback of pending) {
      callback(0);
    }
  }

  it('closes connecting sockets safely after unmount when open arrives later', () => {
    const { unmount } = render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={() => {}}
      />,
    );

    flushAnimationFrames();

    const socket = MockWebSocket.instances[0];
    expect(socket).toBeTruthy();
    expect(socket?.readyState).toBe(MockWebSocket.CONNECTING);

    unmount();

    expect(socket?.close).not.toHaveBeenCalled();

    socket!.readyState = MockWebSocket.OPEN;
    expect(() => {
      socket?.emit('open', new Event('open'));
    }).not.toThrow();
    expect(socket?.close).toHaveBeenCalledTimes(1);
  });

  it('does not run terminal callbacks after disposal during remount-like churn', () => {
    const { unmount } = render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={() => {}}
      />,
    );

    flushAnimationFrames();
    expect(mockTerminalClass.instances).toHaveLength(1);

    unmount();

    expect(() => {
      flushAnimationFrames();
    }).not.toThrow();

    const observer = MockResizeObserver.instances[0];
    expect(observer).toBeTruthy();
    expect(() => {
      observer?.callback([], observer as unknown as ResizeObserver);
    }).not.toThrow();

    expect(mockTerminalClass.instances[0]?.dispose).toHaveBeenCalledTimes(1);
  });

  it('uses the backend websocket origin when building the terminal socket URL', () => {
    render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={() => {}}
      />,
    );

    flushAnimationFrames();

    expect(MockWebSocket.instances[0]?.url).toBe('ws://localhost:8080/ws/terminals/term-running?sessionId=ses-1');
  });

  it('waits for a ready event before treating the terminal as attached', async () => {
    const onTerminalUpdate = vi.fn();

    render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={onTerminalUpdate}
      />,
    );

    flushAnimationFrames();

    const socket = MockWebSocket.instances[0]!;
    socket.readyState = MockWebSocket.OPEN;
    socket.emit('open', new Event('open'));
    socket.emit('message', new MessageEvent('message', {
      data: JSON.stringify({ type: 'ready', terminalId: 'term-running' }),
    }));
    socket.emit('error', new Event('error'));

    await waitFor(() => expect(onTerminalUpdate).not.toHaveBeenCalled());
  });

  it('surfaces the WebSocket close reason when attach fails before ready', async () => {
    const onTerminalUpdate = vi.fn();

    render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={onTerminalUpdate}
      />,
    );

    flushAnimationFrames();

    const socket = MockWebSocket.instances[0]!;
    socket.emitClose('Unknown terminal');

    await waitFor(() => expect(onTerminalUpdate).toHaveBeenCalledWith(expect.objectContaining({
      terminalId: 'term-running',
      status: 'ERROR',
      message: 'Unknown terminal',
    })));
  });

  it('preserves backend-provided runtime error messages after attach', async () => {
    const onTerminalUpdate = vi.fn();

    render(
      <TerminalPanel
        sessionId="ses-1"
        terminals={[runningTerminal]}
        selectedTerminalId="term-running"
        onSelectTerminal={() => {}}
        onCreateTerminal={() => {}}
        onCloseTerminal={() => {}}
        onTerminalUpdate={onTerminalUpdate}
      />,
    );

    flushAnimationFrames();

    const socket = MockWebSocket.instances[0]!;
    socket.emit('message', new MessageEvent('message', {
      data: JSON.stringify({ type: 'ready', terminalId: 'term-running' }),
    }));
    socket.emit('message', new MessageEvent('message', {
      data: JSON.stringify({ type: 'error', message: 'PTY startup failed' }),
    }));

    await waitFor(() => expect(onTerminalUpdate).toHaveBeenCalledWith(expect.objectContaining({
      terminalId: 'term-running',
      status: 'ERROR',
      message: 'PTY startup failed',
    })));
  });
});
