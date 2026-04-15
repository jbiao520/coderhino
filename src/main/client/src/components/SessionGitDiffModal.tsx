import type React from 'react';
import { Popup } from './Popup';
import type { FileContent, SessionGitEntryKind } from '../types/api';
import SessionGitDiffView from './SessionGitDiffView';
import SessionGitFullFileCompareView from './SessionGitFullFileCompareView';
import FileContentViewer from './FileContentViewer';

type SessionGitDiffModalView = 'diff' | 'file' | 'full-file-compare';

interface SessionGitDiffModalProps {
  isOpen: boolean;
  kind: SessionGitEntryKind | null;
  path: string | null;
  diff: string | null;
  fileContent: FileContent | null;
  previousContent: string | null;
  currentContent: string | null;
  view: SessionGitDiffModalView;
  loading: boolean;
  error: string | null;
  onShowDiff: () => void;
  onShowFullFile: () => void;
  onShowFullFileCompare: () => void;
  onClose: () => void;
}

export default function SessionGitDiffModal({
  isOpen,
  kind,
  path,
  diff,
  fileContent,
  previousContent,
  currentContent,
  view,
  loading,
  error,
  onShowDiff,
  onShowFullFile,
  onShowFullFileCompare,
  onClose,
}: SessionGitDiffModalProps) {
  const canShowDiff = !!diff;
  const canShowFullFile = !!fileContent || !!path;
  const canShowFullFileCompare = kind === 'tracked';
  const showToolbar = canShowDiff || canShowFullFile || canShowFullFileCompare;
  const showDiffError = !loading && !!error && view === 'diff' && !diff;
  const showFileError = !loading && !!error && view === 'file' && !fileContent;

  return (
    <Popup
      isOpen={isOpen}
      onClose={onClose}
      headerContent={(
        <div style={styles.header}>
          <div style={styles.title}>Git Diff</div>
          <div style={styles.kind}>{kind ?? 'unknown'}</div>
          <code style={styles.path}>{path ?? 'Unknown file'}</code>
        </div>
      )}
      contentStyle={styles.modal}
      bodyStyle={styles.body}
    >
      <div data-testid="session-git-diff-modal">
        {showToolbar ? (
          <div style={styles.toolbar}>
            <div style={styles.tabGroup}>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.tabButton(view === 'diff')}
                onClick={onShowDiff}
                disabled={!canShowDiff || loading}
                data-testid="session-git-diff-tab"
              >
                Diff
              </button>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.tabButton(view === 'file')}
                onClick={onShowFullFile}
                disabled={!canShowFullFile || loading}
                data-testid="session-git-full-file-tab"
              >
                {view === 'file' && loading ? 'Loading full file…' : 'Show full file'}
              </button>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.tabButton(view === 'full-file-compare')}
                onClick={onShowFullFileCompare}
                disabled={!canShowFullFileCompare || loading}
                data-testid="session-git-full-file-compare-tab"
              >
                {view === 'full-file-compare' && loading ? 'Loading…' : 'Full file (side-by-side)'}
              </button>
            </div>
          </div>
        ) : null}
        {loading && view === 'diff' ? <div className="state-message">Loading git diff…</div> : null}
        {loading && view === 'file' && !fileContent ? <div className="state-message">Loading full file…</div> : null}
        {showDiffError || showFileError ? <div className="state-message error">{error}</div> : null}
        {!loading && !error && view === 'diff' && !diff ? (
          <div className="state-message" data-testid="session-git-diff-empty">
            No diff content is available for this file.
          </div>
        ) : null}
        {!loading && !error && view === 'file' && !fileContent ? (
          <div className="state-message" data-testid="session-git-full-file-empty">
            No file content is available for this file.
          </div>
        ) : null}
        {view === 'diff' && diff ? (
          <SessionGitDiffView diff={diff} />
        ) : null}
        {view === 'file' && fileContent ? (
          <div style={styles.fullFileBody} data-testid="session-git-full-file-viewer">
            <FileContentViewer file={fileContent} loading={loading} />
          </div>
        ) : null}
        {view === 'full-file-compare' ? (
          <div style={styles.fullFileBody} data-testid="session-git-full-file-compare-viewer">
            <SessionGitFullFileCompareView
              previousContent={previousContent}
              currentContent={currentContent}
            />
          </div>
        ) : null}
      </div>
    </Popup>
  );
}

const styles = {
  header: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    minWidth: 0,
  } as React.CSSProperties,
  title: {
    fontSize: 16,
    fontWeight: 600,
    color: 'var(--text)',
  },
  kind: {
    fontSize: 11,
    lineHeight: 1,
    textTransform: 'uppercase',
    letterSpacing: '0.08em',
    color: 'var(--text-muted)',
  },
  path: {
    fontSize: 12,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
    wordBreak: 'break-word' as const,
  } as React.CSSProperties,
  modal: {
    width: 'min(96vw, 1600px)',
    height: 'min(94vh, 1200px)',
    maxHeight: '94vh',
  },
  body: {
    padding: 0,
    overflow: 'hidden',
  },
  toolbar: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'flex-start',
    gap: 12,
    padding: '12px 20px 0',
  },
  tabGroup: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  tabButton: (active: boolean) => ({
    padding: '6px 10px',
    fontSize: 12,
    border: active ? '1px solid var(--accent)' : '1px solid var(--border)',
    color: active ? 'var(--accent)' : 'var(--text-muted)',
    background: active ? 'var(--accent-soft)' : 'transparent',
  }) as React.CSSProperties,
  fullFileBody: {
    height: 'calc(100% - 44px)',
  },
};
