import { Routes, Route, Navigate, useParams, useNavigate, useLocation } from 'react-router-dom';
import { useState, useCallback, useEffect, useRef } from 'react';
import type { CSSProperties, ReactNode } from 'react';
import SessionListPage from './pages/SessionListPage';
import ChatPage from './pages/ChatPage';
import SettingsPage, { type SettingsTab } from './pages/SettingsPage';
import ApprovalsPage from './pages/ApprovalsPage';
import IdeLeftPanel from './components/IdeLeftPanel';
import { Popup } from './components/Popup';
import { FileIcon, PanelFoldIcon, ServiceStatusIcon, TerminalIcon } from './components/Icons';
import { MultiProjectProvider, useMultiProject } from './context/MultiProjectContext';
import { useKeyboardShortcuts } from './hooks/useKeyboardShortcuts';
import { useSettings } from './hooks/useSettings';
import { useTheme } from './hooks/useTheme';
import { api } from './api/client';
import type {
  LspServerStatusDto,
  McpServerStatusDto,
  PluginStatusDto,
  ServiceStatusDto,
  SessionDto,
  TaskCompletionDto,
} from './types/api';

type ServiceStatusTab = 'mcp' | 'lsp' | 'plugins';

const PROJECT_SIDEBAR_STORAGE_KEY = 'coderhino-project-sidebar-width';
const PROJECT_SIDEBAR_MIN_WIDTH = 220;
const PROJECT_SIDEBAR_MAX_WIDTH = 520;
const PROJECT_SIDEBAR_DEFAULT_WIDTH = 260;
const PROJECT_SIDEBAR_COLLAPSED_WIDTH = 72;

function clampProjectSidebarWidth(width: number) {
  return Math.max(PROJECT_SIDEBAR_MIN_WIDTH, Math.min(PROJECT_SIDEBAR_MAX_WIDTH, width));
}

function getStoredProjectSidebarWidth() {
  try {
    const raw = localStorage.getItem(PROJECT_SIDEBAR_STORAGE_KEY);
    if (!raw) {
      return PROJECT_SIDEBAR_DEFAULT_WIDTH;
    }
    const parsed = Number.parseInt(raw, 10);
    if (Number.isNaN(parsed)) {
      return PROJECT_SIDEBAR_DEFAULT_WIDTH;
    }
    return clampProjectSidebarWidth(parsed);
  } catch {
    return PROJECT_SIDEBAR_DEFAULT_WIDTH;
  }
}

const serviceStatusTabs: Array<{ id: ServiceStatusTab; label: string }> = [
  { id: 'mcp', label: 'MCP server status' },
  { id: 'lsp', label: 'LSP status' },
  { id: 'plugins', label: 'Plugins status' },
];

function formatCommandLine(commandLine: string[]) {
  return commandLine.join(' ');
}

function formatLastStartedAt(timestamp?: string | null) {
  if (!timestamp) {
    return 'Not started';
  }
  const parsed = new Date(timestamp);
  return Number.isNaN(parsed.getTime()) ? timestamp : parsed.toLocaleString();
}

function StatusCard({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section style={serviceStatusStyles.card}>
      <h3 style={serviceStatusStyles.cardTitle}>{title}</h3>
      <div style={serviceStatusStyles.cardBody}>{children}</div>
    </section>
  );
}

