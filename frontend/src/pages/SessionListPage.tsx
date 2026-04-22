import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useSessions } from '../hooks/useSessions';
import { useMultiProject } from '../context/MultiProjectContext';
import FilePanel from '../components/FilePanel';
import FileTabBar from '../components/FileTabBar';
import SessionContextPanel from '../components/SessionContextPanel';
import { ChatBubbleIcon, CloseIcon, IconFrame, InfoIcon } from '../components/Icons';
import { api } from '../api/client';
import type { SessionContextDto } from '../types/api';

export default function SessionListPage() {
  const navigate = useNavigate();
  const {
    getActiveProject,
    sessionsByProject,
    setActiveSession,
    setActiveProject,
    removeSession,
    ensureProjectSession,
  } = useMultiProject();
  const activeProject = getActiveProject();

  const { sessions, loading, error, createSession, deleteSession } = useSessions(activeProject?.id);
  const [contextTabOpen, setContextTabOpen] = useState(false);
  const [activePanelTabId, setActivePanelTabId] = useState<'context'>('context');
  const [contextSessionId, setContextSessionId] = useState<string | null>(null);
  const [contextCache, setContextCache] = useState<Record<string, SessionContextDto>>({});
  const [contextLoading, setContextLoading] = useState(false);
  const [contextError, setContextError] = useState<string | null>(null);

  const filteredSessions = sessions;

  useEffect(() => {
    if (activeProject) {
      setActiveProject(activeProject.id);
    }
  }, [activeProject, setActiveProject]);

  const handleNewSession = async () => {
    if (!activeProject) {
      return;
    }

    try {
      const session = await createSession(activeProject.id, activeProject.worktrees[0]?.id);
      setActiveSession(activeProject.id, session.sessionId);
      navigate(`/projects/${activeProject.id}/sessions/${session.sessionId}`);
    } catch {}
  };

  const handleDeleteSession = async (sessionId: string) => {
    if (!activeProject) {
      return;
    }

    try {
      await deleteSession(sessionId);
      removeSession(activeProject.id, sessionId);

      const remainingSessions = (sessionsByProject[activeProject.id] ?? []).filter(
        (session) => session.sessionId !== sessionId,
      );
      const replacementSession = remainingSessions[0] ?? await ensureProjectSession(activeProject.id);
      setActiveSession(activeProject.id, replacementSession.sessionId);
      navigate(`/projects/${activeProject.id}/sessions/${replacementSession.sessionId}`);
    } catch {}
  };

  const handleOpenContext = async (sessionId: string) => {
    if (contextTabOpen && contextSessionId === sessionId && activePanelTabId === 'context') {
      handleClosePanelTab('context');
      return;
    }

    setContextTabOpen(true);
    setActivePanelTabId('context');
    setContextSessionId(sessionId);
    setContextError(null);

    if (contextCache[sessionId]) {
      return;
    }

    setContextLoading(true);
    try {
      const context = await api.sessions.getContext(sessionId);
      setContextCache((prev) => ({ ...prev, [sessionId]: context }));
    } catch (err: unknown) {
      setContextError(err instanceof Error ? err.message : 'Failed to load session context');
    } finally {
      setContextLoading(false);
    }
  };

  const handleClosePanelTab = (tabId: string) => {
    if (tabId !== 'context') {
      return;
    }

    setContextTabOpen(false);
    setContextSessionId(null);
    setContextError(null);
  };

  const selectedContext = contextSessionId ? contextCache[contextSessionId] ?? null : null;
  const selectedContextLabel = selectedContext?.summary?.name
    ?? (contextSessionId ? filteredSessions.find((s) => s.sessionId === contextSessionId)?.name : null)
    ?? (contextSessionId ? contextSessionId.slice(0, 8) : 'Session Context');

  return (
    <div style={styles.pageShell}>
      <div style={styles.page}>
        <div style={styles.header}>
          <div>
            <h1 style={styles.title}>
              {activeProject ? activeProject.name : 'Sessions'}
            </h1>
            <p style={styles.subtitle}>
              {activeProject
                ? `Sessions scoped to ${activeProject.path}`
                : 'Browse and manage your Code Rhino sessions.'}
            </p>
          </div>
          <button
            className="btn btn-primary"
            style={{
              ...styles.newBtn,
              ...(activeProject ? null : styles.newBtnDisabled),
            }}
            onClick={handleNewSession}
            data-testid="new-session-btn"
            disabled={!activeProject}
            title={activeProject ? 'Create session' : 'Open a project to create a session'}
          >
            + New Session
          </button>
        </div>

        {loading && <div className="state-message">Loading sessions…</div>}
        {error && <div className="state-message error">{error}</div>}

        {!loading && !error && filteredSessions.length === 0 && (
          <div style={styles.empty}>
            <span style={styles.emptyIcon}><ChatBubbleIcon size={32} /></span>
            <p style={styles.emptyText}>
              {activeProject
                ? `No sessions in ${activeProject.name}.`
                : 'No sessions yet. Start a new session to begin.'}
            </p>
            {activeProject && (
              <button
                className="btn btn-primary"
                style={styles.ctaBtn}
                onClick={handleNewSession}
                data-testid="start-conversation-btn"
              >
                Start a Conversation
              </button>
            )}
            {!activeProject && (
              <p style={styles.emptySubtext}>
                Open or create a project to start a session.
              </p>
            )}
          </div>
        )}

        {!loading && !error && filteredSessions.length > 0 && (
          <ul style={styles.list} role="list">
            {filteredSessions.map((s) => (
              <li
                key={s.sessionId}
                style={styles.item}
                onClick={() => {
                  if (!s.projectId) {
                    return;
                  }
                  setActiveProject(s.projectId);
                  setActiveSession(s.projectId, s.sessionId);
                  navigate(`/projects/${s.projectId}/sessions/${s.sessionId}`);
                }}
                data-testid={`session-item-${s.sessionId}`}
              >
                <div style={styles.itemLeft}>
                  <span style={styles.itemId}>{s.name || s.sessionId.slice(0, 8)}</span>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={styles.contextBtn}
                    onClick={(e) => {
                      e.stopPropagation();
                      void handleOpenContext(s.sessionId);
                    }}
                    data-testid={`context-session-${s.sessionId}`}
                    title="Open session context"
                    aria-label="Open session context"
                  >
                    <IconFrame><InfoIcon /></IconFrame>
                  </button>
                  <span style={styles.itemStatus(s.status)}>{s.status}</span>
                </div>
                <div style={styles.itemRight}>
                  <span style={styles.itemDate}>
                    {new Date(s.createdAt).toLocaleString()}
                  </span>
                  {!s.activeRun && (
                    <button
                      type="button"
                      className="btn"
                      style={styles.closeBtn}
                      onClick={(e) => {
                        e.stopPropagation();
                        void handleDeleteSession(s.sessionId);
                      }}
                      data-testid={`close-session-${s.sessionId}`}
                      title="Close session"
                    >
                      <IconFrame><CloseIcon size={14} /></IconFrame>
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      <FilePanel isOpen={contextTabOpen} panelTestId="session-context-side-panel">
        <>
          <FileTabBar
            openFiles={[]}
            extraTabs={[{ id: 'context', label: 'Context', actionLabel: 'Close Context', icon: <InfoIcon /> }]}
            activeTabId={activePanelTabId}
            onSelectTab={() => setActivePanelTabId('context')}
            onCloseTab={handleClosePanelTab}
            showTreeTab={false}
          />
          <SessionContextPanel
            context={selectedContext}
            loading={contextLoading}
            error={contextError}
            sessionLabel={selectedContextLabel}
          />
        </>
      </FilePanel>
    </div>
  );
}

const styles = {
  pageShell: {
    display: 'flex',
    height: '100%',
    width: '100%',
    overflow: 'hidden',
  } as React.CSSProperties,
  page: {
    flex: 1,
    padding: '32px 40px',
    fontFamily: 'var(--font-sans)',
    color: 'var(--text)',
    maxWidth: 900,
    overflow: 'auto',
  } as React.CSSProperties,
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
    marginBottom: 32,
  } as React.CSSProperties,
  title: {
    fontSize: 22,
    fontWeight: 600,
    margin: '0 0 4px',
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  subtitle: {
    fontSize: 13,
    color: 'var(--text-muted)',
    margin: 0,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  newBtn: {
    fontSize: 13,
  } as React.CSSProperties,
  newBtnDisabled: {
    opacity: 0.5,
    cursor: 'not-allowed',
  } as React.CSSProperties,
  empty: {
    textAlign: 'center' as const,
    padding: '60px 0',
  } as React.CSSProperties,
  emptyIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  emptyText: {
    color: 'var(--text-muted)',
    fontSize: 14,
    marginTop: 12,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  emptySubtext: {
    color: 'var(--text-muted)',
    fontSize: 13,
    marginTop: 8,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  ctaBtn: {
    marginTop: 16,
    fontSize: 14,
  } as React.CSSProperties,
  list: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  item: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '16px 20px',
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    cursor: 'pointer',
    transition: 'box-shadow 0.15s',
  } as React.CSSProperties,
  itemLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  } as React.CSSProperties,
  contextBtn: {
    border: '1px solid var(--border)',
    borderRadius: '999px',
    width: 22,
    height: 22,
    padding: 0,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 11,
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  itemId: {
    fontFamily: 'var(--font-mono)',
    fontSize: 13,
    color: 'var(--text)',
  } as React.CSSProperties,
  itemStatus: (status: string) => ({
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 'var(--radius-sm)',
    background: status === 'ACTIVE' ? 'rgba(15,123,108,0.12)' : 'rgba(155,154,151,0.12)',
    color: status === 'ACTIVE' ? 'var(--green)' : 'var(--text-muted)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties),
  itemDate: {
    fontSize: 12,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
  } as React.CSSProperties,
  itemRight: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
  } as React.CSSProperties,
  closeBtn: {
    border: 'none',
    background: 'transparent',
    fontSize: 16,
    color: 'var(--text-muted)',
    padding: '0 4px',
    lineHeight: 1,
    borderRadius: 'var(--radius-sm)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
};
