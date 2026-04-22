import { useEffect, useMemo, useRef } from 'react';
import { Terminal } from 'xterm';
import { FitAddon } from 'xterm-addon-fit';
import 'xterm/css/xterm.css';
import type { TerminalDto } from '../types/api';
import { CloseIcon, IconFrame } from './Icons';

interface TerminalSocketPayload {
  type: string;
  data?: string;
  exitCode?: number;
  message?: string;
}

const DEFAULT_ATTACH_ERROR = 'Terminal connection failed';

interface TerminalPanelProps {
  sessionId: string;
  terminals: TerminalDto[];
  selectedTerminalId: string | null;
  onSelectTerminal: (terminalId: string) => void;
  onCreateTerminal: () => void;
  onCloseTerminal: (terminalId: string) => void;
  onTerminalUpdate: (terminal: TerminalDto) => void;
}

function socketUrl(terminalId: string, sessionId: string): string {
  const path = `/ws/terminals/${encodeURIComponent(terminalId)}?sessionId=${encodeURIComponent(sessionId)}`;
  if (typeof window === 'undefined') {
    return path;
  }
  const wsProtocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  const configuredOrigin = import.meta.env.VITE_BACKEND_WS_ORIGIN?.trim()
    || import.meta.env.VITE_BACKEND_ORIGIN?.trim();

  if (configuredOrigin) {
    const origin = new URL(configuredOrigin, window.location.origin);
    origin.protocol = origin.protocol === 'https:' ? 'wss:' : 'ws:';
    return new URL(path, origin).toString();
  }

  const baseOrigin = import.meta.env.DEV
    ? `${wsProtocol}//${window.location.hostname}:8080`
    : `${wsProtocol}//${window.location.host}`;
  return new URL(path, baseOrigin).toString();
}

function statusBadgeClass(status: string): string {
  switch (status) {
    case 'RUNNING':
      return 'status-badge-active';
    case 'EXITED':
      return 'status-badge-idle';
    case 'ERROR':
      return 'status-badge-error';
    default:
      return 'status-badge-pending';
  }
}

function statusLabel(status: string): string {
  switch (status) {
    case 'RUNNING':
      return 'Running';
    case 'EXITED':
      return 'Exited';
    case 'ERROR':
      return 'Error';
    default:
      return status;
  }
}

function terminalSummary(terminal: TerminalDto | null): string | null {
  if (!terminal) {
    return null;
  }
  if (terminal.status === 'EXITED' && typeof terminal.exitCode === 'number') {
    return `Exit code ${terminal.exitCode}`;
  }
  return terminal.message ?? null;
}

