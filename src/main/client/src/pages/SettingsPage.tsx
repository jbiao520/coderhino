import { useEffect, useMemo, useState } from 'react';
import type { FormEvent } from 'react';
import { useSettings } from '../hooks/useSettings';
import { useCredentials } from '../hooks/useCredentials';
import { useMcpConfig } from '../hooks/useMcpConfig';
import { FONT_FAMILY_OPTIONS, FONT_SIZE_OPTIONS } from '../lib/webUiFontSettings';
import type { CredentialProviderDto, CredentialProviderUpdate, WebSettings } from '../types/api';
import './SettingsPage.css';

interface SettingsPageProps {
  embedded?: boolean;
  activeTab?: SettingsTab;
  onTabChange?: (tab: SettingsTab) => void;
  showTabs?: boolean;
  settingsState?: ReturnType<typeof useSettings>;
}

interface EditableProvider {
  id: string;
  name: string;
  apiBaseUrl: string;
  modelsText: string;
  apiKeyInput: string;
  showApiKeyInput: boolean;
  apiKeyMasked: string | null;
  hasApiKey: boolean;
}

type SettingsTab = 'general' | 'providers' | 'mcp';

function toEditableProviders(providers: CredentialProviderDto[] | undefined): EditableProvider[] {
  return (providers ?? []).map((provider) => ({
    id: provider.id,
    name: provider.name,
    apiBaseUrl: provider.apiBaseUrl ?? '',
    modelsText: (provider.models ?? []).join(', '),
    apiKeyInput: '',
    showApiKeyInput: false,
    apiKeyMasked: provider.apiKeyMasked,
    hasApiKey: provider.hasApiKey,
  }));
}

function parseModels(modelsText: string): string[] {
  return modelsText
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
}

const tabMeta: Array<{ id: SettingsTab; label: string; eyebrow: string; description: string }> = [
  {
    id: 'general',
    label: 'General',
    eyebrow: '',
    description: '',
  },
  {
    id: 'providers',
    label: 'Providers',
    eyebrow: '',
    description: '',
  },
  {
    id: 'mcp',
    label: 'MCP Config',
    eyebrow: '',
    description: '',
  },
];

export type { SettingsTab };