function ServiceStatusPopup({
  isOpen,
  activeTab,
  onTabChange,
  onClose,
  status,
  loading,
  error,
}: {
  isOpen: boolean;
  activeTab: ServiceStatusTab;
  onTabChange: (tab: ServiceStatusTab) => void;
  onClose: () => void;
  status: ServiceStatusDto | null;
  loading: boolean;
  error: string | null;
}) {
  const renderMcp = (servers: McpServerStatusDto[]) => {
    if (servers.length === 0) {
      return <div className="state-message">No MCP servers configured.</div>;
    }
    return (
      <div style={serviceStatusStyles.list} data-testid="service-status-mcp-list">
        {servers.map((server) => (
          <StatusCard key={server.name} title={server.name}>
            <div style={serviceStatusStyles.metaRow}><strong>Status</strong><span>{server.status}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Enabled</strong><span>{server.enabled ? 'Yes' : 'No'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Connected</strong><span>{server.connected ? 'Yes' : 'No'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Command</strong><code>{server.command}</code></div>
            <div style={serviceStatusStyles.metaRow}><strong>Command line</strong><code>{formatCommandLine(server.commandLine)}</code></div>
            <div style={serviceStatusStyles.metaRow}><strong>Process</strong><span>{server.processId ?? 'Not running'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Last started</strong><span>{formatLastStartedAt(server.lastStartedAt)}</span></div>
          </StatusCard>
        ))}
      </div>
    );
  };

  const renderLsp = (servers: LspServerStatusDto[]) => {
    if (servers.length === 0) {
      return <div className="state-message">No LSP servers configured.</div>;
    }
    return (
      <div style={serviceStatusStyles.list} data-testid="service-status-lsp-list">
        {servers.map((server) => (
          <StatusCard key={server.language} title={server.language}>
            <div style={serviceStatusStyles.metaRow}><strong>Status</strong><span>{server.status}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Enabled</strong><span>{server.enabled ? 'Yes' : 'No'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Connected</strong><span>{server.connected ? 'Yes' : 'No'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Command</strong><code>{server.command}</code></div>
            <div style={serviceStatusStyles.metaRow}><strong>Command line</strong><code>{formatCommandLine(server.commandLine)}</code></div>
            <div style={serviceStatusStyles.metaRow}><strong>Process</strong><span>{server.processId ?? 'Not running'}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>Last started</strong><span>{formatLastStartedAt(server.lastStartedAt)}</span></div>
          </StatusCard>
        ))}
      </div>
    );
  };

  const renderPlugins = (plugins: PluginStatusDto[]) => {
    if (plugins.length === 0) {
      return <div className="state-message">No plugins loaded.</div>;
    }
    return (
      <div style={serviceStatusStyles.list} data-testid="service-status-plugins-list">
        {plugins.map((plugin) => (
          <StatusCard key={plugin.id} title={plugin.name || plugin.id}>
            <div style={serviceStatusStyles.metaRow}><strong>Status</strong><span>{plugin.status}</span></div>
            <div style={serviceStatusStyles.metaRow}><strong>ID</strong><code>{plugin.id}</code></div>
            <div style={serviceStatusStyles.metaRow}><strong>Version</strong><span>{plugin.version || 'Unknown'}</span></div>
            <div style={serviceStatusStyles.descriptionRow}>{plugin.description || 'No description provided.'}</div>
          </StatusCard>
        ))}
      </div>
    );
  };

  const renderActiveTab = () => {
    if (loading) {
      return <div className="state-message">Loading service status…</div>;
    }
    if (error) {
      return <div className="state-message error">{error}</div>;
    }
    if (!status) {
      return <div className="state-message">No service status available.</div>;
    }
    if (activeTab === 'mcp') {
      return renderMcp(status.mcpServers);
    }
    if (activeTab === 'lsp') {
      return renderLsp(status.lspServers);
    }
    return renderPlugins(status.plugins);
  };

  return (
    <Popup
      isOpen={isOpen}
      onClose={onClose}
      title="Service status"
      contentStyle={{ width: 'min(920px, 94vw)', minWidth: 'min(920px, 94vw)', maxHeight: '88vh', display: 'flex', flexDirection: 'column' }}
      bodyStyle={{ padding: 20, display: 'flex', flexDirection: 'column', gap: 16, overflow: 'auto' }}
    >
      <div style={serviceStatusStyles.tabList} role="tablist" aria-label="Service status tabs" data-testid="service-status-tabs">
        {serviceStatusTabs.map((tab) => {
          const selected = activeTab === tab.id;
          return (
            <button
              key={tab.id}
              type="button"
              role="tab"
              id={`service-status-tab-${tab.id}`}
              aria-selected={selected}
              aria-controls={`service-status-panel-${tab.id}`}
              className="btn btn-ghost"
              style={{ ...serviceStatusStyles.tab, ...(selected ? serviceStatusStyles.tabActive : null) }}
              onClick={() => onTabChange(tab.id)}
              data-testid={`service-status-tab-${tab.id}`}
            >
              {tab.label}
            </button>
          );
        })}
      </div>
      {serviceStatusTabs.map((tab) => (
        <div
          key={tab.id}
          role="tabpanel"
          id={`service-status-panel-${tab.id}`}
          aria-labelledby={`service-status-tab-${tab.id}`}
          hidden={activeTab !== tab.id}
          data-testid={`service-status-panel-${tab.id}`}
        >
          {activeTab === tab.id ? renderActiveTab() : null}
        </div>
      ))}
    </Popup>
  );
}

function LegacySessionRedirect() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { openProjectIds, sessionsByProject } = useMultiProject();

  useEffect(() => {
    if (!id) return;

    for (const projectId of openProjectIds) {
      const sessions = sessionsByProject[projectId] || [];
      const session = sessions.find((s: SessionDto) => s.sessionId === id);
      if (session) {
        navigate(`/projects/${projectId}/sessions/${id}`, { replace: true });
        return;
      }
    }

    api.sessions.get(id).then((session) => {
      const projectId = session.projectId;
      if (!projectId) {
        navigate('/sessions', { replace: true });
        return;
      }
      navigate(`/projects/${projectId}/sessions/${id}`, { replace: true });
    }).catch(() => {
      navigate('/sessions', { replace: true });
    });
  }, [id, navigate, openProjectIds, sessionsByProject]);

  return <div className="state-message">Redirecting...</div>;
}

function AppContent() {
  useKeyboardShortcuts();
  useTheme();

  const location = useLocation();
  const {
    projects,
    openProjectIds,
    activeSessionByProject,
    registerTaskCompletion,
  } = useMultiProject();
  const [fileExplorerOpen, setFileExplorerOpen] = useState(false);
  const [fileExplorerToggleVersion, setFileExplorerToggleVersion] = useState(0);
  const [terminalPanelOpen, setTerminalPanelOpen] = useState(false);
  const [projectViewOpen, setProjectViewOpen] = useState(true);
  const [projectSidebarWidth, setProjectSidebarWidth] = useState(getStoredProjectSidebarWidth);
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [settingsTab, setSettingsTab] = useState<SettingsTab>('general');
  const [serviceStatusOpen, setServiceStatusOpen] = useState(false);
  const [serviceStatusTab, setServiceStatusTab] = useState<ServiceStatusTab>('mcp');
  const [serviceStatus, setServiceStatus] = useState<ServiceStatusDto | null>(null);
  const [serviceStatusLoading, setServiceStatusLoading] = useState(false);
  const [serviceStatusError, setServiceStatusError] = useState<string | null>(null);
  const [toolbarSearchValue, setToolbarSearchValue] = useState('');
  const projectSidebarDraggingRef = useRef(false);
  const projectSidebarWidthRef = useRef(projectSidebarWidth);
  const settingsState = useSettings();
  const toggleFileExplorer = useCallback(() => {
    setFileExplorerToggleVersion((version) => version + 1);
  }, []);
  const toggleTerminalPanel = useCallback(() => setTerminalPanelOpen((o) => !o), []);
  const toggleProjectView = useCallback(() => setProjectViewOpen((open) => !open), []);
  const projectRouteMatch = location.pathname.match(/^\/projects\/([^/]+)\/sessions\/[^/]+$/);
  const activeProjectId = projectRouteMatch?.[1] ?? null;
  const activeProject = activeProjectId ? projects[activeProjectId] ?? null : null;
  const showWorkspaceToolbar = activeProjectId != null;
  const sidebarCollapsed = showWorkspaceToolbar && !projectViewOpen;
  const projectToggleLabel = projectViewOpen ? 'Fold project panel' : 'Expand project panel';
  const terminalToggleLabel = terminalPanelOpen ? 'Hide terminal panel' : 'Show terminal panel';
  const fileToggleLabel = fileExplorerOpen ? 'Close file explorer' : 'Open file explorer';
  const serviceStatusToggleLabel = serviceStatusOpen ? 'Close service status' : 'Open service status';
  const effectiveProjectSidebarWidth = sidebarCollapsed ? PROJECT_SIDEBAR_COLLAPSED_WIDTH : projectSidebarWidth;
  const seenCompletionIdsRef = useRef<Set<string>>(new Set());
  const latestCompletionTimestampRef = useRef<number>(0);
  const notificationAudioRef = useRef<HTMLAudioElement | null>(null);

  const completionKey = useCallback((completion: TaskCompletionDto) => {
    if (completion.runId) {
      return `run:${completion.runId}`;
    }
    if (completion.taskId) {
      return `task:${completion.taskId}`;
    }
    if (completion.completionId) {
      return completion.completionId;
    }
    return `${completion.projectId ?? 'unknown'}:${completion.sessionId ?? 'unknown'}:${completion.completedAt}`;
  }, []);

  const playTaskCompletionSound = useCallback(() => {
    if (typeof Audio === 'undefined') {
      return;
    }
    let audio = notificationAudioRef.current;
    if (!audio) {
      audio = new Audio('data:audio/wav;base64,UklGRlQAAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YTAAAAAAAP//AAD//wAA//8AAP//AAD//wAA');
      notificationAudioRef.current = audio;
    }
    audio.currentTime = 0;
    void audio.play().catch(() => {
    });
  }, []);

  const isCompletionVisible = useCallback((completion: TaskCompletionDto) => {
    const projectId = completion.projectId ?? null;
    const sessionId = completion.sessionId ?? null;
    if (!projectId || !sessionId) {
      return false;
    }
    const routeMatch = location.pathname.match(/^\/projects\/([^/]+)\/sessions\/([^/]+)$/);
    const routeProjectId = routeMatch?.[1] ?? null;
    const routeSessionId = routeMatch?.[2] ?? null;
    if (routeProjectId === projectId && routeSessionId === sessionId) {
      return true;
    }
    return activeSessionByProject[projectId] === sessionId;
  }, [activeSessionByProject, location.pathname]);

  useEffect(() => {
    if (!location.pathname.startsWith('/projects/')) {
      setProjectViewOpen(true);
    }
  }, [location.pathname]);

  useEffect(() => {
    projectSidebarWidthRef.current = projectSidebarWidth;
  }, [projectSidebarWidth]);

  useEffect(() => {
    if (projectSidebarDraggingRef.current) {
      return;
    }
    try {
      localStorage.setItem(PROJECT_SIDEBAR_STORAGE_KEY, String(projectSidebarWidth));
    } catch {
    }
  }, [projectSidebarWidth]);

  useEffect(() => {
    return () => {
      projectSidebarDraggingRef.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
    };
  }, []);

  const handleProjectSidebarResizeStart = useCallback((event: React.MouseEvent<HTMLDivElement>) => {
    if (sidebarCollapsed) {
      return;
    }
    event.preventDefault();
    projectSidebarDraggingRef.current = true;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const handleMouseMove = (moveEvent: MouseEvent) => {
      if (!projectSidebarDraggingRef.current) {
        return;
      }
      const nextWidth = clampProjectSidebarWidth(moveEvent.clientX);
      projectSidebarWidthRef.current = nextWidth;
      setProjectSidebarWidth(nextWidth);
    };

    const handleMouseUp = () => {
      projectSidebarDraggingRef.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      try {
        localStorage.setItem(PROJECT_SIDEBAR_STORAGE_KEY, String(projectSidebarWidthRef.current));
      } catch {
      }
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [sidebarCollapsed]);

  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const response = await api.tasks.completions(latestCompletionTimestampRef.current || undefined);
        if (cancelled) {
          return;
        }
        for (const completion of response.completions) {
          const completedAtMs = Date.parse(completion.completedAt);
          if (Number.isFinite(completedAtMs)) {
            latestCompletionTimestampRef.current = Math.max(latestCompletionTimestampRef.current, completedAtMs);
          }
          const key = completionKey(completion);
          if (seenCompletionIdsRef.current.has(key)) {
            continue;
          }
          seenCompletionIdsRef.current.add(key);
          const visible = isCompletionVisible(completion);
          registerTaskCompletion(completion, visible);
          playTaskCompletionSound();
        }
      } catch {
      }
    };

    void poll();
    const intervalId = window.setInterval(() => {
      void poll();
    }, 3000);

    return () => {
      cancelled = true;
      window.clearInterval(intervalId);
    };
  }, [completionKey, isCompletionVisible, playTaskCompletionSound, registerTaskCompletion, openProjectIds]);

  useEffect(() => {
    if (!serviceStatusOpen) {
      return;
    }

    let cancelled = false;
    setServiceStatusLoading(true);
    setServiceStatusError(null);

    api.system.status()
      .then((response) => {
        if (cancelled) {
          return;
        }
        setServiceStatus(response);
      })
      .catch((error: unknown) => {
        if (cancelled) {
          return;
        }
        setServiceStatusError(error instanceof Error ? error.message : 'Failed to load service status');
      })
      .finally(() => {
        if (!cancelled) {
          setServiceStatusLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [serviceStatusOpen]);

  return (
    <div className="app-shell">
      {showWorkspaceToolbar && (
        <div className="app-toolbar" data-testid="chat-toolbar">
          <div className="app-toolbar-section">
            <button
              type="button"
              className={`btn btn-ghost app-toolbar-btn${projectViewOpen ? '' : ' app-toolbar-btn-active'}`}
              onClick={toggleProjectView}
              title={projectToggleLabel}
              aria-label={projectToggleLabel}
              data-testid="toolbar-project-toggle"
            >
              <span className="app-toolbar-icon" data-testid="toolbar-project-toggle-icon"><PanelFoldIcon /></span>
            </button>
          </div>
          <div className="app-toolbar-center">
            <input
              type="search"
              className="input-field app-toolbar-search"
              value={toolbarSearchValue}
              onChange={(event) => setToolbarSearchValue(event.target.value)}
              placeholder="Search"
              data-testid="toolbar-search-input"
            />
          </div>
          <div className="app-toolbar-section app-toolbar-section-right">
            {activeProject != null && (
              <button
                type="button"
                className={`btn btn-ghost app-toolbar-btn${terminalPanelOpen ? ' app-toolbar-btn-active' : ''}`}
                onClick={toggleTerminalPanel}
                title={terminalToggleLabel}
                aria-label={terminalToggleLabel}
                data-testid="toolbar-terminal-toggle"
              >
                <span className="app-toolbar-icon"><TerminalIcon /></span>
              </button>
            )}
            {activeProject != null && (
              <button
                type="button"
                className={`btn btn-ghost app-toolbar-btn${fileExplorerOpen ? ' app-toolbar-btn-active' : ''}`}
                onClick={toggleFileExplorer}
                title={fileToggleLabel}
                aria-label={fileToggleLabel}
                data-testid="toolbar-file-toggle"
              >
                <span className="app-toolbar-icon"><FileIcon /></span>
              </button>
            )}
            {activeProject != null && (
              <button
                type="button"
                className={`btn btn-ghost app-toolbar-btn${serviceStatusOpen ? ' app-toolbar-btn-active' : ''}`}
                onClick={() => setServiceStatusOpen((open) => !open)}
                title={serviceStatusToggleLabel}
                aria-label={serviceStatusToggleLabel}
                data-testid="toolbar-service-status-toggle"
              >
                <span className="app-toolbar-icon"><ServiceStatusIcon /></span>
              </button>
            )}
          </div>
        </div>
      )}
      <div className="app-workspace">
        <aside
          className={`app-sidebar${sidebarCollapsed ? ' app-sidebar-collapsed' : ''}`}
          style={{ '--app-sidebar-width': `${effectiveProjectSidebarWidth}px` } as CSSProperties}
          data-testid="app-sidebar"
        >
          {!sidebarCollapsed && (
            <div
              className="app-sidebar-resize-handle"
              onMouseDown={handleProjectSidebarResizeStart}
              data-testid="app-sidebar-resize-handle"
            />
          )}
          <IdeLeftPanel collapsed={sidebarCollapsed} settings={settingsState.settings} onOpenSettings={() => {
            setSettingsTab('general');
            setSettingsOpen(true);
          }} />
        </aside>
        <main className="app-main">
          <Routes>
            <Route path="/" element={<Navigate to="/sessions" replace />} />
            <Route path="/sessions" element={<SessionListPage />} />
            <Route path="/projects/:projectId/sessions/:id" element={
              <ChatPage
                fileExplorerOpen={fileExplorerOpen}
                fileExplorerToggleVersion={fileExplorerToggleVersion}
                onFileExplorerVisibilityChange={setFileExplorerOpen}
                terminalPanelOpen={terminalPanelOpen}
                onTerminalPanelVisibilityChange={setTerminalPanelOpen}
                settings={settingsState.settings}
              />
            } />
            <Route path="/sessions/:id" element={<LegacySessionRedirect />} />
            <Route path="/settings" element={<SettingsPage settingsState={settingsState} />} />
            <Route path="/approvals" element={<ApprovalsPage />} />
          </Routes>
        </main>
      </div>
      <Popup
        isOpen={settingsOpen}
        onClose={() => setSettingsOpen(false)}
        title="Settings"
        showCloseButton={false}
        contentStyle={{
          width: 'min(960px, 94vw)',
          minWidth: 'min(960px, 94vw)',
          height: 600,
          maxHeight: '88vh',
          display: 'flex',
          flexDirection: 'column',
        }}
        bodyStyle={{ padding: 16, flex: 1, overflow: 'auto' }}
      >
        <SettingsPage embedded activeTab={settingsTab} onTabChange={setSettingsTab} showTabs settingsState={settingsState} />
      </Popup>
      <ServiceStatusPopup
        isOpen={serviceStatusOpen}
        activeTab={serviceStatusTab}
        onTabChange={setServiceStatusTab}
        onClose={() => setServiceStatusOpen(false)}
        status={serviceStatus}
        loading={serviceStatusLoading}
        error={serviceStatusError}
      />
    </div>
  );
}

const serviceStatusStyles: Record<string, CSSProperties> = {
  tabList: {
    display: 'flex',
    gap: 8,
    flexWrap: 'wrap',
  },
  tab: {
    borderRadius: 999,
    padding: '8px 12px',
  },
  tabActive: {
    background: 'var(--surface-accent)',
    borderColor: 'var(--accent)',
    color: 'var(--accent)',
  },
  list: {
    display: 'grid',
    gap: 12,
  },
  card: {
    border: '1px solid var(--border)',
    borderRadius: 14,
    padding: 16,
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-sm)',
  },
  cardTitle: {
    margin: '0 0 12px',
    fontSize: 15,
    fontWeight: 600,
    color: 'var(--text)',
  },
  cardBody: {
    display: 'grid',
    gap: 10,
  },
  metaRow: {
    display: 'grid',
    gridTemplateColumns: '120px minmax(0, 1fr)',
    gap: 12,
    alignItems: 'start',
    fontSize: 13,
    color: 'var(--text)',
  },
  descriptionRow: {
    fontSize: 13,
    color: 'var(--text-muted)',
    lineHeight: 1.5,
  },
};

export default function App() {
  return (
    <MultiProjectProvider>
      <AppContent />
    </MultiProjectProvider>
  );
}