export default function TerminalPanel({
  sessionId,
  terminals,
  selectedTerminalId,
  onSelectTerminal,
  onCreateTerminal,
  onCloseTerminal,
  onTerminalUpdate,
}: TerminalPanelProps) {
  const selectedTerminal = useMemo(
    () => terminals.find((terminal) => terminal.terminalId === selectedTerminalId) ?? terminals[0] ?? null,
    [selectedTerminalId, terminals],
  );
  const selectedTerminalDetail = useMemo(() => terminalSummary(selectedTerminal), [selectedTerminal]);
  const selectedTerminalLifecycleKey = selectedTerminal
    ? `${selectedTerminal.terminalId}:${selectedTerminal.status === 'RUNNING' ? 'running' : 'inactive'}`
    : 'none';
  const containerRef = useRef<HTMLDivElement | null>(null);
  const terminalRef = useRef<Terminal | null>(null);
  const fitAddonRef = useRef<FitAddon | null>(null);
  const socketRef = useRef<WebSocket | null>(null);
  const selectedTerminalIdRef = useRef<string | null>(null);

  useEffect(() => {
    selectedTerminalIdRef.current = selectedTerminal?.terminalId ?? null;
  }, [selectedTerminal]);

  useEffect(() => {
    if (!containerRef.current || !selectedTerminal) {
      return;
    }

    const container = containerRef.current;
    let disposed = false;
    let term: Terminal | null = null;
    let fitAddon: FitAddon | null = null;
    let socket: WebSocket | null = null;
    let resizeObserver: ResizeObserver | null = null;
    let fitFrame: number | null = null;
    let closeSocketOnOpen = false;
    let attachReady = selectedTerminal.status !== 'RUNNING';
    let attachFailureMessage: string | null = null;

    const applyAttachFailure = (message?: string | null) => {
      if (disposed || selectedTerminalIdRef.current !== selectedTerminal.terminalId) {
        return;
      }
      const nextMessage = message ?? attachFailureMessage ?? selectedTerminal.message ?? DEFAULT_ATTACH_ERROR;
      attachFailureMessage = nextMessage;
      onTerminalUpdate({
        ...selectedTerminal,
        status: 'ERROR',
        message: nextMessage,
      });
    };

    const safeFit = () => {
      if (disposed || !container.isConnected || !term || !fitAddon || !term.element) {
        return false;
      }
      try {
        fitAddon.fit();
        term.focus();
        return true;
      } catch {
        return false;
      }
    };

    const sendResize = () => {
      if (!term || !socket || socket.readyState !== WebSocket.OPEN) {
        return;
      }
      socket.send(JSON.stringify({ type: 'resize', cols: term.cols, rows: term.rows }));
    };

    const initializeTerminal = () => {
      if (disposed || !container.isConnected) {
        return;
      }

      term = new Terminal({
        convertEol: true,
        cursorBlink: true,
        fontFamily: 'var(--font-mono)',
        fontSize: 12,
        theme: {
          background: '#ffffff',
          foreground: '#1e1e1e',
          cursor: '#333333',
          cursorAccent: '#ffffff',
          selectionBackground: '#b5d5ff',
          selectionForeground: '#1e1e1e',
          black: '#000000',
          red: '#cd3131',
          green: '#00bc00',
          yellow: '#949800',
          blue: '#0451a5',
          magenta: '#bc05bc',
          cyan: '#0598bc',
          white: '#555555',
          brightBlack: '#666666',
          brightRed: '#cd3131',
          brightGreen: '#14ce14',
          brightYellow: '#b5ba00',
          brightBlue: '#0451a5',
          brightMagenta: '#bc05bc',
          brightCyan: '#0598bc',
          brightWhite: '#a5a5a5',
        },
      });
      fitAddon = new FitAddon();
      term.loadAddon(fitAddon);
      term.open(container);
      term.focus();

      terminalRef.current = term;
      fitAddonRef.current = fitAddon;

      // Defer initial fit to ensure the container has non-zero dimensions.
      fitFrame = requestAnimationFrame(() => {
        if (!safeFit()) {
          return;
        }
        sendResize();
      });

      if (selectedTerminal.status !== 'RUNNING') {
        term.writeln(selectedTerminal.message ?? 'Terminal session ended.');
        socketRef.current = null;
      } else {
        socket = new WebSocket(socketUrl(selectedTerminal.terminalId, sessionId));
        socketRef.current = socket;

        socket.addEventListener('open', () => {
          if (disposed || closeSocketOnOpen) {
            if (socket?.readyState === WebSocket.OPEN) {
              socket.close();
            }
            return;
          }
          if (!term) {
            return;
          }
          sendResize();
        });

        socket.addEventListener('message', (event) => {
          if (!term) {
            return;
          }

          let payload: TerminalSocketPayload | null = null;
          try {
            payload = JSON.parse(event.data) as TerminalSocketPayload;
          } catch {
            return;
          }
          if (!payload) {
            return;
          }
          if (payload.type === 'ready') {
            attachReady = true;
            attachFailureMessage = null;
            return;
          }
          if (payload.type === 'output' && payload.data) {
            attachReady = true;
            attachFailureMessage = null;
            term.write(payload.data);
            return;
          }
          if (payload.type === 'exit') {
            attachReady = true;
            term.writeln(`\r\n[Process exited${typeof payload.exitCode === 'number' ? `: ${payload.exitCode}` : ''}]`);
            if (selectedTerminalIdRef.current === selectedTerminal.terminalId) {
              onTerminalUpdate({
                ...selectedTerminal,
                status: 'EXITED',
                exitCode: payload.exitCode ?? null,
                message: selectedTerminal.message ?? null,
              });
            }
            return;
          }
          if (payload.type === 'error') {
            attachReady = true;
            attachFailureMessage = payload.message ?? attachFailureMessage;
            term.writeln(`\r\n[Terminal error] ${payload.message ?? 'Unknown error'}`);
            if (selectedTerminalIdRef.current === selectedTerminal.terminalId) {
              onTerminalUpdate({
                ...selectedTerminal,
                status: 'ERROR',
                message: payload.message ?? attachFailureMessage ?? selectedTerminal.message ?? 'Terminal error',
              });
            }
          }
        });

        socket.addEventListener('error', () => {
          if (disposed || closeSocketOnOpen || attachReady) {
            return;
          }
          applyAttachFailure();
        });

        socket.addEventListener('close', (event) => {
          if (disposed || closeSocketOnOpen || attachReady) {
            return;
          }
          const closeMessage = typeof event.reason === 'string' && event.reason.trim()
            ? event.reason.trim()
            : null;
          applyAttachFailure(closeMessage);
        });
      }

      term.onData((data) => {
        if (socket?.readyState === WebSocket.OPEN) {
          socket.send(JSON.stringify({ type: 'input', data }));
        }
      });

      resizeObserver = new ResizeObserver(() => {
        if (disposed) {
          return;
        }
        if (!safeFit()) {
          return;
        }
        sendResize();
      });
      resizeObserver.observe(container);
    };

    const initFrame = requestAnimationFrame(initializeTerminal);

    return () => {
      disposed = true;
      cancelAnimationFrame(initFrame);
      if (fitFrame !== null) {
        cancelAnimationFrame(fitFrame);
      }
      resizeObserver?.disconnect();
      if (socket) {
        if (socket.readyState === WebSocket.CONNECTING) {
          closeSocketOnOpen = true;
        } else {
          socket.close();
        }
      }
      const termToDispose = term;
      requestAnimationFrame(() => {
        termToDispose?.dispose();
      });
      socketRef.current = null;
      terminalRef.current = null;
      fitAddonRef.current = null;
    };
  }, [onTerminalUpdate, selectedTerminal, selectedTerminalLifecycleKey, sessionId]);

  useEffect(() => {
    const surface = containerRef.current;
    if (!surface) {
      return undefined;
    }

    const focusTerminal = () => terminalRef.current?.focus();
    surface.addEventListener('click', focusTerminal);
    surface.addEventListener('mousedown', focusTerminal);

    return () => {
      surface.removeEventListener('click', focusTerminal);
      surface.removeEventListener('mousedown', focusTerminal);
    };
  }, [selectedTerminal]);

  if (terminals.length === 0) {
    return null;
  }

  return (
    <section className="terminal-panel" data-testid="terminal-panel">
      <div className="terminal-panel-header">
        <div className="terminal-panel-topbar">
          <div className="terminal-panel-heading">
            <div className="terminal-panel-title-row">
              <h2 className="terminal-panel-title" data-testid="terminal-panel-title">
                {selectedTerminal?.label ?? 'Terminal'}
              </h2>
              {selectedTerminal && (
                <span
                  className={`status-badge terminal-status-badge ${statusBadgeClass(selectedTerminal.status)}`}
                  data-testid="terminal-status-badge"
                >
                  {statusLabel(selectedTerminal.status)}
                </span>
              )}
            </div>
          </div>
          <div className="terminal-panel-actions">
            <button type="button" className="btn btn-ghost terminal-add-btn" onClick={onCreateTerminal} data-testid="terminal-add-tab">
              New terminal
            </button>
          </div>
        </div>
        <div className="terminal-tab-strip">
          <div className="terminal-tab-list" role="tablist" aria-label="Terminal tabs">
            {terminals.map((terminal) => (
              <div
                key={terminal.terminalId}
                className={`terminal-tab${terminal.terminalId === selectedTerminal?.terminalId ? ' terminal-tab-active' : ''}`}
              >
                <button
                  type="button"
                  role="tab"
                  aria-selected={terminal.terminalId === selectedTerminal?.terminalId}
                  className="terminal-tab-button"
                  onClick={() => onSelectTerminal(terminal.terminalId)}
                  data-testid={`terminal-tab-${terminal.terminalId}`}
                >
                  <span className="terminal-tab-label" title={terminal.label}>{terminal.label}</span>
                </button>
                <button
                  type="button"
                  className="terminal-tab-close"
                  aria-label={`Close ${terminal.label}`}
                  data-testid={`terminal-close-${terminal.terminalId}`}
                  onClick={() => {
                    void onCloseTerminal(terminal.terminalId);
                  }}
                >
                  <IconFrame size={14}><CloseIcon size={14} /></IconFrame>
                </button>
              </div>
            ))}
          </div>
        </div>
        {selectedTerminal && (
          <div className="terminal-meta-row" data-testid="terminal-meta-row">
            <div className="terminal-meta-item">
              <span className="terminal-meta-label">Path</span>
              <span className="terminal-meta-value font-mono" title={selectedTerminal.cwd} data-testid="terminal-meta-cwd">
                {selectedTerminal.cwd}
              </span>
            </div>
            {selectedTerminalDetail && (
              <div className="terminal-meta-item">
                <span className="terminal-meta-label">State</span>
                <span className="terminal-meta-value" data-testid="terminal-meta-detail">{selectedTerminalDetail}</span>
              </div>
            )}
          </div>
        )}
      </div>
      <div className="terminal-panel-body">
        <div className="terminal-surface-shell">
          <div className="terminal-surface" ref={containerRef} data-testid="terminal-surface" />
        </div>
      </div>
    </section>
  );
}