export default function SettingsPage({ embedded = false, activeTab, onTabChange, showTabs = true, settingsState }: SettingsPageProps) {
  const localSettingsState = useSettings();
  const {
    settings,
    loading: settingsLoading,
    error: settingsError,
    saving,
    saveSettings,
  } = settingsState ?? localSettingsState;
  const {
    credentials,
    loading: credentialsLoading,
    error: credentialsError,
    saving: credsSaving,
    saveCredentials,
  } = useCredentials();

  const [internalActiveTab, setInternalActiveTab] = useState<SettingsTab>('general');
  const resolvedActiveTab = activeTab ?? internalActiveTab;
  const handleTabChange = onTabChange ?? setInternalActiveTab;
  const {
    config: mcpConfig,
    loading: mcpLoading,
    error: mcpError,
    saving: mcpSaving,
    saveConfig,
  } = useMcpConfig(resolvedActiveTab === 'mcp');

  const [permMode, setPermMode] = useState('');
  const [theme, setTheme] = useState('');
  const [model, setModel] = useState('');
  const [sidebarFontFamily, setSidebarFontFamily] = useState('sans');
  const [sidebarFontSize, setSidebarFontSize] = useState('13');
  const [chatFontFamily, setChatFontFamily] = useState('sans');
  const [chatFontSize, setChatFontSize] = useState('13');
  const [saved, setSaved] = useState(false);

  const [providers, setProviders] = useState<EditableProvider[]>([]);
  const [defaultProviderId, setDefaultProviderId] = useState('');
  const [credsSaved, setCredsSaved] = useState(false);

  const [mcpContent, setMcpContent] = useState('');
  const [mcpSaved, setMcpSaved] = useState(false);

  useEffect(() => {
    if (settings) {
      setPermMode(settings.defaultPermissionMode);
      setTheme(settings.theme);
      setModel(settings.defaultModel ?? '');
      setSidebarFontFamily(settings.sidebarFontFamily ?? 'sans');
      setSidebarFontSize(String(settings.sidebarFontSize ?? 13));
      setChatFontFamily(settings.chatFontFamily ?? 'sans');
      setChatFontSize(String(settings.chatFontSize ?? 13));
    }
  }, [settings]);

  useEffect(() => {
    if (credentials) {
      const nextProviders = toEditableProviders(credentials.providers);
      setProviders(nextProviders);
      setDefaultProviderId(credentials.defaultProviderId ?? nextProviders[0]?.id ?? '');
    }
  }, [credentials]);

  useEffect(() => {
    if (mcpConfig) {
      setMcpContent(mcpConfig.content);
    }
  }, [mcpConfig]);

  const providerOptions = useMemo(
    () => providers.map((provider) => ({ id: provider.id, name: provider.name.trim() || provider.id })),
    [providers],
  );
  const activeTabMeta = tabMeta.find((tab) => tab.id === resolvedActiveTab) ?? tabMeta[0]!;
  const shouldShowNavigation = showTabs || embedded;

  const handleSave = async (event: FormEvent) => {
    event.preventDefault();
    await saveSettings({
      defaultPermissionMode: permMode,
      theme,
      defaultModel: model,
      sidebarFontFamily,
      sidebarFontSize: Number(sidebarFontSize),
      chatFontFamily,
      chatFontSize: Number(chatFontSize),
    });
    setSaved(true);
    setTimeout(() => setSaved(false), 2000);
  };

  const sidebarPreviewStyle = getFontPreviewStyle({
    sidebarFontFamily,
    sidebarFontSize: Number(sidebarFontSize),
  }, 'sidebar');

  const chatPreviewStyle = getFontPreviewStyle({
    chatFontFamily,
    chatFontSize: Number(chatFontSize),
  }, 'chat');

  const handleProviderChange = (providerId: string, updates: Partial<EditableProvider>) => {
    setProviders((current) => current.map((provider) => (
      provider.id === providerId ? { ...provider, ...updates } : provider
    )));
  };

  const handleAddProvider = () => {
    setProviders((current) => {
      const nextId = `provider-${current.length + 1}`;
      const next = [
        ...current,
        {
          id: nextId,
          name: `Provider ${current.length + 1}`,
          apiBaseUrl: '',
          modelsText: '',
          apiKeyInput: '',
          showApiKeyInput: false,
          apiKeyMasked: null,
          hasApiKey: false,
        },
      ];
      if (!defaultProviderId) {
        setDefaultProviderId(nextId);
      }
      return next;
    });
  };

  const handleRemoveProvider = (providerId: string) => {
    setProviders((current) => {
      const next = current.filter((provider) => provider.id !== providerId);
      if (defaultProviderId === providerId) {
        setDefaultProviderId(next[0]?.id ?? '');
      }
      return next;
    });
  };

  const handleCredsSave = async (event: FormEvent) => {
    event.preventDefault();
    const payloadProviders: CredentialProviderUpdate[] = providers.map((provider) => {
      const nextProvider: CredentialProviderUpdate = {
        id: provider.id,
        name: provider.name.trim() || provider.id,
        apiBaseUrl: provider.apiBaseUrl.trim() || null,
        models: parseModels(provider.modelsText),
      };
      if (provider.apiKeyInput.trim()) {
        nextProvider.apiKey = provider.apiKeyInput.trim();
      }
      return nextProvider;
    });
    await saveCredentials({
      defaultProviderId: defaultProviderId || payloadProviders[0]?.id || null,
      providers: payloadProviders,
    });
    setProviders((current) => current.map((provider) => ({
      ...provider,
      apiKeyInput: '',
      showApiKeyInput: false,
    })));
    setCredsSaved(true);
    setTimeout(() => setCredsSaved(false), 2000);
  };

  const handleMcpSave = async (event: FormEvent) => {
    event.preventDefault();
    try {
      await saveConfig({ content: mcpContent });
      setMcpSaved(true);
      setTimeout(() => setMcpSaved(false), 2000);
    } catch {
    }
  };

  if (settingsLoading) {
    return <div className="state-message">Loading settings…</div>;
  }

  if (settingsError) {
    return <div className="state-message error">{settingsError}</div>;
  }

  return (
    <div
      className={`settings-shell${embedded ? ' settings-shell-embedded' : ''}`}
      data-testid={embedded ? 'settings-embedded' : 'settings-page'}
    >
      {!embedded && (
        <header className="settings-page-header">
          <div>
            <p className="settings-page-eyebrow">Preferences</p>
            <h1 className="settings-page-title">Settings</h1>
          </div>
          <p className="settings-page-copy">Configure workspace settings.</p>
        </header>
      )}

      <div className="settings-layout">
        {shouldShowNavigation && (
          <>
            <div className="settings-mobile-nav" data-testid="settings-nav-mobile">
              <label className="settings-mobile-nav-label" htmlFor="settings-section-select">
                Settings section
              </label>
              <select
                id="settings-section-select"
                className="input-field settings-mobile-nav-select"
                value={resolvedActiveTab}
                onChange={(event) => handleTabChange(event.target.value as SettingsTab)}
                data-testid="settings-section-select"
              >
                {tabMeta.map((tab) => (
                  <option key={tab.id} value={tab.id}>{tab.label}</option>
                ))}
              </select>
            </div>

            <nav className="settings-sidebar" aria-label="Settings sections" data-testid="settings-tabs">
              <div className="settings-sidebar-card">
                <p className="settings-sidebar-eyebrow">Navigate</p>
                <div className="settings-sidebar-list" role="tablist" aria-orientation="vertical">
                  {tabMeta.map((tab) => {
                    const selected = resolvedActiveTab === tab.id;
                    return (
                      <button
                        key={tab.id}
                        type="button"
                        role="tab"
                        aria-selected={selected}
                        aria-controls={`settings-tabpanel-${tab.id}`}
                        id={`settings-tab-${tab.id}`}
                        className={`settings-sidebar-tab${selected ? ' settings-sidebar-tab-active' : ''}`}
                        onClick={() => handleTabChange(tab.id)}
                        data-testid={`settings-tab-${tab.id}`}
                      >
                        <span className="settings-sidebar-tab-label">{tab.label}</span>
                        <span className="settings-sidebar-tab-copy">{tab.eyebrow}</span>
                      </button>
                    );
                  })}
                </div>
              </div>
            </nav>
          </>
        )}

        <section className="settings-panel" aria-live="polite">
          <div className="settings-panel-hero">
            <div>
              <p className="settings-panel-eyebrow">{activeTabMeta.eyebrow}</p>
              <h2 className="settings-panel-title">{activeTabMeta.label}</h2>
            </div>
            <p className="settings-panel-copy">{activeTabMeta.description}</p>
          </div>

          {resolvedActiveTab === 'general' && (
            <section
              role="tabpanel"
              id="settings-tabpanel-general"
              aria-labelledby="settings-tab-general"
              className="settings-tabpanel settings-panel-enter"
              data-testid="settings-tabpanel-general"
            >
              <form onSubmit={handleSave} className="settings-form" data-testid="settings-form">
                <section className="settings-card" data-testid="settings-general-overview">
                  <div className="settings-card-header">
                    <div>
                      <h3 className="settings-card-title">Workspace defaults</h3>
                    </div>
                  </div>

                  <div className="settings-grid settings-grid-tight">
                    <div className="settings-field">
                      <label className="settings-label" htmlFor="permMode">
                        Default Permission Mode
                      </label>
                      <select
                        id="permMode"
                        value={permMode}
                        onChange={(event) => setPermMode(event.target.value)}
                        className="input-field settings-input settings-select"
                        data-testid="perm-mode-select"
                      >
                        <option value="BYPASS">BYPASS</option>
                        <option value="AUTO">AUTO</option>
                      </select>
                    </div>

                    <div className="settings-field">
                      <label className="settings-label" htmlFor="theme">
                        Theme
                      </label>
                      <select
                        id="theme"
                        value={theme}
                        onChange={(event) => setTheme(event.target.value)}
                        className="input-field settings-input settings-select"
                        data-testid="theme-select"
                      >
                        <option value="system">System</option>
                        <option value="dark">Dark</option>
                        <option value="light">Light</option>
                      </select>
                    </div>
                  </div>

                  <div className="settings-field">
                    <label className="settings-label" htmlFor="model">
                      Default Model
                    </label>
                    <input
                      id="model"
                      type="text"
                      value={model}
                      onChange={(event) => setModel(event.target.value)}
                      className="input-field settings-input"
                      data-testid="model-input"
                    />
                  </div>
                </section>

                <section className="settings-font-grid">
                  <section className="settings-card" data-testid="sidebar-font-settings-group">
                    <div className="settings-card-header">
                      <div>
                        <h3 className="settings-card-title">Project Sidebar Font</h3>
                        
                      </div>
                    </div>

                    <div className="settings-grid">
                      <div className="settings-field">
                        <label className="settings-label" htmlFor="sidebar-font-family">Font Family</label>
                        <select
                          id="sidebar-font-family"
                          value={sidebarFontFamily}
                          onChange={(event) => setSidebarFontFamily(event.target.value)}
                          className="input-field settings-input settings-select"
                          data-testid="sidebar-font-family-select"
                        >
                          {FONT_FAMILY_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                      </div>

                      <div className="settings-field">
                        <label className="settings-label" htmlFor="sidebar-font-size">Font Size</label>
                        <select
                          id="sidebar-font-size"
                          value={sidebarFontSize}
                          onChange={(event) => setSidebarFontSize(event.target.value)}
                          className="input-field settings-input settings-select"
                          data-testid="sidebar-font-size-select"
                        >
                          {FONT_SIZE_OPTIONS.map((size) => (
                            <option key={size} value={String(size)}>{size}px</option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div className="settings-preview-card" style={sidebarPreviewStyle} data-testid="sidebar-font-preview">
                      <span className="settings-preview-label">Preview</span>
                      <div className="settings-preview-lines">
                        <span>Project Alpha</span>
                        <span className="settings-preview-muted">Recent sessions and files stay comfortably readable.</span>
                      </div>
                    </div>
                  </section>

                  <section className="settings-card" data-testid="chat-font-settings-group">
                    <div className="settings-card-header">
                      <div>
                        <h3 className="settings-card-title">Chat Page Font</h3>
                        
                      </div>
                    </div>

                    <div className="settings-grid">
                      <div className="settings-field">
                        <label className="settings-label" htmlFor="chat-font-family">Font Family</label>
                        <select
                          id="chat-font-family"
                          value={chatFontFamily}
                          onChange={(event) => setChatFontFamily(event.target.value)}
                          className="input-field settings-input settings-select"
                          data-testid="chat-font-family-select"
                        >
                          {FONT_FAMILY_OPTIONS.map((option) => (
                            <option key={option.value} value={option.value}>{option.label}</option>
                          ))}
                        </select>
                      </div>

                      <div className="settings-field">
                        <label className="settings-label" htmlFor="chat-font-size">Font Size</label>
                        <select
                          id="chat-font-size"
                          value={chatFontSize}
                          onChange={(event) => setChatFontSize(event.target.value)}
                          className="input-field settings-input settings-select"
                          data-testid="chat-font-size-select"
                        >
                          {FONT_SIZE_OPTIONS.map((size) => (
                            <option key={size} value={String(size)}>{size}px</option>
                          ))}
                        </select>
                      </div>
                    </div>

                    <div className="settings-preview-card" style={chatPreviewStyle} data-testid="chat-font-preview">
                      <span className="settings-preview-label">Preview</span>
                      <div className="settings-preview-lines">
                        <span>Assistant: The latest changes are ready for review.</span>
                        <span className="settings-preview-muted">Readable conversation density makes long sessions easier to scan.</span>
                      </div>
                    </div>
                  </section>
                </section>

                <div className="settings-actions">
                  <button type="submit" className="btn btn-primary settings-save-btn" disabled={saving}>
                    {saving ? 'Saving…' : 'Save Settings'}
                  </button>
                  {saved && <span className="settings-saved-msg">Saved</span>}
                </div>
              </form>
            </section>
          )}

          {resolvedActiveTab === 'providers' && (
            <section
              role="tabpanel"
              id="settings-tabpanel-providers"
              aria-labelledby="settings-tab-providers"
              className="settings-tabpanel settings-panel-enter"
              data-testid="settings-tabpanel-providers"
            >
              {credentialsLoading && <div className="state-message">Loading providers…</div>}
              {credentialsError && <div className="state-message error">{credentialsError}</div>}

              {!credentialsLoading && !credentialsError && (
                <form onSubmit={handleCredsSave} className="settings-form" data-testid="credentials-form">
                  <section className="settings-card">
                    <div className="settings-field">
                      <label className="settings-label" htmlFor="default-provider-select">
                        Default Provider
                      </label>
                      <select
                        id="default-provider-select"
                        className="input-field settings-input settings-select"
                        value={defaultProviderId}
                        onChange={(event) => setDefaultProviderId(event.target.value)}
                        data-testid="default-provider-select"
                      >
                        {providerOptions.map((provider) => (
                          <option key={provider.id} value={provider.id}>{provider.name}</option>
                        ))}
                      </select>
                    </div>
                  </section>

                  {providers.map((provider) => (
                    <section key={provider.id} className="settings-card settings-provider-card" data-testid={`provider-card-${provider.id}`}>
                      <div className="settings-card-header settings-card-header-inline">
                        <div>
                          <h3 className="settings-card-title">{provider.name.trim() || provider.id}</h3>
                          
                        </div>
                        <button
                          type="button"
                          className="btn btn-secondary"
                          disabled={providers.length === 1 || defaultProviderId === provider.id}
                          onClick={() => handleRemoveProvider(provider.id)}
                          data-testid={`remove-provider-btn-${provider.id}`}
                        >
                          Remove
                        </button>
                      </div>

                      <div className="settings-field">
                        <label className="settings-label" htmlFor={`provider-name-${provider.id}`}>
                          Provider Name
                        </label>
                        <input
                          id={`provider-name-${provider.id}`}
                          className="input-field settings-input"
                          value={provider.name}
                          onChange={(event) => handleProviderChange(provider.id, { name: event.target.value })}
                          data-testid={`provider-name-input-${provider.id}`}
                        />
                      </div>

                      <div className="settings-field">
                        <label className="settings-label">API Key</label>
                        {!provider.showApiKeyInput ? (
                          <div className="settings-inline-row">
                            <span className="settings-api-key-masked">
                              {provider.hasApiKey ? provider.apiKeyMasked : 'No API key set'}
                            </span>
                            <button
                              type="button"
                              className="btn btn-secondary"
                              onClick={() => handleProviderChange(provider.id, { showApiKeyInput: true })}
                              data-testid={`change-api-key-btn-${provider.id}`}
                            >
                              Change
                            </button>
                          </div>
                        ) : (
                          <div className="settings-inline-row">
                            <input
                              className="input-field settings-input"
                              type="password"
                              value={provider.apiKeyInput}
                              onChange={(event) => handleProviderChange(provider.id, { apiKeyInput: event.target.value })}
                              placeholder="Enter new API key"
                              data-testid={`api-key-input-${provider.id}`}
                            />
                            <button
                              type="button"
                              className="btn btn-secondary"
                              onClick={() => handleProviderChange(provider.id, { showApiKeyInput: false, apiKeyInput: '' })}
                            >
                              Cancel
                            </button>
                          </div>
                        )}
                      </div>

                      <div className="settings-grid">
                        <div className="settings-field">
                          <label className="settings-label" htmlFor={`provider-base-url-${provider.id}`}>
                            API Base URL
                          </label>
                          <input
                            id={`provider-base-url-${provider.id}`}
                            className="input-field settings-input"
                            value={provider.apiBaseUrl}
                            onChange={(event) => handleProviderChange(provider.id, { apiBaseUrl: event.target.value })}
                            data-testid={`provider-base-url-input-${provider.id}`}
                          />
                        </div>

                        <div className="settings-field">
                          <label className="settings-label" htmlFor={`provider-models-${provider.id}`}>
                            Models
                          </label>
                          <input
                            id={`provider-models-${provider.id}`}
                            className="input-field settings-input"
                            value={provider.modelsText}
                            onChange={(event) => handleProviderChange(provider.id, { modelsText: event.target.value })}
                            placeholder="Comma-separated models"
                            data-testid={`provider-models-input-${provider.id}`}
                          />
                        </div>
                      </div>
                    </section>
                  ))}

                  <div className="settings-actions">
                    <button
                      type="button"
                      className="btn btn-secondary"
                      onClick={handleAddProvider}
                      data-testid="add-provider-btn"
                    >
                      Add Provider
                    </button>
                    <button type="submit" className="btn btn-primary settings-save-btn" disabled={credsSaving}>
                      {credsSaving ? 'Saving…' : 'Save Providers'}
                    </button>
                    {credsSaved && <span className="settings-saved-msg">Saved</span>}
                  </div>
                </form>
              )}
            </section>
          )}

          {resolvedActiveTab === 'mcp' && (
            <section
              role="tabpanel"
              id="settings-tabpanel-mcp"
              aria-labelledby="settings-tab-mcp"
              className="settings-tabpanel settings-panel-enter"
              data-testid="settings-tabpanel-mcp"
            >
              {mcpLoading && <div className="state-message">Loading MCP config…</div>}

              {!mcpLoading && (
                <form onSubmit={handleMcpSave} className="settings-form" data-testid="mcp-config-form">
                  <section className="settings-card">
                    <div className="settings-field">
                      <label className="settings-label" htmlFor="mcp-config-content">
                        MCP Configuration JSON
                      </label>
                      <textarea
                        id="mcp-config-content"
                        className="input-field settings-input settings-textarea"
                        value={mcpContent}
                        onChange={(event) => setMcpContent(event.target.value)}
                        data-testid="mcp-config-textarea"
                        spellCheck={false}
                      />
                    </div>

                    {mcpError && <div className="state-message error">{mcpError}</div>}
                  </section>

                  <div className="settings-actions">
                    <button type="submit" className="btn btn-primary settings-save-btn" disabled={mcpSaving}>
                      {mcpSaving ? 'Saving…' : 'Save MCP Config'}
                    </button>
                    {mcpSaved && <span className="settings-saved-msg">Saved</span>}
                  </div>
                </form>
              )}
            </section>
          )}
        </section>
      </div>
    </div>
  );
}

function getFontPreviewStyle(settings: Partial<WebSettings>, surface: 'sidebar' | 'chat'): React.CSSProperties {
  const fontFamily = settings.sidebarFontFamily ?? settings.chatFontFamily ?? 'sans';
  const fontSize = settings.sidebarFontSize ?? settings.chatFontSize ?? 13;
  return {
    fontFamily: fontFamily === 'mono' ? 'var(--font-mono)' : 'var(--font-sans)',
    fontSize,
    lineHeight: 1.5,
    minHeight: 80,
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
    justifyContent: 'center',
    border: '1px solid var(--border)',
    borderRadius: '12px',
    background: surface === 'sidebar'
      ? 'linear-gradient(180deg, var(--surface), color-mix(in srgb, var(--surface) 78%, var(--bg)))'
      : 'linear-gradient(180deg, color-mix(in srgb, var(--bg) 86%, var(--surface)), var(--surface))',
    color: 'var(--text)',
    padding: '12px 14px',
    boxShadow: 'var(--shadow-sm)',
  };
}
