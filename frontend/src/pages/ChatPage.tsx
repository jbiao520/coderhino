import React, { useRef, useEffect, useState, useCallback, useMemo } from 'react';
import { useParams } from 'react-router-dom';
import { useStreamingSession } from '../hooks/useStreamingSession';
import type { FileChangeSummary, RunTranscriptItem } from '../hooks/useStreamingSession';
import { useMultiProject } from '../context/MultiProjectContext';
import { useOpenFiles } from '../hooks/useOpenFiles';
import { useFileSearch } from '../hooks/useFileSearch';
import { api } from '../api/client';
import InlineToolActivityBlock from '../components/InlineToolActivityBlock';
import FileExplorer from '../components/FileExplorer';
import FileContentViewer from '../components/FileContentViewer';
import FilePanel from '../components/FilePanel';
import FileTabBar from '../components/FileTabBar';
import FileAutocomplete from '../components/FileAutocomplete';
import CommandPalette from '../components/CommandPalette';
import { Popup } from '../components/Popup';
import RichMessageContent from '../components/RichMessageContent';
import StructuredMessage from '../components/StructuredMessage';
import TerminalPanel from '../components/TerminalPanel';
import SessionContextPanel from '../components/SessionContextPanel';
import SessionGitDiffModal from '../components/SessionGitDiffModal';
import SessionGitPanel from '../components/SessionGitPanel';
import {
  ChatBubbleIcon,
  ChevronDownIcon,
  IconFrame,
  InfoIcon,
  PackageIcon,
  ServiceStatusIcon,
  SendIcon,
  StopIcon,
} from '../components/Icons';
import { useTerminalPanelState } from '../hooks/useTerminalPanelState';
import { getChatFontScopeStyle } from '../lib/webUiFontSettings';
import { getCaretCoordinates } from '../utils/caretPosition';
import type { CommandDto, FileContent, FileNode, ModelOptionDto, ProviderOptionDto, ReferenceDto, SessionContextDto, SessionGitDiffDto, SessionGitEntryDto, SessionGitStatusDto, WebSettings } from '../types/api';

type ToolbarMenu = 'intent' | 'provider-model' | 'model-mode';
type ChatPanelTabId = 'tree' | 'context' | 'git' | string;
type ReadPlaybackStatus = 'idle' | 'generating' | 'ready' | 'playing' | 'paused' | 'ended' | 'error';

interface ReadPlaybackState {
  status: ReadPlaybackStatus;
  token: string | null;
  url: string | null;
  prompt: string | null;
  output: string | null;
}

const TOOLBAR_MENU_ROW_HEIGHT = 32;
const TOOLBAR_MENU_PADDING = 16;
const REFERENCE_PAGE_SIZE = 12;
const READ_PLAYBACK_STORAGE_KEY = 'coderhino-read-playback';
const MESSAGES_AUTO_FOLLOW_THRESHOLD_PX = 48;
function createReadPlaybackState(overrides: Partial<ReadPlaybackState> = {}): ReadPlaybackState {
  return {
    status: 'idle',
    token: null,
    url: null,
    prompt: null,
    output: null,
    ...overrides,
  };
}

function loadStoredReadPlaybackState(): ReadPlaybackState {
  if (typeof window === 'undefined') {
    return createReadPlaybackState();
  }
  const raw = window.localStorage.getItem(READ_PLAYBACK_STORAGE_KEY);
  if (!raw) {
    return createReadPlaybackState();
  }
  try {
    const parsed = JSON.parse(raw) as Partial<ReadPlaybackState>;
    if (!parsed || typeof parsed !== 'object') {
      return createReadPlaybackState();
    }
    return createReadPlaybackState({
      status: parsed.status ?? 'idle',
      token: typeof parsed.token === 'string' ? parsed.token : null,
      url: typeof parsed.url === 'string' ? parsed.url : null,
      prompt: typeof parsed.prompt === 'string' ? parsed.prompt : null,
      output: typeof parsed.output === 'string' ? parsed.output : null,
    });
  } catch {
    return createReadPlaybackState();
  }
}

function normalizeReadPlaybackUrl(url: string): string {
  if (typeof window === 'undefined') {
    return url;
  }
  try {
    return new URL(url, window.location.origin).toString();
  } catch {
    return url;
  }
}

function normalizeModelMode(
  availableModes: string[] | undefined,
  current: string | null | undefined,
): string | null {
  const modes = availableModes ?? [];
  if (modes.length === 0) {
    return null;
  }
  if (current && modes.includes(current)) {
    return current;
  }
  return modes.includes('default') ? 'default' : (modes[0] ?? null);
}

function fallbackModelOptions(
  models: string[] | undefined,
  sessionModel: string | undefined,
  sessionModes: string[] | undefined,
): ModelOptionDto[] {
  return (models ?? []).map((model) => {
    const availableModelModes = model === sessionModel ? (sessionModes ?? []) : [];
    return {
      id: model,
      label: model,
      modelModeSupported: availableModelModes.length > 0,
      availableModelModes,
    };
  });
}

function shouldShowTranscriptItem(item: RunTranscriptItem, showThinkingDetails: boolean, showToolExecutions: boolean): boolean {
  if (item.kind === 'thinking' && !showThinkingDetails) {
    return false;
  }
  if ((item.kind === 'tool-input' || item.kind === 'tool') && !showToolExecutions) {
    return false;
  }
  return true;
}

function renderInlineTranscriptItem(item: RunTranscriptItem, idx: number, prefix: string, isLiveTranscript = false) {
  if (item.kind === 'assistant') {
    return (
      <div
        key={`${prefix}-text-${idx}`}
        style={styles.assistantMessageItem}
        data-testid={isLiveTranscript ? 'live-output' : `${prefix}-text-${idx}`}
      >
        <StructuredMessage text={item.content} showCursor={isLiveTranscript} />
      </div>
    );
  }
  const testId = prefix === 'thinking-block'
    ? `thinking-block-${idx}`
    : prefix === 'status-block'
      ? `status-block-${idx}`
      : prefix === 'tool-input-block'
        ? `tool-input-block-${idx}`
        : prefix === 'inline-tool-block'
          ? `inline-tool-block-${idx}`
          : `${prefix}-${item.kind}-${idx}`;
  if (item.kind === 'thinking') {
    return (
      <div key={`${prefix}-thinking-${idx}`} style={styles.inlineProgressItem} data-testid={testId}>
        <div style={styles.inlineProgressCard}>
          <div style={styles.inlineProgressHeader}>
            <span style={styles.inlineProgressIcon}><IconFrame><InfoIcon size={12} /></IconFrame></span>
            <span style={styles.inlineProgressLabel}>Thinking</span>
            <span style={styles.inlineProgressTag}>Model</span>
          </div>
          <div style={styles.inlineProgressBody}>{item.content}</div>
        </div>
      </div>
    );
  }
  if (item.kind === 'status') {
    return (
      <div key={`${prefix}-status-${idx}`} style={styles.inlineProgressItem} data-testid={testId}>
        <div style={styles.inlineProgressCard}>
          <div style={styles.inlineProgressHeader}>
            <span style={styles.inlineProgressIcon}><IconFrame><InfoIcon size={12} /></IconFrame></span>
            <span style={styles.inlineProgressLabel}>Retrying</span>
            <span style={styles.inlineProgressTag}>Request</span>
          </div>
          <div style={styles.inlineProgressBody}>{item.content}</div>
        </div>
      </div>
    );
  }
  if (item.kind === 'tool-input') {
    return (
      <div key={`${prefix}-tool-input-${idx}`} style={styles.inlineProgressItem} data-testid={testId}>
        <div style={styles.inlineProgressCard}>
          <div style={styles.inlineProgressHeader}>
            <span style={styles.inlineProgressIcon}><IconFrame><PackageIcon size={12} /></IconFrame></span>
            <span style={styles.inlineProgressLabel}>Preparing {item.toolName}</span>
            <span style={styles.inlineProgressTag}>Input</span>
          </div>
          <pre style={styles.inlineProgressCode}>{item.partialJson}</pre>
        </div>
      </div>
    );
  }
  return (
    <div key={`${prefix}-tool-${idx}`} style={styles.inlineToolItem}>
      <InlineToolActivityBlock tool={item} testId={testId} />
    </div>
  );
}

function renderFileSummaryBox(summary: FileChangeSummary, testId: string) {
  return (
    <div style={styles.fileSummaryBox} data-testid={testId}>
      <div style={styles.fileSummaryTitle}>
        Session Summary - {summary.totalChanges} file{summary.totalChanges !== 1 ? 's' : ''} changed
      </div>
      {summary.created.length > 0 && (
        <div style={styles.fileSummaryRow}>
          <span style={styles.fileSummaryLabel}>Created ({summary.created.length}):</span>{' '}
          {summary.created.join(', ')}
        </div>
      )}
      {summary.modified.length > 0 && (
        <div style={styles.fileSummaryRow}>
          <span style={styles.fileSummaryLabel}>Modified ({summary.modified.length}):</span>{' '}
          {summary.modified.join(', ')}
        </div>
      )}
      {summary.deleted.length > 0 && (
        <div style={styles.fileSummaryRow}>
          <span style={styles.fileSummaryLabel}>Deleted ({summary.deleted.length}):</span>{' '}
          {summary.deleted.join(', ')}
        </div>
      )}
    </div>
  );
}

function formatModeLabel(mode: string): string {
  if (!mode) {
    return 'Default';
  }
  return mode.charAt(0).toUpperCase() + mode.slice(1);
}

function toTestIdFragment(value: string): string {
  return value.replace(/[^a-z0-9]+/gi, '-').replace(/^-+|-+$/g, '').toLowerCase();
}

function formatMessageTimestamp(timestamp: string | undefined): string {
  if (!timestamp) {
    return 'Unknown time';
  }
  const parsed = new Date(timestamp);
  if (Number.isNaN(parsed.getTime())) {
    return 'Unknown time';
  }
  return parsed.toLocaleString();
}

function isMessagesViewportNearBottom(element: HTMLElement): boolean {
  return element.scrollHeight - element.scrollTop - element.clientHeight <= MESSAGES_AUTO_FOLLOW_THRESHOLD_PX;
}

function PlainMessageContent({ text, testId }: { text: string; testId?: string }) {
  return (
    <div style={styles.plainMessageText} data-testid={testId}>
      {text}
    </div>
  );
}

function parseSlashCommandInput(input: string): { commandName: string; args: string[] } | null {
  const trimmed = input.trim();
  if (!trimmed.startsWith('/')) {
    return null;
  }
  const commandInput = trimmed.slice(1).trim();
  if (!commandInput) {
    return null;
  }
  const tokens = commandInput.split(/\s+/);
  const commandName = tokens[0] ?? '';
  if (!commandName) {
    return null;
  }
  return {
    commandName,
    args: tokens.slice(1),
  };
}

function formatSlashCommandPrompt(commandName: string, args: string[]): string {
  const joinedArgs = args.join(' ');
  return (`/${commandName} ${joinedArgs}`).trim();
}

async function copyMessageText(text: string): Promise<void> {
  if (navigator.clipboard?.writeText) {
    await navigator.clipboard.writeText(text);
    return;
  }

  const textarea = document.createElement('textarea');
  textarea.value = text;
  textarea.setAttribute('readonly', 'true');
  textarea.style.position = 'fixed';
  textarea.style.opacity = '0';
  document.body.appendChild(textarea);
  textarea.select();
  const copied = document.execCommand('copy');
  document.body.removeChild(textarea);
  if (!copied) {
    throw new Error('Copy is not supported in this browser context');
  }
}

interface ChatPageProps {
  fileExplorerOpen?: boolean;
  fileExplorerToggleVersion?: number;
  onFileExplorerVisibilityChange?: (visible: boolean) => void;
  terminalPanelOpen?: boolean;
  onTerminalPanelVisibilityChange?: (visible: boolean) => void;
  settings?: WebSettings | null;
}

export default function ChatPage({
  fileExplorerOpen = false,
  fileExplorerToggleVersion = 0,
  onFileExplorerVisibilityChange,
  terminalPanelOpen = false,
  onTerminalPanelVisibilityChange,
  settings,
}: ChatPageProps) {
  const { id, projectId } = useParams<{ id: string; projectId: string }>();
  const { projects, openProjectIds, getActiveProjectForSession, getSessionById, openProject, setActiveProject, setActiveSession } = useMultiProject();
  const activeProject = id ? getActiveProjectForSession(id) : (projectId ? projects[projectId] ?? null : null);
  const knownSession = id ? getSessionById(id) : null;

  const {
    session,
    loading,
    error,
    messages,
    runTranscript,
    activeRetryStatus,
    activeRun,
    runStatus,
    submitMessage,
    cancelRun,
    cancelingRun,
    rollbackToMessage,
    appendMessage,
    refreshSession,
    fileSummary,
    pendingQuestion,
    answerPendingQuestion,
  } = useStreamingSession(id);

  const projectPath = session?.worktree?.path ?? knownSession?.worktree?.path ?? activeProject?.path ?? '/tmp';
  const activeWorktreeId = session?.worktreeId ?? knownSession?.worktreeId ?? activeProject?.worktrees?.[0]?.id ?? null;

  const [inputValue, setInputValue] = useState('');
  const [submittedInputHistory, setSubmittedInputHistory] = useState<string[]>([]);
  const [activeHistoryIndex, setActiveHistoryIndex] = useState<number | null>(null);
  const [preservedHistoryDraft, setPreservedHistoryDraft] = useState<string | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [activeMessageActionId, setActiveMessageActionId] = useState<string | null>(null);
  const [commands, setCommands] = useState<CommandDto[]>([]);
  const [references, setReferences] = useState<ReferenceDto[]>([]);
  const [referenceBrowserOpen, setReferenceBrowserOpen] = useState(false);
  const [referenceSearchQuery, setReferenceSearchQuery] = useState('');
  const [referencePage, setReferencePage] = useState(0);
  const [referencePreviewId, setReferencePreviewId] = useState<string | null>(null);
  const [paletteOpen, setPaletteOpen] = useState(false);
  const [paletteQuery, setPaletteQuery] = useState('');
  const [editingName, setEditingName] = useState(false);
  const [nameInput, setNameInput] = useState('');
  const [sessionName, setSessionName] = useState<string | null>(null);
  const [selectedProviderId, setSelectedProviderId] = useState<string | null>(null);
  const [selectedModel, setSelectedModel] = useState<string | null>(null);
  const [planMode, setPlanMode] = useState(false);
  const [selectedModelMode, setSelectedModelMode] = useState<string | null>(null);
  const [openToolbarMenu, setOpenToolbarMenu] = useState<ToolbarMenu | null>(null);
  const [openToolbarMenuAbove, setOpenToolbarMenuAbove] = useState(false);
  const [providerMenuProviderId, setProviderMenuProviderId] = useState<string | null>(null);
  const [contextTabOpen, setContextTabOpen] = useState(false);
  const [gitTabOpen, setGitTabOpen] = useState(false);
  const [filePanelVisible, setFilePanelVisible] = useState(fileExplorerOpen);
  const [contextCache, setContextCache] = useState<Record<string, SessionContextDto>>({});
  const [contextLoading, setContextLoading] = useState(false);
  const [contextError, setContextError] = useState<string | null>(null);
  const [gitCache, setGitCache] = useState<Record<string, SessionGitStatusDto>>({});
  const [gitLoading, setGitLoading] = useState(false);
  const [gitError, setGitError] = useState<string | null>(null);
  const [gitDiff, setGitDiff] = useState<SessionGitDiffDto | null>(null);
  const [gitDiffFileContent, setGitDiffFileContent] = useState<FileContent | null>(null);
  const [gitDiffFileContentCompare, setGitDiffFileContentCompare] = useState<{ previousContent: string | null; currentContent: string | null } | null>(null);
  const [gitDiffView, setGitDiffView] = useState<'diff' | 'file' | 'full-file-compare'>('diff');
  const [gitDiffLoading, setGitDiffLoading] = useState(false);
  const [gitDiffError, setGitDiffError] = useState<string | null>(null);
  const [gitDiffOpen, setGitDiffOpen] = useState(false);
  const [readPlayback, setReadPlayback] = useState<ReadPlaybackState>(() => loadStoredReadPlaybackState());
  const [pendingQuestionChoice, setPendingQuestionChoice] = useState<string>('');
  const [pendingQuestionCustomAnswer, setPendingQuestionCustomAnswer] = useState('');
  const [showThinkingDetails, setShowThinkingDetails] = useState(true);
  const [showToolExecutions, setShowToolExecutions] = useState(true);
  const [lastNonContextTabId, setLastNonContextTabId] = useState<ChatPanelTabId>('tree');
  const [shouldAutoFollowMessages, setShouldAutoFollowMessages] = useState(true);
  const lastFileExplorerToggleVersionRef = useRef(fileExplorerToggleVersion);
  const messagesAreaRef = useRef<HTMLDivElement | null>(null);
  const messagesEndRef = useRef<HTMLDivElement | null>(null);
  const nameInputRef = useRef<HTMLInputElement | null>(null);
  const textareaRef = useRef<HTMLTextAreaElement | null>(null);
  const toolbarRef = useRef<HTMLDivElement | null>(null);
  const readAudioRef = useRef<HTMLAudioElement | null>(null);
  const creatingInitialTerminalRef = useRef(false);
  const lastReportedTerminalVisibleRef = useRef<boolean | null>(null);
  const syncingTerminalVisibilityRef = useRef(false);
  const isRunning = activeRun !== null;
  const composerActionDisabled = isRunning ? cancelingRun : !inputValue.trim();
  const isWaitingForUser = activeRun?.status === 'WAITING_FOR_USER' || pendingQuestion !== null;
  const isReadCommandGenerating = readPlayback.status === 'generating';
  const showReadPlaybackControls = Boolean(readPlayback.url)
    && ['ready', 'playing', 'paused', 'ended', 'error'].includes(readPlayback.status);

  const [atTriggerIndex, setAtTriggerIndex] = useState<number | null>(null);
  const [autocompleteVisible, setAutocompleteVisible] = useState(false);
  const [autocompleteSelectedIndex, setAutocompleteSelectedIndex] = useState(-1);
  const [caretPosition, setCaretPosition] = useState<{ top: number; left: number; lineHeight: number } | null>(null);
  const { search: fileSearch, loading: fileSearchLoading, ensureLoaded } = useFileSearch(projectPath);
  const terminalPanel = useTerminalPanelState(id);
  const {
    visible: terminalVisible,
    terminals: terminalTabs,
    selectedTerminalId,
    setVisible: setTerminalVisible,
    createTerminal,
    closeTerminal,
    selectTerminal,
    replaceTerminal,
    restoring: restoringTerminalState,
  } = terminalPanel;

  useEffect(() => {
    syncingTerminalVisibilityRef.current = true;
    lastReportedTerminalVisibleRef.current = terminalPanelOpen;
    setTerminalVisible(terminalPanelOpen);
  }, [setTerminalVisible, terminalPanelOpen]);

  useEffect(() => {
    if (typeof window === 'undefined') {
      return;
    }
    if (!readPlayback.token || !readPlayback.url) {
      window.localStorage.removeItem(READ_PLAYBACK_STORAGE_KEY);
      return;
    }
    window.localStorage.setItem(READ_PLAYBACK_STORAGE_KEY, JSON.stringify(readPlayback));
  }, [readPlayback]);

  useEffect(() => {
    if (!readPlayback.url || readPlayback.status === 'generating') {
      if (readPlayback.status === 'generating' && readAudioRef.current) {
        readAudioRef.current.pause();
        readAudioRef.current.currentTime = 0;
      }
      return;
    }

    const normalizedUrl = normalizeReadPlaybackUrl(readPlayback.url);
    const previousAudio = readAudioRef.current;
    const audio = previousAudio?.src === normalizedUrl
      ? previousAudio
      : new Audio(normalizedUrl);
    if (!audio) {
      return;
    }
    if (previousAudio && previousAudio !== audio) {
      previousAudio.pause();
      previousAudio.currentTime = 0;
    }
    readAudioRef.current = audio;

    const handlePlaying = () => setReadPlayback((current) => (
      current.url === readPlayback.url ? { ...current, status: 'playing' } : current
    ));
    const handlePause = () => setReadPlayback((current) => {
      if (current.url !== readPlayback.url || audio.ended) {
        return current;
      }
      return { ...current, status: 'paused' };
    });
    const handleEnded = () => setReadPlayback((current) => (
      current.url === readPlayback.url ? { ...current, status: 'ended' } : current
    ));
    const handleError = () => setReadPlayback((current) => (
      current.url === readPlayback.url ? { ...current, status: 'error' } : current
    ));

    audio.addEventListener('playing', handlePlaying);
    audio.addEventListener('pause', handlePause);
    audio.addEventListener('ended', handleEnded);
    audio.addEventListener('error', handleError);

    if (readPlayback.status === 'playing') {
      void audio.play().catch(() => {
        setReadPlayback((current) => (
          current.url === readPlayback.url ? { ...current, status: 'ready' } : current
        ));
      });
    }

    return () => {
      audio.removeEventListener('playing', handlePlaying);
      audio.removeEventListener('pause', handlePause);
      audio.removeEventListener('ended', handleEnded);
      audio.removeEventListener('error', handleError);
    };
  }, [readPlayback.status, readPlayback.url]);

  useEffect(() => () => {
    readAudioRef.current?.pause();
  }, []);

  useEffect(() => {
    if (syncingTerminalVisibilityRef.current) {
      syncingTerminalVisibilityRef.current = false;
      return;
    }
    if (lastReportedTerminalVisibleRef.current === terminalVisible) {
      return;
    }
    lastReportedTerminalVisibleRef.current = terminalVisible;
    onTerminalPanelVisibilityChange?.(terminalVisible);
  }, [onTerminalPanelVisibilityChange, terminalVisible]);

  useEffect(() => {
    if (!terminalVisible || restoringTerminalState || terminalTabs.length > 0 || !id) {
      creatingInitialTerminalRef.current = false;
      return;
    }
    if (creatingInitialTerminalRef.current) {
      return;
    }
    creatingInitialTerminalRef.current = true;
    void createTerminal(activeWorktreeId).finally(() => {
      creatingInitialTerminalRef.current = false;
    });
  }, [activeWorktreeId, createTerminal, id, restoringTerminalState, terminalTabs.length, terminalVisible]);

  useEffect(() => {
    let cancelled = false;
    api.commands.list()
      .then((data) => {
        if (!cancelled) {
          setCommands(data);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setCommands([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    let cancelled = false;
    api.references.list()
      .then((data) => {
        if (!cancelled) {
          setReferences(data.references ?? []);
        }
      })
      .catch(() => {
        if (!cancelled) {
          setReferences([]);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (session?.name !== undefined) {
      setSessionName(session.name);
    }
  }, [session?.name]);

  useEffect(() => {
    setSubmittedInputHistory([]);
    setActiveHistoryIndex(null);
    setPreservedHistoryDraft(null);
  }, [id]);

  useEffect(() => {
    setShouldAutoFollowMessages(true);
  }, [id]);

  useEffect(() => {
    setPendingQuestionChoice('');
    setPendingQuestionCustomAnswer('');
  }, [pendingQuestion?.toolUseId]);

  useEffect(() => {
    if (!session) {
      return;
    }
    setSelectedProviderId(session.providerId ?? session.availableProviders?.[0]?.id ?? null);
    setSelectedModel(session.model ?? null);
    setPlanMode(Boolean(session.planMode));
    setSelectedModelMode(session.modelMode ?? 'default');
    setOpenToolbarMenu(null);
    setProviderMenuProviderId(session.providerId ?? session.availableProviders?.[0]?.id ?? null);
  }, [session]);

  useEffect(() => {
    if (openToolbarMenu == null) {
      return undefined;
    }
    const handlePointerDown = (event: MouseEvent) => {
      if (toolbarRef.current && !toolbarRef.current.contains(event.target as Node)) {
        setOpenToolbarMenu(null);
      }
    };
    document.addEventListener('mousedown', handlePointerDown);
    return () => document.removeEventListener('mousedown', handlePointerDown);
  }, [openToolbarMenu]);

  const selectedProvider = useMemo(() => (
    (session?.availableProviders ?? []).find((provider) => provider.id === selectedProviderId) ?? null
  ), [session?.availableProviders, selectedProviderId]);

  const selectedProviderModelOptions = useMemo(() => {
    if (!selectedProvider) {
      return fallbackModelOptions(session?.availableModels, session?.model, session?.availableModelModes);
    }
    if (selectedProvider.modelOptions && selectedProvider.modelOptions.length > 0) {
      return selectedProvider.modelOptions;
    }
    const fallbackModels = selectedProvider.models && selectedProvider.models.length > 0
      ? selectedProvider.models
      : session?.availableModels;
    return fallbackModelOptions(fallbackModels, session?.model, session?.availableModelModes);
  }, [selectedProvider, session?.availableModelModes, session?.availableModels, session?.model]);

  const selectedModelOption = useMemo(() => (
    selectedProviderModelOptions.find((option) => option.id === selectedModel)
    ?? selectedProviderModelOptions[0]
    ?? null
  ), [selectedModel, selectedProviderModelOptions]);

  const modelModeOptions = selectedModelOption?.availableModelModes ?? [];
  const modelModeSupported = modelModeOptions.length > 0;

  const providerMenuProvider = useMemo(() => (
    (session?.availableProviders ?? []).find((provider) => provider.id === providerMenuProviderId)
    ?? selectedProvider
    ?? session?.availableProviders?.[0]
    ?? null
  ), [providerMenuProviderId, selectedProvider, session?.availableProviders]);

  const providerMenuModelOptions = useMemo(() => {
    if (!providerMenuProvider) {
      return [] as ModelOptionDto[];
    }
    if (providerMenuProvider.modelOptions && providerMenuProvider.modelOptions.length > 0) {
      return providerMenuProvider.modelOptions;
    }
    return fallbackModelOptions(providerMenuProvider.models, session?.model, session?.availableModelModes);
  }, [providerMenuProvider, session?.availableModelModes, session?.model]);

  const normalizedReferenceSearchQuery = referenceSearchQuery.trim().toLowerCase();
  const filteredReferences = useMemo(() => {
    if (normalizedReferenceSearchQuery.length === 0) {
      return references;
    }
    return references.filter((reference) =>
      reference.label.toLowerCase().includes(normalizedReferenceSearchQuery)
      || reference.filename.toLowerCase().includes(normalizedReferenceSearchQuery)
      || (reference.source?.toLowerCase().includes(normalizedReferenceSearchQuery) ?? false));
  }, [normalizedReferenceSearchQuery, references]);
  const referencePageCount = Math.max(1, Math.ceil(filteredReferences.length / REFERENCE_PAGE_SIZE));
  const visibleReferences = useMemo(() => {
    const start = referencePage * REFERENCE_PAGE_SIZE;
    return filteredReferences.slice(start, start + REFERENCE_PAGE_SIZE);
  }, [filteredReferences, referencePage]);
  const referencePreview = useMemo(() => (
    references.find((reference) => reference.id === referencePreviewId) ?? null
  ), [referencePreviewId, references]);

  useEffect(() => {
    if (!selectedProviderId && session?.availableProviders && session.availableProviders.length > 0) {
      setSelectedProviderId(session.availableProviders[0]!.id);
    }
  }, [selectedProviderId, session?.availableProviders]);

  useEffect(() => {
    if (selectedProviderModelOptions.length === 0) {
      return;
    }
    if (!selectedProviderModelOptions.some((option) => option.id === selectedModel)) {
      setSelectedModel(selectedProviderModelOptions[0]!.id);
    }
  }, [selectedModel, selectedProviderModelOptions]);

  useEffect(() => {
    setSelectedModelMode((current) =>
      normalizeModelMode(modelModeOptions, current),
    );
  }, [modelModeOptions]);

  useEffect(() => {
    if (providerMenuProviderId == null && session?.availableProviders && session.availableProviders.length > 0) {
      setProviderMenuProviderId(selectedProviderId ?? session.availableProviders[0]!.id);
    }
  }, [providerMenuProviderId, selectedProviderId, session?.availableProviders]);

  useEffect(() => {
    setReferencePage(0);
  }, [normalizedReferenceSearchQuery]);

  useEffect(() => {
    setReferencePage((current) => Math.min(current, referencePageCount - 1));
  }, [referencePageCount]);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    if (openProjectIds.includes(projectId)) {
      return;
    }
    const routedProject = projects[projectId];
    if (routedProject) {
      openProject(routedProject);
      return;
    }
    let cancelled = false;
    api.projects.get(projectId).then((project) => {
      if (cancelled) {
        return;
      }
      openProject(project);
    }).catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [projectId, openProjectIds, projects, openProject]);

  useEffect(() => {
    if (!projectId) {
      return;
    }
    setActiveProject(projectId);
    if (id) {
      setActiveSession(projectId, id);
    }
  }, [projectId, id, setActiveProject, setActiveSession]);

  const handleStartRename = useCallback(() => {
    if (!session) return;
    setNameInput(sessionName || '');
    setEditingName(true);
    setTimeout(() => nameInputRef.current?.focus(), 0);
  }, [session, sessionName]);

  const handleFinishRename = useCallback(async () => {
    setEditingName(false);
    if (!session || !nameInput.trim()) return;
    try {
      const updated = await api.sessions.rename(session.sessionId, nameInput.trim());
      setSessionName(updated.name ?? nameInput.trim());
    } catch {
      setSessionName(nameInput.trim());
    }
  }, [session, nameInput]);

  const handleCancelRename = useCallback(() => {
    setEditingName(false);
  }, []);

  const autocompleteItems = atTriggerIndex !== null && autocompleteVisible
    ? fileSearch(inputValue.slice(atTriggerIndex + 1, inputValue.length))
    : [];

  const dismissAutocomplete = useCallback(() => {
    setAtTriggerIndex(null);
    setAutocompleteVisible(false);
    setAutocompleteSelectedIndex(-1);
    setCaretPosition(null);
  }, []);

  const focusTextareaAtEnd = useCallback((value: string) => {
    setTimeout(() => {
      if (textareaRef.current) {
        const cursorPos = value.length;
        textareaRef.current.focus();
        textareaRef.current.setSelectionRange(cursorPos, cursorPos);
      }
    }, 0);
  }, []);

  const resetHistoryNavigation = useCallback(() => {
    setActiveHistoryIndex(null);
    setPreservedHistoryDraft(null);
  }, []);

  const recordSubmittedInput = useCallback((value: string) => {
    const nextValue = value.trim();
    if (!nextValue) {
      return;
    }
    setSubmittedInputHistory((current) => [nextValue, ...current]);
    resetHistoryNavigation();
  }, [resetHistoryNavigation]);

  const navigateSubmittedInputHistory = useCallback((direction: 'older' | 'newer') => {
    if (submittedInputHistory.length === 0) {
      return false;
    }

    if (direction === 'older') {
      if (activeHistoryIndex === null) {
        const nextValue = submittedInputHistory[0]!;
        setPreservedHistoryDraft(inputValue);
        setActiveHistoryIndex(0);
        setInputValue(nextValue);
        focusTextareaAtEnd(nextValue);
        return true;
      }

      const nextIndex = Math.min(activeHistoryIndex + 1, submittedInputHistory.length - 1);
      const nextValue = submittedInputHistory[nextIndex];
      if (!nextValue) {
        return false;
      }
      setActiveHistoryIndex(nextIndex);
      setInputValue(nextValue);
      focusTextareaAtEnd(nextValue);
      return true;
    }

    if (activeHistoryIndex === null) {
      return false;
    }

    const nextIndex = activeHistoryIndex - 1;
    if (nextIndex >= 0) {
      const nextValue = submittedInputHistory[nextIndex];
      if (!nextValue) {
        return false;
      }
      setActiveHistoryIndex(nextIndex);
      setInputValue(nextValue);
      focusTextareaAtEnd(nextValue);
      return true;
    }

    const restoredDraft = preservedHistoryDraft ?? '';
    setActiveHistoryIndex(null);
    setPreservedHistoryDraft(null);
    setInputValue(restoredDraft);
    focusTextareaAtEnd(restoredDraft);
    return true;
  }, [activeHistoryIndex, focusTextareaAtEnd, inputValue, preservedHistoryDraft, submittedInputHistory]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLTextAreaElement>) => {
      const value = e.target.value;
      const cursorPos = e.target.selectionStart ?? value.length;
      if (activeHistoryIndex !== null) {
        setActiveHistoryIndex(null);
        setPreservedHistoryDraft(null);
      }
      setInputValue(value);

      if (value.startsWith('/')) {
        const commandQuery = value.slice(1, cursorPos);
        if (!commandQuery.includes(' ') && !commandQuery.includes('\n')) {
          setPaletteOpen(true);
          setPaletteQuery(commandQuery);
          dismissAutocomplete();
          return;
        }
      }

      setPaletteOpen(false);
      setPaletteQuery('');

      const atIdx = value.lastIndexOf('@', cursorPos - 1);
      if (atIdx >= 0) {
        const charBefore = atIdx === 0 ? ' ' : value[atIdx - 1];
        if (charBefore === ' ' || charBefore === '\n') {
          const query = value.slice(atIdx + 1, cursorPos);
          if (!query.includes(' ') && !query.includes('\n')) {
            if (!autocompleteVisible) {
              void ensureLoaded();
            }
            setAtTriggerIndex(atIdx);
            setAutocompleteVisible(true);
            setAutocompleteSelectedIndex(-1);
            if (textareaRef.current) {
              const coords = getCaretCoordinates(textareaRef.current, atIdx);
              setCaretPosition(coords);
            }
            return;
          }
        }
      }

      setAtTriggerIndex(null);
      setAutocompleteVisible(false);
      setAutocompleteSelectedIndex(-1);
      setCaretPosition(null);
    },
    [activeHistoryIndex, autocompleteVisible, dismissAutocomplete, ensureLoaded],
  );

  const handleCommandDismiss = useCallback(() => {
    setPaletteOpen(false);
    setPaletteQuery('');
    setInputValue((current) => (current.startsWith('/') ? current.slice(1) : current));
  }, []);

  const handleCommandSelect = useCallback(
    (command: CommandDto) => {
      const nextValue = `/${command.name} `;
      setPaletteOpen(false);
      setPaletteQuery('');
      setInputValue(nextValue);
      setSubmitError(null);
      setTimeout(() => {
        if (textareaRef.current) {
          const cursorPos = nextValue.length;
          textareaRef.current.focus();
          textareaRef.current.setSelectionRange(cursorPos, cursorPos);
        }
      }, 0);
    },
    [],
  );

  const insertTextAtSelection = useCallback((text: string) => {
    const textarea = textareaRef.current;
    const selectionStart = textarea?.selectionStart ?? inputValue.length;
    const selectionEnd = textarea?.selectionEnd ?? inputValue.length;
    const nextValue = `${inputValue.slice(0, selectionStart)}${text}${inputValue.slice(selectionEnd)}`;
    const nextCursor = selectionStart + text.length;

    setInputValue(nextValue);
    setSubmitError(null);
    setOpenToolbarMenu(null);
    dismissAutocomplete();
    setPaletteOpen(false);
    setPaletteQuery('');

    setTimeout(() => {
      if (textareaRef.current) {
        textareaRef.current.focus();
        textareaRef.current.setSelectionRange(nextCursor, nextCursor);
      }
    }, 0);
  }, [dismissAutocomplete, inputValue]);

  const handleReferenceSelect = useCallback((reference: ReferenceDto) => {
    setReferencePreviewId(null);
    setReferenceBrowserOpen(false);
    insertTextAtSelection(reference.markdown);
  }, [insertTextAtSelection]);

  const handleOpenReferenceBrowser = useCallback(() => {
    setReferenceSearchQuery('');
    setReferencePage(0);
    setReferencePreviewId(null);
    setReferenceBrowserOpen(true);
    setOpenToolbarMenu(null);
    dismissAutocomplete();
    setPaletteOpen(false);
    setPaletteQuery('');
  }, [dismissAutocomplete]);

  const handleCloseReferenceBrowser = useCallback(() => {
    setReferenceBrowserOpen(false);
    setReferencePreviewId(null);
  }, []);

  const handleOpenReferencePreview = useCallback((referenceId: string) => {
    setReferencePreviewId(referenceId);
  }, []);

  const handleCloseReferencePreview = useCallback(() => {
    setReferencePreviewId(null);
  }, []);

  const releaseReadAudio = useCallback(async (token: string | null) => {
    if (!token) {
      return;
    }
    try {
      await api.commands.releaseAudio(token);
    } catch {
    }
  }, []);

  const handleReadPlaybackPlay = useCallback(async () => {
    const audio = readAudioRef.current;
    if (!audio) {
      return;
    }
    try {
      await audio.play();
      setReadPlayback((current) => ({ ...current, status: 'playing' }));
    } catch {
      setReadPlayback((current) => ({ ...current, status: 'ready' }));
    }
  }, []);

  const handleReadPlaybackPause = useCallback(() => {
    readAudioRef.current?.pause();
    setReadPlayback((current) => ({ ...current, status: 'paused' }));
  }, []);

  const handleReadPlaybackStop = useCallback(() => {
    const token = readPlayback.token;
    const audio = readAudioRef.current;
    if (audio) {
      audio.pause();
      audio.currentTime = 0;
    }
    readAudioRef.current = null;
    setReadPlayback(createReadPlaybackState());
    void releaseReadAudio(token);
  }, [readPlayback.token, releaseReadAudio]);

  useEffect(() => {
    if (!id || !readPlayback.token || !readPlayback.url) {
      return;
    }
    void fetch(readPlayback.url, { method: 'HEAD' }).then((response) => {
      if (response.ok) {
        return;
      }
      setReadPlayback(createReadPlaybackState());
    }).catch(() => {
      setReadPlayback(createReadPlaybackState());
    });
  }, [id, readPlayback.token, readPlayback.url]);

  const handleAutocompleteSelect = useCallback(
    (item: FileNode) => {
      if (atTriggerIndex === null) return;
      const before = inputValue.slice(0, atTriggerIndex);
      const after = inputValue.slice(inputValue.length);
      const newValue = `${before}@${item.path}${after}`;
      setInputValue(newValue);
      setAtTriggerIndex(null);
      setAutocompleteVisible(false);
      setAutocompleteSelectedIndex(-1);
      setCaretPosition(null);
      setTimeout(() => {
        if (textareaRef.current) {
          const cursorPos = before.length + item.path.length + 1;
          textareaRef.current.focus();
          textareaRef.current.setSelectionRange(cursorPos, cursorPos);
        }
      }, 0);
    },
    [atTriggerIndex, inputValue],
  );

  const handleAutocompleteHover = useCallback(
    (index: number) => {
      setAutocompleteSelectedIndex(index);
    },
    [],
  );

  const { openFiles, activeTabId, openFile, closeTab, setActiveTab, getActiveFile } = useOpenFiles(projectPath);

  useEffect(() => {
    setFilePanelVisible(fileExplorerOpen);
  }, [fileExplorerOpen]);

  useEffect(() => {
    if (lastFileExplorerToggleVersionRef.current === fileExplorerToggleVersion) {
      return;
    }
    lastFileExplorerToggleVersionRef.current = fileExplorerToggleVersion;

    if (filePanelVisible && activeTabId !== 'context' && activeTabId !== 'git') {
      setFilePanelVisible(false);
      setContextTabOpen(false);
      setGitTabOpen(false);
      setContextError(null);
      setGitError(null);
      onFileExplorerVisibilityChange?.(false);
      return;
    }

    setFilePanelVisible(true);
    if (activeTabId === 'context' || activeTabId === 'git') {
      setActiveTab('tree');
    }
    onFileExplorerVisibilityChange?.(true);
  }, [activeTabId, fileExplorerToggleVersion, filePanelVisible, onFileExplorerVisibilityChange, setActiveTab]);

  useEffect(() => {
    if (activeTabId !== 'context' && activeTabId !== 'git') {
      setLastNonContextTabId(activeTabId);
    }
  }, [activeTabId]);

  const currentRunText = useMemo(() => runTranscript
    .filter((item): item is { kind: 'assistant'; content: string } => item.kind === 'assistant')
    .map((item) => item.content)
    .join(''), [runTranscript]);
  const visibleRunTranscript = useMemo(() => runTranscript.flatMap((item, originalIndex) => {
    if (item.kind === 'thinking' && !showThinkingDetails) {
      return [];
    }
    if ((item.kind === 'tool-input' || item.kind === 'tool') && !showToolExecutions) {
      return [];
    }
    return [{ item, originalIndex }];
  }), [runTranscript, showThinkingDetails, showToolExecutions]);
  const showRunStartPlaceholder = isRunning && visibleRunTranscript.length === 0;
  const pendingQuestionRequiresCustomAnswer = !pendingQuestion || pendingQuestion.choices.length === 0 || pendingQuestionChoice === '__custom__';
  const pendingQuestionAnswer = pendingQuestionRequiresCustomAnswer
    ? pendingQuestionCustomAnswer.trim()
    : pendingQuestionChoice;

  const visibleMessages = useMemo(() => {
    if (runStatus !== 'completed' || runTranscript.length === 0 || messages.length === 0) {
      return messages;
    }

    const lastMessage = messages[messages.length - 1];
    const hasPersistedActivity = Boolean(lastMessage?.activityTimeline?.length)
      || Boolean(lastMessage?.fileSummary && lastMessage.fileSummary.totalChanges > 0);
    if (lastMessage?.role === 'assistant' && lastMessage.content === currentRunText && !hasPersistedActivity) {
      return messages.slice(0, -1);
    }

    return messages;
  }, [currentRunText, messages, runStatus, runTranscript.length]);

  const handleMessagesAreaScroll = useCallback(() => {
    const container = messagesAreaRef.current;
    if (!container) {
      return;
    }
    setShouldAutoFollowMessages((current) => {
      const next = isMessagesViewportNearBottom(container);
      return current === next ? current : next;
    });
  }, []);

  useEffect(() => {
    if (!shouldAutoFollowMessages) {
      return;
    }
    messagesEndRef.current?.scrollIntoView({ block: 'end' });
  }, [messages, runTranscript, shouldAutoFollowMessages]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const msg = inputValue.trim();
    if (!msg || isRunning) return;

    const parsedCommand = parseSlashCommandInput(msg);
    if (parsedCommand) {
      const command = commands.find((candidate) => candidate.name === parsedCommand.commandName
        || candidate.aliases.includes(parsedCommand.commandName));

      if (!command) {
        setSubmitError(`/${parsedCommand.commandName} is not a recognized command.`);
        return;
      }

      if (!command.webCompatible) {
        setSubmitError(`/${command.name} is not available in web mode.`);
        return;
      }

      const normalizedPrompt = formatSlashCommandPrompt(command.name, parsedCommand.args);

      setInputValue('');
      setSubmitError(null);

      if (command.promptBacked) {
        try {
          const resolved = await api.commands.resolvePrompt(command.name, parsedCommand.args);
          await submitMessage({
            message: normalizedPrompt,
            visiblePrompt: resolved.visiblePrompt,
            model: selectedModel ?? undefined,
            providerId: selectedProviderId,
            buildMode: !planMode,
            planMode,
            modelMode: modelModeSupported ? selectedModelMode : null,
          });
          recordSubmittedInput(normalizedPrompt);
        } catch (err: unknown) {
          setSubmitError(err instanceof Error ? err.message : 'Failed to submit');
        }
        return;
      }

      if (command.name === 'read') {
        setReadPlayback((current) => createReadPlaybackState({
          ...current,
          status: 'generating',
          prompt: normalizedPrompt,
          output: null,
        }));
      }

      try {
        appendMessage('user', normalizedPrompt);
        const response = await api.commands.execute(command.name, parsedCommand.args, session?.sessionId);
        recordSubmittedInput(normalizedPrompt);
        const refreshed = await refreshSession().catch(() => undefined);
        const refreshedMessages = refreshed?.messages ?? [];
        const hasPersistedOutput = !response.output || refreshedMessages.some((message) =>
          (message.type === 'assistant' || message.type === 'system') && message.content === response.output,
        );
        if (response.output && !hasPersistedOutput) {
          appendMessage('system', response.output);
        }
        if (response.commandName === 'read') {
          if (response.success && response.audio?.url && response.audio.token) {
            setReadPlayback(createReadPlaybackState({
              status: 'ready',
              token: response.audio.token,
              url: response.audio.url,
              prompt: response.prompt,
              output: response.output,
            }));
          } else {
            setReadPlayback(createReadPlaybackState({
              status: response.success ? 'idle' : 'error',
              prompt: response.prompt,
              output: response.output,
            }));
          }
        }
        return;
      } catch (err: unknown) {
        if (command.name === 'read') {
          setReadPlayback(createReadPlaybackState({ status: 'error' }));
        }
        setSubmitError(err instanceof Error ? err.message : 'Failed to execute command');
        return;
      }
    }

    setInputValue('');
    setSubmitError(null);
    try {
      await submitMessage({
        message: msg,
        model: selectedModel ?? undefined,
        providerId: selectedProviderId,
        buildMode: !planMode,
        planMode,
        modelMode: modelModeSupported ? selectedModelMode : null,
      });
      recordSubmittedInput(msg);
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to submit');
    }
  };

  const handleCancel = async () => {
    try {
      await cancelRun();
    } catch (e) {
      console.error('Cancel failed:', e);
    }
  };

  const handleCopyMessage = useCallback(async (content: string) => {
    try {
      await copyMessageText(content);
      setSubmitError(null);
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to copy message');
    }
  }, []);

  const handleRollbackMessage = useCallback(async (rollbackIndex: number, content: string) => {
    try {
      await rollbackToMessage(rollbackIndex);
      setInputValue(content);
      setSubmitError(null);
      setActiveMessageActionId(null);
      setTimeout(() => textareaRef.current?.focus(), 0);
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to roll back message');
    }
  }, [rollbackToMessage]);

  const handlePendingQuestionSubmit = useCallback(async () => {
    if (!pendingQuestion) {
      return;
    }
    if (!pendingQuestionAnswer) {
      setSubmitError('Please answer the pending question before continuing.');
      return;
    }
    setSubmitError(null);
    try {
      await answerPendingQuestion(pendingQuestion.toolUseId, pendingQuestionAnswer);
    } catch (err: unknown) {
      setSubmitError(err instanceof Error ? err.message : 'Failed to submit answer');
    }
  }, [answerPendingQuestion, pendingQuestion, pendingQuestionAnswer]);

  const toggleToolbarMenu = useCallback((menu: ToolbarMenu, trigger: HTMLElement) => {
    const next = openToolbarMenu === menu ? null : menu;
    if (next === 'provider-model') {
      setProviderMenuProviderId(selectedProviderId ?? session?.availableProviders?.[0]?.id ?? null);
    }
    if (next != null) {
      const triggerRect = trigger.getBoundingClientRect();
      const rowCount = menu === 'provider-model'
        ? Math.max((session?.availableProviders?.length ?? 0), providerMenuModelOptions.length + 1)
        : (menu === 'model-mode' ? modelModeOptions.length : 2);
      const estimatedMenuHeight = rowCount * TOOLBAR_MENU_ROW_HEIGHT + TOOLBAR_MENU_PADDING;
      const spaceBelow = window.innerHeight - triggerRect.bottom;
      const spaceAbove = triggerRect.top;
      setOpenToolbarMenuAbove(spaceBelow < estimatedMenuHeight && spaceAbove > spaceBelow);
    }
    setOpenToolbarMenu(next);
  }, [modelModeOptions.length, openToolbarMenu, providerMenuModelOptions.length, selectedProviderId, session?.availableProviders]);

  const handleClosePanelTab = useCallback((tabId: string) => {
    if (tabId === 'context') {
      setContextTabOpen(false);
      setContextError(null);

      if (activeTabId === 'context') {
        const fallbackTab = lastNonContextTabId !== 'context'
          && (lastNonContextTabId === 'tree' || openFiles.some((file) => file.path === lastNonContextTabId))
          ? lastNonContextTabId
          : 'tree';
        setActiveTab(fallbackTab);
      }
      return;
    }

    if (tabId === 'git') {
      setGitTabOpen(false);
      setGitError(null);

      if (activeTabId === 'git') {
        const fallbackTab = lastNonContextTabId !== 'context'
          && lastNonContextTabId !== 'git'
          && (lastNonContextTabId === 'tree' || openFiles.some((file) => file.path === lastNonContextTabId))
          ? lastNonContextTabId
          : 'tree';
        setActiveTab(fallbackTab);
      }
      return;
    }

    if (tabId === 'tree') {
      setFilePanelVisible(false);
      setContextTabOpen(false);
      setGitTabOpen(false);
      setContextError(null);
      setGitError(null);
      onFileExplorerVisibilityChange?.(false);
      return;
    }

    closeTab(tabId);
  }, [activeTabId, closeTab, lastNonContextTabId, onFileExplorerVisibilityChange, openFiles, setActiveTab]);

  const handleToggleContext = useCallback(async () => {
    if (!id) {
      return;
    }

    if (contextTabOpen && activeTabId === 'context') {
      handleClosePanelTab('context');
      return;
    }

    setContextTabOpen(true);
    setActiveTab('context');
    setContextError(null);

    if (contextCache[id]) {
      return;
    }

    setContextLoading(true);
    try {
      const context = await api.sessions.getContext(id);
      setContextCache((prev) => ({ ...prev, [id]: context }));
    } catch (err: unknown) {
      setContextError(err instanceof Error ? err.message : 'Failed to load session context');
    } finally {
      setContextLoading(false);
    }
  }, [activeTabId, contextCache, contextTabOpen, handleClosePanelTab, id, setActiveTab]);

  const handleToggleGit = useCallback(async () => {
    if (!id) {
      return;
    }

    if (gitTabOpen && activeTabId === 'git') {
      handleClosePanelTab('git');
      return;
    }

    setGitTabOpen(true);
    setActiveTab('git');
    setGitError(null);

    if (gitCache[id]) {
      return;
    }

    setGitLoading(true);
    try {
      const gitStatus = await api.sessions.getGitStatus(id);
      setGitCache((prev) => ({ ...prev, [id]: gitStatus }));
    } catch (err: unknown) {
      setGitError(err instanceof Error ? err.message : 'Failed to load git status');
    } finally {
      setGitLoading(false);
    }
  }, [activeTabId, gitCache, gitTabOpen, handleClosePanelTab, id, setActiveTab]);

  const handleLoadGitDiff = useCallback(async (entry: SessionGitEntryDto) => {
    if (!id) {
      return;
    }

    setGitDiffOpen(true);
    setGitDiffView('diff');
    setGitDiffLoading(true);
    setGitDiffError(null);
    setGitDiffFileContent(null);
    setGitDiff((prev) => {
      if (prev && prev.path === entry.path) {
        return {
          ...prev,
          kind: entry.kind,
          path: entry.path,
        };
      }

      return {
        kind: entry.kind,
        path: entry.path,
        diff: '',
      };
    });

    try {
      const diff = await api.sessions.getGitDiff(id, entry.path);
      setGitDiff(diff);
    } catch (err: unknown) {
      setGitDiffError(err instanceof Error ? err.message : 'Failed to load git diff');
      setGitDiff((prev) => ({
        kind: entry.kind,
        path: entry.path,
        diff: prev?.path === entry.path ? prev.diff : '',
      }));
    } finally {
      setGitDiffLoading(false);
    }
  }, [id]);

  const handleOpenGitDiff = useCallback(async (entry: SessionGitEntryDto) => {
    await handleLoadGitDiff(entry);
  }, [handleLoadGitDiff]);

  const handleShowGitDiff = useCallback(() => {
    setGitDiffView('diff');
  }, []);

  const handleShowGitFullFile = useCallback(async () => {
    if (!gitDiff?.path || gitDiffLoading) {
      return;
    }

    setGitDiffView('file');
    if (gitDiffFileContent) {
      return;
    }

    setGitDiffLoading(true);
    setGitDiffError(null);
    try {
      const content = await api.files.content(projectPath, gitDiff.path);
      setGitDiffFileContent(content);
    } catch (err: unknown) {
      setGitDiffError(err instanceof Error ? err.message : 'Failed to load file content');
    } finally {
      setGitDiffLoading(false);
    }
  }, [gitDiff?.path, gitDiffFileContent, gitDiffLoading, projectPath]);

  const handleShowGitFullFileCompare = useCallback(async () => {
    if (!gitDiff?.path || gitDiffLoading || !id) {
      return;
    }

    setGitDiffView('full-file-compare');
    if (gitDiffFileContentCompare) {
      return;
    }

    setGitDiffLoading(true);
    setGitDiffError(null);
    try {
      const content = await api.sessions.getGitFileContent(id, gitDiff.path, true);
      setGitDiffFileContentCompare({
        previousContent: content.previousContent,
        currentContent: content.currentContent,
      });
    } catch (err: unknown) {
      setGitDiffError(err instanceof Error ? err.message : 'Failed to load file content');
    } finally {
      setGitDiffLoading(false);
    }
  }, [gitDiff?.path, gitDiffFileContentCompare, gitDiffLoading, id]);

  const handleCloseGitDiff = useCallback(() => {
    setGitDiffOpen(false);
    setGitDiffLoading(false);
    setGitDiffError(null);
    setGitDiff(null);
    setGitDiffFileContent(null);
    setGitDiffFileContentCompare(null);
    setGitDiffView('diff');
  }, []);

  const selectedContext = id ? (contextCache[id] ?? null) : null;
  const selectedGitStatus = id ? (gitCache[id] ?? null) : null;
  const selectedContextLabel = sessionName ?? session?.name ?? (session?.sessionId ? session.sessionId.slice(0, 8) : 'Session Context');
  const selectedGitLabel = sessionName ?? session?.name ?? (session?.sessionId ? session.sessionId.slice(0, 8) : 'Git');
  const panelOpen = filePanelVisible || contextTabOpen || gitTabOpen;
  const mainColumnStyle = panelOpen ? styles.mainColumnConstrained : styles.mainColumnExpanded;
  const extraTabs = [
    ...(contextTabOpen ? [{ id: 'context', label: 'Context', actionLabel: 'Close Context', icon: <InfoIcon /> }] : []),
    ...(gitTabOpen ? [{ id: 'git', label: 'Git', actionLabel: 'Close Git', icon: <PackageIcon /> }] : []),
  ];

  if (loading) {
    return <div className="state-message">Loading session…</div>;
  }

  if (error && !session) {
    return <div className="state-message error">{error}</div>;
  }

  if (!session) {
    return <div className="state-message">Session not found.</div>;
  }

  const chatFontScopeStyle = getChatFontScopeStyle(settings);

  return (
    <div style={{ ...styles.page, ...chatFontScopeStyle }} data-testid="chat-font-scope">
      <div style={mainColumnStyle} data-testid="chat-main-column">
        <div style={styles.header}>
          <div>
            <div style={styles.titleRow}>
              {editingName ? (
                <input
                  ref={nameInputRef}
                  className="input-field"
                  style={styles.nameInput}
                  value={nameInput}
                  onChange={(e) => setNameInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      void handleFinishRename();
                    } else if (e.key === 'Escape') {
                      handleCancelRename();
                    }
                  }}
                  onBlur={() => {
                    void handleFinishRename();
                  }}
                  data-testid="session-name-input"
                />
              ) : (
                <h1
                  style={styles.title}
                  onClick={handleStartRename}
                  data-testid="session-name"
                  title="Click to rename"
                >
                  {sessionName || 'New Session'}
                </h1>
              )}
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.contextBtn}
                onClick={() => {
                  void handleToggleContext();
                }}
                data-testid="chat-session-context-btn"
                title="Open session context"
                aria-label="Open session context"
                aria-pressed={contextTabOpen && activeTabId === 'context'}
              >
                <IconFrame><InfoIcon /></IconFrame>
              </button>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.contextBtn}
                onClick={() => {
                  void handleToggleGit();
                }}
                data-testid="chat-session-git-btn"
                title="Open session git status"
                aria-label="Open session git status"
                aria-pressed={gitTabOpen && activeTabId === 'git'}
              >
                <IconFrame><PackageIcon /></IconFrame>
              </button>
            </div>
            <code style={styles.sessionId}>{session.sessionId}</code>
          </div>
          <div style={styles.meta}>
            <span style={styles.statusBadge(session.status)}>{session.status}</span>
            <span style={styles.metaDate}>
              Created {new Date(session.createdAt).toLocaleString()}
            </span>
          </div>
        </div>

          <div style={styles.transcriptControls} data-testid="transcript-controls">
            <span style={styles.transcriptControlsLabel}>Transcript</span>
            <button
              type="button"
              className="btn btn-ghost"
              style={styles.transcriptIconButton(showThinkingDetails)}
              onClick={() => setShowThinkingDetails((current) => !current)}
              aria-pressed={showThinkingDetails}
              aria-label={showThinkingDetails ? 'Hide model thinking' : 'Show model thinking'}
              title={showThinkingDetails ? 'Hide model thinking' : 'Show model thinking'}
              data-testid="toggle-thinking-visibility"
            >
              <span style={styles.transcriptIconButtonIcon}>
                <IconFrame size={14}><InfoIcon size={14} /></IconFrame>
              </span>
              <span style={styles.transcriptIconButtonText}>Thinking</span>
            </button>
            <button
              type="button"
              className="btn btn-ghost"
              style={styles.transcriptIconButton(showToolExecutions)}
              onClick={() => setShowToolExecutions((current) => !current)}
              aria-pressed={showToolExecutions}
              aria-label={showToolExecutions ? 'Hide tool executions' : 'Show tool executions'}
              title={showToolExecutions ? 'Hide tool executions' : 'Show tool executions'}
              data-testid="toggle-tool-visibility"
            >
              <span style={styles.transcriptIconButtonIcon}>
                <IconFrame size={14}><ServiceStatusIcon size={14} /></IconFrame>
              </span>
              <span style={styles.transcriptIconButtonText}>Tools</span>
            </button>
          </div>

        {isRunning && (
          <div style={styles.runBanner}>
            <div style={styles.runBannerStatus}>
              <span style={styles.runDot} />
              Run <code style={styles.runId}>{activeRun.runId}</code> — {activeRun.status}
            </div>
            <button
              className="btn btn-danger"
              style={styles.cancelBtn}
              onClick={handleCancel}
              data-testid="cancel-btn"
            >
              Cancel
            </button>
          </div>
        )}

        {isRunning && activeRetryStatus && (
          <div style={styles.retryBanner} data-testid="run-retry-indicator">
            <div style={styles.retryBannerTitle}>Agent still active: retrying request</div>
            <div style={styles.retryBannerBody}>{activeRetryStatus}</div>
          </div>
        )}

        <div
          ref={messagesAreaRef}
          style={styles.messagesArea}
          data-testid="messages-area"
          onScroll={handleMessagesAreaScroll}
        >
          {visibleMessages.length === 0 && visibleRunTranscript.length === 0 && !showRunStartPlaceholder ? (
            <div style={styles.emptyMessages}>
              <span style={styles.emptyIcon}><ChatBubbleIcon size={28} /></span>
              <p style={styles.emptyText}>No messages yet in this session.</p>
            </div>
          ) : (
            <div style={styles.messageList}>
              {visibleMessages.map((msg, idx) => {
                const isUserMessage = msg.role === 'user';
                const actionId = `persisted-message-${idx}`;
                const actionsVisible = activeMessageActionId === actionId;
                const visiblePersistedActivity = (msg.activityTimeline ?? []).filter((item) =>
                  shouldShowTranscriptItem(item, showThinkingDetails, showToolExecutions),
                );
                return (
                  <div
                    key={idx}
                    style={isUserMessage ? styles.userMessageItem : styles.assistantMessageItem}
                    data-testid={isUserMessage ? `user-message-${idx}` : `assistant-message-${idx}`}
                    onMouseEnter={() => setActiveMessageActionId(actionId)}
                    onMouseLeave={() => setActiveMessageActionId((current) => (current === actionId ? null : current))}
                    onFocus={() => setActiveMessageActionId(actionId)}
                    onBlur={(event) => {
                      if (!event.currentTarget.contains(event.relatedTarget as Node | null)) {
                        setActiveMessageActionId((current) => (current === actionId ? null : current));
                      }
                    }}
                    >
                      {isUserMessage ? (
                        <div style={styles.userMessageFrame} data-testid={`user-message-frame-${idx}`}>
                          <PlainMessageContent text={msg.content} testId={`user-message-content-${idx}`} />
                        </div>
                      ) : (
                        <div style={styles.assistantMessageFrame} data-testid={`assistant-message-frame-${idx}`}>
                          <StructuredMessage text={msg.content} />
                        </div>
                    )}
                    {!isUserMessage && visiblePersistedActivity.length > 0 ? (
                      <div style={styles.persistedTranscriptGroup} data-testid={`persisted-activity-${idx}`}>
                        {visiblePersistedActivity.map((item, activityIdx) => renderInlineTranscriptItem(item, activityIdx, `persisted-activity-${idx}`))}
                      </div>
                    ) : null}
                    {!isUserMessage && msg.fileSummary && msg.fileSummary.totalChanges > 0
                      ? renderFileSummaryBox(msg.fileSummary, `persisted-file-summary-${idx}`)
                      : null}
                    {actionsVisible ? (
                      <div
                        style={isUserMessage ? styles.messageActionsUser : styles.messageActionsAssistant}
                        data-testid={`message-actions-${idx}`}
                      >
                        {isUserMessage && msg.rollbackIndex != null ? (
                          <button
                            type="button"
                            className="btn btn-ghost"
                            style={styles.messageActionButton}
                            onClick={() => {
                              void handleRollbackMessage(msg.rollbackIndex!, msg.content);
                            }}
                            data-testid={`message-rollback-${idx}`}
                          >
                            Rollback
                          </button>
                        ) : null}
                        <button
                          type="button"
                          className="btn btn-ghost"
                          style={styles.messageActionButton}
                          onClick={() => {
                            void handleCopyMessage(msg.content);
                          }}
                          data-testid={`message-copy-${idx}`}
                        >
                          Copy
                        </button>
                        <span style={styles.messageTimestamp} data-testid={`message-timestamp-${idx}`}>
                          {formatMessageTimestamp(msg.timestamp)}
                        </span>
                      </div>
                    ) : null}
                  </div>
                );
              })}
              {showRunStartPlaceholder ? (
                <div
                  style={styles.assistantMessageItem}
                  data-testid="run-start-placeholder"
                >
                  <div style={styles.runStartPlaceholder}>
                    <span style={styles.runDot} />
                    Thinking...
                  </div>
                </div>
              ) : null}
              {visibleRunTranscript.map(({ item, originalIndex }, idx) => (
                item.kind === 'assistant' ? (
                  (() => {
                    const isLiveTranscript = originalIndex === runTranscript.length - 1 && runStatus !== 'completed' && runStatus !== 'error';
                    return (
                  <div
                    key={`run-text-${originalIndex}`}
                    style={styles.assistantMessageItem}
                    data-testid={isLiveTranscript ? 'live-output' : `run-transcript-text-${idx}`}
                  >
                    <StructuredMessage text={item.content} showCursor={isLiveTranscript} />
                  </div>
                    );
                  })()
                ) : (
                  renderInlineTranscriptItem(
                    item,
                    idx,
                    item.kind === 'thinking'
                      ? 'thinking-block'
                      : item.kind === 'status'
                        ? 'status-block'
                        : item.kind === 'tool-input'
                          ? 'tool-input-block'
                          : 'inline-tool-block',
                  )
                )
              ))}
            </div>
          )}
          <div ref={messagesEndRef} />
        </div>

        {runStatus === 'completed' && !isRunning && (
          <div style={styles.completedBadge} data-testid="run-completed-badge">
            Run completed
          </div>
        )}

        {runStatus === 'completed' && fileSummary && fileSummary.totalChanges > 0 && renderFileSummaryBox(fileSummary, 'file-summary')}

        {runStatus === 'error' && error && (
          <div style={styles.runErrorBadge} data-testid="run-error-badge">
            Run failed: {error}
          </div>
        )}

        {isReadCommandGenerating && (
          <div style={styles.readProgress} data-testid="read-command-progress">
            <div style={styles.readProgressLabel}>Generating voice...</div>
            <div style={styles.readProgressTrack}>
              <div style={styles.readProgressBar} />
            </div>
          </div>
        )}

        {showReadPlaybackControls && (
          <div style={styles.readPlayer} data-testid="read-command-player">
            <div style={styles.readPlayerHeader}>
              <div>
                <div style={styles.readPlayerTitle}>Voice playback</div>
                <div style={styles.readPlayerMeta}>{readPlayback.prompt ?? '/read'}</div>
              </div>
              <div style={styles.readPlayerStatus}>{readPlayback.status}</div>
            </div>
            <div style={styles.readPlayerControls}>
              {(readPlayback.status === 'ready' || readPlayback.status === 'paused' || readPlayback.status === 'ended') && (
                <button type="button" className="btn btn-ghost" style={styles.readPlayerButton} onClick={() => {
                  void handleReadPlaybackPlay();
                }} data-testid="read-command-play">
                  Play
                </button>
              )}
              {readPlayback.status === 'playing' && (
                <button type="button" className="btn btn-ghost" style={styles.readPlayerButton} onClick={handleReadPlaybackPause} data-testid="read-command-pause">
                  Pause
                </button>
              )}
              <button type="button" className="btn btn-ghost" style={styles.readPlayerButton} onClick={handleReadPlaybackStop} data-testid="read-command-stop">
                Stop
              </button>
            </div>
          </div>
        )}

        {pendingQuestion && (
          <div style={styles.pendingQuestionCard} data-testid="pending-question-card">
            <div style={styles.pendingQuestionTitle}>Pending question</div>
            <div style={styles.pendingQuestionText} data-testid="pending-question-text">{pendingQuestion.question}</div>
            {pendingQuestion.choices.length > 0 && (
              <div style={styles.pendingQuestionChoices}>
                {pendingQuestion.choices.map((choice) => (
                  <label key={choice} style={styles.pendingQuestionChoiceLabel}>
                    <input
                      type="radio"
                      name="pending-question-choice"
                      checked={pendingQuestionChoice === choice}
                      onChange={() => setPendingQuestionChoice(choice)}
                    />
                    <span>{choice}</span>
                  </label>
                ))}
                <label style={styles.pendingQuestionChoiceLabel} data-testid="pending-question-custom-option">
                  <input
                    type="radio"
                    name="pending-question-choice"
                    checked={pendingQuestionChoice === '__custom__'}
                    onChange={() => setPendingQuestionChoice('__custom__')}
                  />
                  <span>Type your own answer</span>
                </label>
              </div>
            )}
            {pendingQuestionRequiresCustomAnswer && (
              <textarea
                style={styles.pendingQuestionInput}
                rows={3}
                value={pendingQuestionCustomAnswer}
                onChange={(event) => setPendingQuestionCustomAnswer(event.target.value)}
                placeholder="Type your answer..."
                data-testid="pending-question-input"
              />
            )}
            <div style={styles.pendingQuestionActions}>
              <button
                type="button"
                className="btn btn-primary"
                style={styles.pendingQuestionSubmit}
                onClick={() => {
                  void handlePendingQuestionSubmit();
                }}
                disabled={pendingQuestionAnswer.length === 0}
                data-testid="pending-question-submit"
              >
                Submit answer
              </button>
            </div>
          </div>
        )}

        <form style={styles.composer} onSubmit={handleSubmit} data-testid="composer">
          <div style={styles.composerInputRow} data-testid="composer-input-row">
            <div style={styles.textareaWrap}>
              <CommandPalette
                commands={commands}
                query={paletteQuery}
                onSelect={(command) => {
                  void handleCommandSelect(command);
                }}
                onDismiss={handleCommandDismiss}
                visible={paletteOpen}
              />
              <textarea
                ref={textareaRef}
                style={styles.textarea}
                value={inputValue}
                onChange={handleInputChange}
                placeholder={isWaitingForUser ? 'Answer the pending question to continue…' : isRunning ? 'Waiting for response…' : 'Type a message… (use / for commands, @ to reference files)'}
                disabled={isRunning}
                rows={3}
                data-testid="message-input"
                onKeyDown={(e) => {
                  if (paletteOpen && ['ArrowDown', 'ArrowUp', 'Enter', 'Escape'].includes(e.key)) {
                    e.preventDefault();
                    return;
                  }
                  if (autocompleteVisible && autocompleteItems.length > 0) {
                    if (e.key === 'ArrowDown') {
                      e.preventDefault();
                      setAutocompleteSelectedIndex((prev) =>
                        prev < autocompleteItems.length - 1 ? prev + 1 : 0,
                      );
                      return;
                    }
                    if (e.key === 'ArrowUp') {
                      e.preventDefault();
                      setAutocompleteSelectedIndex((prev) =>
                        prev > 0 ? prev - 1 : autocompleteItems.length - 1,
                      );
                      return;
                    }
                    if (e.key === 'Enter' && autocompleteSelectedIndex >= 0) {
                      e.preventDefault();
                      handleAutocompleteSelect(autocompleteItems[autocompleteSelectedIndex]!);
                      return;
                    }
                    if (e.key === 'Escape') {
                      e.preventDefault();
                      dismissAutocomplete();
                      return;
                    }
                  }
                  if (!e.altKey && !e.ctrlKey && !e.metaKey && !e.shiftKey) {
                    if (e.key === 'ArrowUp' && navigateSubmittedInputHistory('older')) {
                      e.preventDefault();
                      return;
                    }
                    if (e.key === 'ArrowDown' && navigateSubmittedInputHistory('newer')) {
                      e.preventDefault();
                      return;
                    }
                  }
                  if (e.key === 'Enter' && !e.shiftKey) {
                    e.preventDefault();
                    void handleSubmit(e as unknown as React.FormEvent);
                  }
                }}
              />
            </div>
              <button
                type="button"
                className={isRunning ? 'btn btn-danger' : 'btn btn-primary'}
                style={styles.sendBtn(composerActionDisabled, isRunning)}
                disabled={composerActionDisabled}
                onClick={(event) => {
                  if (isRunning) {
                    void handleCancel();
                    return;
                  }
                  void handleSubmit(event as unknown as React.FormEvent);
                }}
                data-testid={isRunning ? 'stop-btn' : 'send-btn'}
                aria-label={isRunning ? 'Stop run' : 'Send message'}
                title={isRunning ? 'Stop run' : 'Send message'}
              >
                <IconFrame>{isRunning ? <StopIcon /> : <SendIcon />}</IconFrame>
              </button>
          </div>
          {submitError && <span style={styles.submitError}>{submitError}</span>}
          <div style={styles.composerToolbar} data-testid="composer-toolbar" ref={toolbarRef}>
            <div style={styles.toolbarDropdown}>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.dropdownTrigger(isRunning)}
                onClick={(e) => toggleToolbarMenu('intent', e.currentTarget)}
                disabled={isRunning}
                data-testid="composer-intent-trigger"
              >
                <span>{planMode ? 'Plan' : 'Build'}</span>
                <span style={styles.dropdownChevron}><ChevronDownIcon size={14} /></span>
              </button>
              {openToolbarMenu === 'intent' && (
                <div style={styles.dropdownMenu(openToolbarMenuAbove)} data-testid="composer-intent-menu">
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={styles.dropdownOption(!planMode)}
                    onClick={() => {
                      setPlanMode(false);
                      setOpenToolbarMenu(null);
                    }}
                    data-testid="composer-intent-option-build"
                  >
                    Build
                  </button>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={styles.dropdownOption(planMode)}
                    onClick={() => {
                      setPlanMode(true);
                      setOpenToolbarMenu(null);
                    }}
                    data-testid="composer-intent-option-plan"
                  >
                    Plan
                  </button>
                </div>
              )}
            </div>

            <div style={styles.toolbarDropdown}>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.dropdownTrigger(isRunning)}
                onClick={(e) => toggleToolbarMenu('provider-model', e.currentTarget)}
                disabled={isRunning}
                data-testid="composer-provider-model-trigger"
              >
                <span style={styles.providerModelTriggerText}>
                  {selectedProvider?.name ?? 'Provider'} / {selectedModelOption?.label ?? selectedModel}
                </span>
                <span style={styles.dropdownChevron}><ChevronDownIcon size={14} /></span>
              </button>
              {openToolbarMenu === 'provider-model' && (
                <div style={styles.providerModelMenu(openToolbarMenuAbove)} data-testid="composer-provider-model-menu">
                  <div style={styles.providerColumn}>
                    {(session.availableProviders ?? []).map((provider: ProviderOptionDto) => (
                      <button
                        key={provider.id}
                        type="button"
                        className="btn btn-ghost"
                        style={styles.dropdownOption(provider.id === providerMenuProvider?.id)}
                        onClick={() => setProviderMenuProviderId(provider.id)}
                        data-testid={`composer-provider-option-${toTestIdFragment(provider.id)}`}
                      >
                        {provider.name}
                      </button>
                    ))}
                  </div>
                  <div style={styles.modelColumn}>
                    <div style={styles.dropdownSectionTitle}>{providerMenuProvider?.name ?? 'Models'}</div>
                    {providerMenuModelOptions.map((modelOption) => (
                      <button
                        key={`${providerMenuProvider?.id ?? 'provider'}-${modelOption.id}`}
                        type="button"
                        className="btn btn-ghost"
                        style={styles.dropdownOption(
                          providerMenuProvider?.id === selectedProviderId && modelOption.id === selectedModel,
                        )}
                        onClick={() => {
                          if (providerMenuProvider?.id) {
                            setSelectedProviderId(providerMenuProvider.id);
                          }
                          setSelectedModel(modelOption.id);
                          setOpenToolbarMenu(null);
                        }}
                        data-testid={
                          `composer-model-option-${toTestIdFragment(providerMenuProvider?.id ?? 'provider')}-${toTestIdFragment(modelOption.id)}`
                        }
                      >
                        {modelOption.label}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </div>

            {modelModeSupported && selectedModelMode != null && (
              <div style={styles.toolbarDropdown}>
                <button
                  type="button"
                  className="btn btn-ghost"
                  style={styles.dropdownTrigger(isRunning)}
                  onClick={(e) => toggleToolbarMenu('model-mode', e.currentTarget)}
                  disabled={isRunning}
                  data-testid="composer-model-mode-trigger"
                >
                  <span>{formatModeLabel(selectedModelMode)}</span>
                  <span style={styles.dropdownChevron}><ChevronDownIcon size={14} /></span>
                </button>
                {openToolbarMenu === 'model-mode' && (
                    <div style={styles.dropdownMenu(openToolbarMenuAbove)} data-testid="composer-model-mode-menu">
                    {modelModeOptions.map((mode) => (
                      <button
                        key={mode}
                        type="button"
                        className="btn btn-ghost"
                        style={styles.dropdownOption(selectedModelMode === mode)}
                        onClick={() => {
                          setSelectedModelMode(mode);
                          setOpenToolbarMenu(null);
                        }}
                        data-testid={`composer-model-mode-option-${toTestIdFragment(mode)}`}
                      >
                        {formatModeLabel(mode)}
                      </button>
                    ))}
                  </div>
                )}
              </div>
            )}

            <div style={styles.toolbarDropdown}>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.dropdownTrigger(isRunning)}
                onClick={handleOpenReferenceBrowser}
                disabled={isRunning}
                data-testid="composer-reference-trigger"
              >
                <span>Reference</span>
                <span style={styles.dropdownChevron}><ChevronDownIcon size={14} /></span>
              </button>
            </div>
          </div>
        </form>

        {terminalVisible && id && (
          <TerminalPanel
            sessionId={id}
            terminals={terminalTabs}
            selectedTerminalId={selectedTerminalId}
            onSelectTerminal={selectTerminal}
            onCreateTerminal={() => createTerminal(activeWorktreeId)}
            onCloseTerminal={closeTerminal}
            onTerminalUpdate={replaceTerminal}
          />
        )}

        <FileAutocomplete
          isVisible={autocompleteVisible}
          items={autocompleteItems}
          selectedIndex={autocompleteSelectedIndex}
          onSelect={handleAutocompleteSelect}
          onHover={handleAutocompleteHover}
          position={caretPosition}
          loading={fileSearchLoading}
          textareaRef={textareaRef}
        />
      </div>

      <FilePanel isOpen={panelOpen}>
        <>
          <FileTabBar
            openFiles={openFiles}
            extraTabs={extraTabs}
            activeTabId={activeTabId}
            onSelectTab={setActiveTab}
            onCloseTab={handleClosePanelTab}
            treeActionLabel={filePanelVisible ? 'Close Files' : undefined}
          />
          {activeTabId === 'context' ? (
            <SessionContextPanel
              context={selectedContext}
              loading={contextLoading}
              error={contextError}
              sessionLabel={selectedContextLabel}
            />
          ) : activeTabId === 'git' ? (
            <SessionGitPanel
              gitStatus={selectedGitStatus}
              loading={gitLoading}
              error={gitError}
              sessionLabel={selectedGitLabel}
              onSelectEntry={(entry) => {
                void handleOpenGitDiff(entry);
              }}
            />
          ) : activeTabId === 'tree' ? (
            <FileExplorer
              projectPath={projectPath}
              onFileSelect={openFile}
            />
          ) : (
            <FileContentViewer
              file={getActiveFile()?.content ?? null}
              loading={getActiveFile()?.loading}
            />
          )}
        </>
      </FilePanel>
      <SessionGitDiffModal
        isOpen={gitDiffOpen}
        kind={gitDiff?.kind ?? null}
        path={gitDiff?.path ?? null}
        diff={gitDiff?.diff ?? null}
        fileContent={gitDiffFileContent}
        previousContent={gitDiffFileContentCompare?.previousContent ?? null}
        currentContent={gitDiffFileContentCompare?.currentContent ?? null}
        view={gitDiffView}
        loading={gitDiffLoading}
        error={gitDiffError}
        onShowDiff={handleShowGitDiff}
        onShowFullFile={() => {
          void handleShowGitFullFile();
        }}
        onShowFullFileCompare={() => {
          void handleShowGitFullFileCompare();
        }}
        onClose={handleCloseGitDiff}
      />
      <Popup
        isOpen={referenceBrowserOpen}
        onClose={handleCloseReferenceBrowser}
        title="References"
        contentStyle={styles.referenceBrowserModal}
        bodyStyle={styles.referenceBrowserBody}
      >
        <div style={styles.referenceBrowserRoot} data-testid="composer-reference-browser">
          <div style={styles.referenceSearchRow}>
            <input
              className="input-field"
              type="text"
              value={referenceSearchQuery}
              onChange={(event) => setReferenceSearchQuery(event.target.value)}
              placeholder="Search references by title"
              style={styles.referenceSearchInput}
              data-testid="composer-reference-search"
              aria-label="Search references"
              data-autofocus="true"
            />
          </div>
          {references.length === 0 ? (
            <div style={styles.referenceEmptyState} data-testid="composer-reference-empty">
              No references available
            </div>
          ) : filteredReferences.length === 0 ? (
            <div style={styles.referenceEmptyState} data-testid="composer-reference-no-results">
              No references match your search
            </div>
          ) : (
            <>
              <div style={styles.referenceResultsList} data-testid="composer-reference-results">
                {visibleReferences.map((reference) => (
                  <div
                    key={reference.id}
                    style={styles.referenceRow}
                    data-testid={`composer-reference-row-${toTestIdFragment(reference.id)}`}
                  >
                    <button
                      type="button"
                      className="btn btn-ghost"
                      style={styles.referenceSelectButton}
                      onClick={() => handleReferenceSelect(reference)}
                      data-testid={`composer-reference-option-${toTestIdFragment(reference.id)}`}
                    >
                      <span style={styles.referenceRowText}>
                        <span style={styles.referenceRowFilename}>{reference.filename}</span>
                        {reference.source ? <span style={styles.referenceRowMeta}>{reference.source}</span> : null}
                      </span>
                    </button>
                    <button
                      type="button"
                      className="btn btn-ghost"
                      style={styles.referencePreviewButton}
                      onClick={() => handleOpenReferencePreview(reference.id)}
                      data-testid={`composer-reference-preview-${toTestIdFragment(reference.id)}`}
                    >
                      Preview
                    </button>
                  </div>
                ))}
              </div>
              {referencePageCount > 1 ? (
                <div style={styles.referencePagination}>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={styles.referencePageButton(referencePage === 0)}
                    onClick={() => setReferencePage((current) => Math.max(current - 1, 0))}
                    disabled={referencePage === 0}
                    data-testid="composer-reference-page-prev"
                  >
                    Previous
                  </button>
                  <span style={styles.referencePaginationStatus} data-testid="composer-reference-page-status">
                    Page {referencePage + 1} of {referencePageCount}
                  </span>
                  <button
                    type="button"
                    className="btn btn-ghost"
                    style={styles.referencePageButton(referencePage >= referencePageCount - 1)}
                    onClick={() => setReferencePage((current) => Math.min(current + 1, referencePageCount - 1))}
                    disabled={referencePage >= referencePageCount - 1}
                    data-testid="composer-reference-page-next"
                  >
                    Next
                  </button>
                </div>
              ) : null}
            </>
          )}
        </div>
      </Popup>
      <Popup
        isOpen={referencePreview != null}
        onClose={handleCloseReferencePreview}
        headerContent={(
          <div style={styles.referencePreviewHeader}>
            <div style={styles.referencePreviewTitle}>{referencePreview?.filename ?? 'Reference preview'}</div>
            <code style={styles.referencePreviewMeta}>{referencePreview?.source ?? referencePreview?.label ?? 'reference'}</code>
          </div>
        )}
        contentStyle={styles.referencePreviewModal}
        bodyStyle={styles.referencePreviewBody}
      >
        <div style={styles.referencePreviewContent} data-testid="composer-reference-preview-modal">
          {referencePreview ? (
            <RichMessageContent text={referencePreview.markdown} />
          ) : (
            <div style={styles.referenceEmptyState}>Reference preview unavailable.</div>
          )}
        </div>
      </Popup>
    </div>
  );
}

const styles = {
  page: {
    display: 'flex',
    fontFamily: 'var(--chat-font-family)',
    color: 'var(--text)',
    height: '100%',
    width: '100%',
    minWidth: 0,
    boxSizing: 'border-box' as const,
    overflow: 'hidden',
  } as React.CSSProperties,
  mainColumn: {
    flex: 1,
    padding: '24px 32px',
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 16,
    boxSizing: 'border-box' as const,
    overflow: 'auto',
  } as React.CSSProperties,
  mainColumnConstrained: {
    flex: 1,
    minWidth: 0,
    padding: '24px 32px',
    maxWidth: 900,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 16,
    boxSizing: 'border-box' as const,
    overflow: 'auto',
  } as React.CSSProperties,
  mainColumnExpanded: {
    flex: 1,
    minWidth: 0,
    padding: '24px 32px',
    maxWidth: 'none',
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 16,
    boxSizing: 'border-box' as const,
    overflow: 'auto',
  } as React.CSSProperties,
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'flex-start',
  } as React.CSSProperties,
  titleRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 10,
  } as React.CSSProperties,
  title: {
    fontSize: 'calc(var(--chat-font-size) + 7px)',
    fontWeight: 600,
    margin: '0 0 4px',
    color: 'var(--text)',
    cursor: 'pointer',
  } as React.CSSProperties,
  contextBtn: {
    border: '1px solid var(--border)',
    borderRadius: '999px',
    width: 24,
    height: 24,
    padding: 0,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  sessionId: {
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  nameInput: {
    fontSize: 'calc(var(--chat-font-size) + 7px)',
    fontWeight: 600,
    margin: '0 0 4px',
    color: 'var(--text)',
    background: 'var(--surface)',
    borderColor: 'var(--accent)',
    borderRadius: 'var(--radius-sm)',
    padding: '2px 8px',
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  meta: {
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'stretch',
    position: 'relative',
    gap: 4,
  } as React.CSSProperties,
  statusBadge: (status: string) =>
    ({
      fontSize: 'calc(var(--chat-font-size) - 2px)',
      fontWeight: 600,
      padding: '3px 10px',
      borderRadius: 'var(--radius-sm)',
      background: status === 'ACTIVE' ? 'rgba(15,123,108,0.12)' : 'rgba(155,154,151,0.12)',
      color: status === 'ACTIVE' ? 'var(--green)' : 'var(--text-muted)',
    }) as React.CSSProperties,
  metaDate: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--text-muted)',
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  runBanner: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    flexWrap: 'wrap' as const,
    padding: '8px 14px',
    background: 'rgba(35,131,226,0.08)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    fontSize: 'var(--chat-font-size)',
    color: 'var(--accent)',
  } as React.CSSProperties,
  retryBanner: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    padding: '10px 14px',
    background: 'rgba(191,90,36,0.1)',
    border: '1px solid rgba(191,90,36,0.24)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
  } as React.CSSProperties,
  retryBannerTitle: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    fontWeight: 700,
    color: 'var(--orange, #bf5a24)',
  } as React.CSSProperties,
  retryBannerBody: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    lineHeight: 1.4,
    color: 'var(--text-muted)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  runBannerStatus: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    minWidth: 0,
    flex: '1 1 auto',
  } as React.CSSProperties,
  runDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    background: 'var(--accent)',
    flexShrink: 0,
    animation: 'pulse 1.4s ease-in-out infinite',
  } as React.CSSProperties,
  runId: {
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    flex: 1,
  } as React.CSSProperties,
  cancelBtn: {
    marginLeft: 'auto',
    padding: '4px 12px',
    borderRadius: 'var(--radius-sm)',
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    fontWeight: 600,
    flexShrink: 0,
  } as React.CSSProperties,
  messagesArea: {
    flex: 1,
    background: 'var(--bg)',
    borderRadius: 'var(--radius-md)',
    padding: 16,
    overflowY: 'auto' as const,
    minHeight: 200,
  } as React.CSSProperties,
  transcriptControls: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    flexWrap: 'wrap' as const,
    justifyContent: 'flex-end',
  } as React.CSSProperties,
  transcriptControlsLabel: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    color: 'var(--text-muted)',
    fontWeight: 600,
    fontFamily: 'var(--chat-font-family)',
    marginRight: 4,
  } as React.CSSProperties,
  transcriptIconButton: (active: boolean) => ({
    display: 'inline-flex',
    alignItems: 'center',
    gap: 4,
    minHeight: 24,
    padding: '3px 7px',
    borderRadius: '999px',
    border: active ? '1px solid color-mix(in srgb, var(--accent) 64%, var(--border))' : '1px solid color-mix(in srgb, var(--border) 82%, transparent)',
    background: active ? 'linear-gradient(180deg, rgba(35,131,226,0.14), rgba(35,131,226,0.06))' : 'color-mix(in srgb, var(--surface) 92%, transparent)',
    color: active ? 'var(--accent)' : 'var(--text-muted)',
    fontSize: 'calc(var(--chat-font-size) - 4px)',
    fontWeight: 600,
    lineHeight: 1,
    boxShadow: active ? '0 4px 12px rgba(35,131,226,0.1)' : 'none',
  }) as React.CSSProperties,
  transcriptIconButtonIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 14,
    height: 14,
    borderRadius: '999px',
    background: 'rgba(255,255,255,0.18)',
  } as React.CSSProperties,
  transcriptIconButtonText: {
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  emptyMessages: {
    textAlign: 'center' as const,
    padding: '40px 0',
  } as React.CSSProperties,
  emptyIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  emptyText: {
    color: 'var(--text-muted)',
    fontSize: 'var(--chat-font-size)',
    fontFamily: 'var(--chat-font-family)',
    marginTop: 12,
  } as React.CSSProperties,
  messageList: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 20,
  } as React.CSSProperties,
  messageItem: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    width: '100%',
  } as React.CSSProperties,
  userMessageItem: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    width: '100%',
    alignItems: 'flex-end' as const,
    textAlign: 'right' as const,
  } as React.CSSProperties,
  userMessageFrame: {
    maxWidth: '100%',
    border: '1px solid var(--border)',
    background: 'var(--surface-accent)',
    borderRadius: 'var(--radius-md)',
    padding: '8px 12px',
  } as React.CSSProperties,
  plainMessageText: {
    margin: 0,
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'calc(var(--chat-font-size) + 1px)',
    lineHeight: 1.6,
    color: 'var(--text)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  assistantMessageFrame: {
    maxWidth: '100%',
  } as React.CSSProperties,
  assistantMessageItem: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    width: '100%',
    alignItems: 'flex-start' as const,
    textAlign: 'left' as const,
  } as React.CSSProperties,
  persistedTranscriptGroup: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
    width: '100%',
    marginTop: 4,
  } as React.CSSProperties,
  messageActionsUser: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-end',
    flexWrap: 'wrap' as const,
    gap: 8,
  } as React.CSSProperties,
  messageActionsAssistant: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-start',
    flexWrap: 'wrap' as const,
    gap: 8,
  } as React.CSSProperties,
  messageActionButton: {
    minHeight: 24,
    padding: '4px 8px',
    borderRadius: '999px',
    border: '1px solid var(--border)',
    background: 'var(--surface)',
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    lineHeight: 1,
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  messageTimestamp: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    color: 'var(--text-muted)',
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  inlineToolItem: {
    paddingLeft: 12,
    minWidth: 0,
  } as React.CSSProperties,
  inlineProgressItem: {
    paddingLeft: 12,
    minWidth: 0,
  } as React.CSSProperties,
  inlineProgressCard: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    padding: '6px 8px',
    borderRadius: 'var(--radius-sm)',
    background: 'color-mix(in srgb, var(--surface) 94%, transparent)',
    border: '1px solid var(--border)',
  } as React.CSSProperties,
  inlineProgressHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    flexWrap: 'wrap' as const,
    fontSize: 'calc(var(--chat-font-size) - 3px)',
    fontWeight: 600,
    color: 'var(--text)',
  } as React.CSSProperties,
  inlineProgressIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--accent)',
  } as React.CSSProperties,
  inlineProgressLabel: {
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  inlineProgressTag: {
    display: 'inline-flex',
    alignItems: 'center',
    padding: '1px 5px',
    borderRadius: '999px',
    background: 'rgba(35,131,226,0.08)',
    color: 'var(--text-muted)',
    fontSize: 'calc(var(--chat-font-size) - 5px)',
    fontWeight: 700,
    letterSpacing: 0.3,
    textTransform: 'uppercase' as const,
  } as React.CSSProperties,
  inlineProgressBody: {
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    lineHeight: 1.35,
    color: 'var(--text-muted)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  inlineProgressCode: {
    margin: 0,
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'calc(var(--chat-font-size) - 4px)',
    lineHeight: 1.35,
    color: 'var(--text)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
    overflowX: 'auto' as const,
    maxHeight: 96,
  } as React.CSSProperties,
  runStartPlaceholder: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: 8,
    marginLeft: 24,
    padding: '8px 12px',
    borderRadius: 'var(--radius-md)',
    background: 'color-mix(in srgb, var(--surface) 78%, transparent)',
    color: 'var(--text-muted)',
    fontSize: 'var(--chat-font-size)',
  } as React.CSSProperties,
  completedBadge: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--green)',
    textAlign: 'center' as const,
    padding: '4px 0',
  } as React.CSSProperties,
  runErrorBadge: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--red)',
    textAlign: 'center' as const,
    padding: '4px 0',
  } as React.CSSProperties,
  readProgress: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
    padding: '10px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface)',
    border: '1px solid var(--border)',
  } as React.CSSProperties,
  readProgressLabel: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--text-muted)',
    fontWeight: 600,
  } as React.CSSProperties,
  readProgressTrack: {
    position: 'relative' as const,
    width: '100%',
    height: 6,
    overflow: 'hidden',
    borderRadius: 999,
    background: 'rgba(35,131,226,0.12)',
  } as React.CSSProperties,
  readProgressBar: {
    position: 'absolute' as const,
    top: 0,
    left: '-35%',
    width: '35%',
    height: '100%',
    borderRadius: 999,
    background: 'var(--accent)',
    animation: 'read-progress 1.1s ease-in-out infinite',
  } as React.CSSProperties,
  readPlayer: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 10,
    padding: '12px 14px',
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface)',
    border: '1px solid var(--border)',
  } as React.CSSProperties,
  readPlayerHeader: {
    display: 'flex',
    alignItems: 'flex-start',
    justifyContent: 'space-between',
    gap: 12,
  } as React.CSSProperties,
  readPlayerTitle: {
    fontSize: 'var(--chat-font-size)',
    fontWeight: 600,
    color: 'var(--text)',
  } as React.CSSProperties,
  readPlayerMeta: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--text-muted)',
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  readPlayerStatus: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    fontWeight: 600,
    textTransform: 'capitalize' as const,
    color: 'var(--accent)',
  } as React.CSSProperties,
  readPlayerControls: {
    display: 'flex',
    gap: 8,
    flexWrap: 'wrap' as const,
  } as React.CSSProperties,
  readPlayerButton: {
    minHeight: 28,
    padding: '6px 10px',
    borderRadius: '999px',
    border: '1px solid var(--border)',
    background: 'var(--bg)',
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--text)',
  } as React.CSSProperties,
  fileSummaryBox: {
    margin: '8px 0',
    padding: '10px 14px',
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    fontSize: 'var(--chat-font-size)',
    fontFamily: 'var(--chat-font-family)',
  } as React.CSSProperties,
  fileSummaryTitle: {
    fontWeight: 600,
    marginBottom: 6,
  } as React.CSSProperties,
  fileSummaryRow: {
    marginBottom: 2,
  } as React.CSSProperties,
  fileSummaryLabel: {
    fontWeight: 600,
  } as React.CSSProperties,
  composer: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    padding: 8,
    outline: '1px solid var(--border)',
  } as React.CSSProperties,
  pendingQuestionCard: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 12,
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    padding: 12,
  } as React.CSSProperties,
  pendingQuestionTitle: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    fontWeight: 700,
    letterSpacing: '0.06em',
    textTransform: 'uppercase' as const,
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  pendingQuestionText: {
    fontSize: 'calc(var(--chat-font-size) + 1px)',
    lineHeight: 1.5,
    color: 'var(--text)',
  } as React.CSSProperties,
  pendingQuestionChoices: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  pendingQuestionChoiceLabel: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    color: 'var(--text)',
    fontSize: 'var(--chat-font-size)',
  } as React.CSSProperties,
  pendingQuestionInput: {
    width: '100%',
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    color: 'var(--text)',
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'var(--chat-font-size)',
    padding: '10px 12px',
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
    outline: 'none',
  } as React.CSSProperties,
  pendingQuestionActions: {
    display: 'flex',
    justifyContent: 'flex-end',
  } as React.CSSProperties,
  pendingQuestionSubmit: {
    minWidth: 140,
  } as React.CSSProperties,
  composerInputRow: {
    display: 'flex',
    alignItems: 'stretch',
    position: 'relative',
    gap: 12,
  } as React.CSSProperties,
  composerToolbar: {
    display: 'flex',
    flexWrap: 'wrap' as const,
    alignItems: 'center',
    gap: 8,
    paddingTop: 2,
  } as React.CSSProperties,
  toolbarDropdown: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    position: 'relative' as const,
  } as React.CSSProperties,
  toolbarLabel: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    fontWeight: 600,
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  dropdownTrigger: (disabled: boolean) => ({
    minWidth: 180,
    justifyContent: 'space-between',
    gap: 8,
    fontSize: 'calc(var(--chat-font-size) - 3px)',
    padding: '4px 8px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border)',
    background: disabled ? 'var(--bg)' : 'var(--surface)',
    color: 'var(--text)',
    fontWeight: 600,
    boxShadow: 'var(--shadow-sm)',
  }) as React.CSSProperties,
  dropdownChevron: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  } as React.CSSProperties,
  dropdownMenu: (openAbove: boolean) => ({
    position: 'absolute' as const,
    left: 0,
    ...(openAbove ? { bottom: 'calc(100% + 4px)' } : { top: 'calc(100% + 4px)' }),
    zIndex: 20,
    minWidth: 180,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    padding: 4,
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border)',
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-md)',
  }) as React.CSSProperties,
  dropdownOption: (active: boolean) => ({
    width: '100%',
    justifyContent: 'flex-start',
    padding: '4px 6px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid transparent',
    background: active ? 'var(--surface-accent)' : 'transparent',
    color: active ? 'var(--accent)' : 'var(--text)',
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    fontWeight: active ? 600 : 500,
  }) as React.CSSProperties,
  providerModelMenu: (openAbove: boolean) => ({
    position: 'absolute' as const,
    left: 0,
    ...(openAbove ? { bottom: 'calc(100% + 4px)' } : { top: 'calc(100% + 4px)' }),
    zIndex: 20,
    minWidth: 360,
    display: 'grid',
    gridTemplateColumns: '160px minmax(180px, 1fr)',
    gap: 8,
    padding: 8,
    borderRadius: 'var(--radius-md)',
    border: '1px solid var(--border)',
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-md)',
  }) as React.CSSProperties,
  providerColumn: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    paddingRight: 8,
    borderRight: '1px solid var(--border)',
  } as React.CSSProperties,
  modelColumn: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    minWidth: 0,
  } as React.CSSProperties,
  dropdownSectionTitle: {
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    fontWeight: 600,
    color: 'var(--text-muted)',
    padding: '2px 8px 4px',
  } as React.CSSProperties,
  dropdownEmptyState: {
    padding: '6px 8px',
    fontSize: 'calc(var(--chat-font-size) - 2px)',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  referenceBrowserModal: {
    width: 'min(92vw, 640px)',
    maxHeight: 'min(84vh, 820px)',
  } as React.CSSProperties,
  referenceBrowserBody: {
    padding: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    overflow: 'hidden',
  } as React.CSSProperties,
  referenceBrowserRoot: {
    display: 'flex',
    flexDirection: 'column' as const,
    minHeight: 260,
  } as React.CSSProperties,
  referenceSearchRow: {
    padding: '12px 16px 10px',
    borderBottom: '1px solid var(--border)',
  } as React.CSSProperties,
  referenceSearchInput: {
    width: '100%',
    background: 'var(--bg)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    color: 'var(--text)',
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'var(--chat-font-size)',
    padding: '8px 10px',
    boxSizing: 'border-box' as const,
  } as React.CSSProperties,
  referenceResultsList: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    padding: '8px 10px',
    overflowY: 'auto' as const,
    flex: 1,
  } as React.CSSProperties,
  referenceRow: {
    display: 'grid',
    gridTemplateColumns: 'minmax(0, 1fr) auto',
    gap: 8,
    alignItems: 'center',
  } as React.CSSProperties,
  referenceSelectButton: {
    width: '100%',
    display: 'flex',
    alignItems: 'center',
    minWidth: 0,
    padding: '6px 8px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid transparent',
    background: 'transparent',
    textAlign: 'left' as const,
  } as React.CSSProperties,
  referenceRowText: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 2,
    width: '100%',
    minWidth: 0,
  } as React.CSSProperties,
  referenceRowFilename: {
    display: 'block',
    width: '100%',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    fontWeight: 600,
    color: 'var(--text)',
  } as React.CSSProperties,
  referenceRowMeta: {
    display: 'block',
    width: '100%',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
    fontSize: 'calc(var(--chat-font-size) - 3px)',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  referencePreviewButton: {
    minWidth: 72,
    padding: '4px 10px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border)',
    background: 'var(--surface)',
    color: 'var(--text)',
    fontSize: 'calc(var(--chat-font-size) - 3px)',
    fontWeight: 600,
  } as React.CSSProperties,
  referenceEmptyState: {
    padding: '24px 16px',
    color: 'var(--text-muted)',
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    textAlign: 'center' as const,
  } as React.CSSProperties,
  referencePagination: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 8,
    padding: '8px 12px 10px',
    borderTop: '1px solid var(--border)',
  } as React.CSSProperties,
  referencePageButton: (disabled: boolean) => ({
    minWidth: 72,
    padding: '4px 10px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border)',
    color: disabled ? 'var(--text-muted)' : 'var(--text)',
    background: disabled ? 'var(--bg)' : 'var(--surface)',
    fontSize: 'calc(var(--chat-font-size) - 3px)',
  }) as React.CSSProperties,
  referencePaginationStatus: {
    fontSize: 'calc(var(--chat-font-size) - 3px)',
    color: 'var(--text-muted)',
    fontWeight: 600,
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,
  referencePreviewModal: {
    width: 'min(92vw, 840px)',
    maxHeight: 'min(88vh, 960px)',
  } as React.CSSProperties,
  referencePreviewBody: {
    padding: 0,
    overflow: 'hidden',
  } as React.CSSProperties,
  referencePreviewHeader: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    minWidth: 0,
  } as React.CSSProperties,
  referencePreviewTitle: {
    fontSize: 16,
    fontWeight: 600,
    color: 'var(--text)',
  } as React.CSSProperties,
  referencePreviewMeta: {
    fontSize: 12,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  referencePreviewContent: {
    padding: '16px 20px 20px',
    overflowY: 'auto' as const,
    maxHeight: '70vh',
  } as React.CSSProperties,
  providerModelTriggerText: {
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,
  textarea: {
    width: '100%',
    background: 'transparent',
    border: 'none',
    borderRadius: 'var(--radius-md)',
    color: 'var(--text)',
    fontFamily: 'var(--chat-font-family)',
    fontSize: 'var(--chat-font-size)',
    padding: '8px 10px',
    resize: 'vertical' as const,
    boxSizing: 'border-box' as const,
    outline: 'none',
  } as React.CSSProperties,
  textareaWrap: {
    position: 'relative' as const,
    flex: 1,
    minWidth: 0,
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    background: 'var(--bg)',
    paddingRight: 48,
  } as React.CSSProperties,
  submitError: {
    fontSize: 'calc(var(--chat-font-size) - 1px)',
    color: 'var(--red)',
  } as React.CSSProperties,
  sendBtn: (disabled: boolean, danger = false) =>
    ({
      width: 36,
      height: 36,
      padding: 0,
      position: 'absolute' as const,
      right: 6,
      bottom: 6,
      display: 'inline-flex',
      alignItems: 'center',
      justifyContent: 'center',
      color: disabled ? 'var(--text-muted)' : 'var(--text-on-accent)',
      border: 'none',
      borderRadius: '999px',
      fontSize: 16,
      fontWeight: 600,
      background: disabled
        ? 'var(--bg)'
        : (danger ? 'var(--red)' : 'var(--accent)'),
      boxShadow: disabled ? 'none' : 'var(--shadow-sm)',
    }) as React.CSSProperties,
};
