import type React from 'react';
import type { SessionGitEntryDto, SessionGitStatusDto } from '../types/api';
import { ChevronRightIcon, CloseIcon, CopyIcon, EditIcon, IconFrame, MoreHorizontalIcon, PlusIcon, WarningIcon } from './Icons';
import { getFileIcon } from './FileTreeItem';

type ChangeCategory = 'modified' | 'added' | 'deleted' | 'renamed' | 'copied' | 'unmerged' | 'unversioned' | 'changed';

interface ChangePresentation {
  category: ChangeCategory;
  badgeLabel: string;
  icon: React.ReactNode;
  color: string;
  softColor: string;
  borderColor: string;
}

interface SessionGitPanelProps {
  gitStatus: SessionGitStatusDto | null;
  loading: boolean;
  error: string | null;
  sessionLabel: string;
  onSelectEntry?: (entry: SessionGitEntryDto) => void;
}

function toEntryTestId(path: string): string {
  return path.replace(/[^a-z0-9]+/gi, '-').replace(/^-+|-+$/g, '').toLowerCase();
}

function getChangePresentation(entry: SessionGitEntryDto): ChangePresentation {
  const category = getPrimaryCategory(entry);
  const badgeLabel = (entry.status && entry.status.trim()) || (entry.kind === 'unversioned' ? 'unversioned' : 'changed');

  switch (category) {
    case 'modified':
      return {
        category,
        badgeLabel,
        icon: <EditIcon size={12} />,
        color: 'var(--accent)',
        softColor: 'var(--accent-soft)',
        borderColor: 'color-mix(in srgb, var(--accent) 30%, var(--border))',
      };
    case 'added':
      return {
        category,
        badgeLabel,
        icon: <PlusIcon size={12} />,
        color: 'var(--green)',
        softColor: 'var(--green-soft)',
        borderColor: 'color-mix(in srgb, var(--green) 30%, var(--border))',
      };
    case 'deleted':
      return {
        category,
        badgeLabel,
        icon: <CloseIcon size={12} />,
        color: 'var(--red)',
        softColor: 'var(--red-soft)',
        borderColor: 'color-mix(in srgb, var(--red) 30%, var(--border))',
      };
    case 'renamed':
      return {
        category,
        badgeLabel,
        icon: <ChevronRightIcon size={12} />,
        color: 'var(--orange)',
        softColor: 'color-mix(in srgb, var(--orange) 12%, var(--surface))',
        borderColor: 'color-mix(in srgb, var(--orange) 30%, var(--border))',
      };
    case 'copied':
      return {
        category,
        badgeLabel,
        icon: <CopyIcon size={12} />,
        color: 'var(--orange)',
        softColor: 'color-mix(in srgb, var(--orange) 12%, var(--surface))',
        borderColor: 'color-mix(in srgb, var(--orange) 30%, var(--border))',
      };
    case 'unmerged':
      return {
        category,
        badgeLabel,
        icon: <WarningIcon size={12} />,
        color: 'var(--orange)',
        softColor: 'color-mix(in srgb, var(--orange) 12%, var(--surface))',
        borderColor: 'color-mix(in srgb, var(--orange) 30%, var(--border))',
      };
    case 'unversioned':
      return {
        category,
        badgeLabel,
        icon: <PlusIcon size={12} />,
        color: 'var(--green)',
        softColor: 'color-mix(in srgb, var(--green) 10%, var(--surface))',
        borderColor: 'color-mix(in srgb, var(--green) 30%, var(--border))',
      };
    default:
      return {
        category,
        badgeLabel,
        icon: <MoreHorizontalIcon size={12} />,
        color: 'var(--text-muted)',
        softColor: 'var(--surface)',
        borderColor: 'var(--border)',
      };
  }
}

function getPrimaryCategory(entry: SessionGitEntryDto): ChangeCategory {
  if (entry.kind === 'unversioned') {
    return 'unversioned';
  }

  const normalizedStatus = (entry.status ?? '').toLowerCase();
  if (normalizedStatus.includes('deleted')) {
    return 'deleted';
  }
  if (normalizedStatus.includes('unmerged')) {
    return 'unmerged';
  }
  if (normalizedStatus.includes('renamed')) {
    return 'renamed';
  }
  if (normalizedStatus.includes('copied')) {
    return 'copied';
  }
  if (normalizedStatus.includes('added')) {
    return 'added';
  }
  if (normalizedStatus.includes('modified') || normalizedStatus.includes('type changed')) {
    return 'modified';
  }
  return 'changed';
}

function renderEntry(entry: SessionGitEntryDto, testIdPrefix: string, isTracked: boolean, onSelectEntry?: (entry: SessionGitEntryDto) => void) {
  const entryId = toEntryTestId(entry.path);
  const presentation = getChangePresentation(entry);
  return (
    <li key={isTracked ? `${entry.path}-${entry.status}` : entry.path} style={styles.entryItem}>
      <button
        type="button"
        className="btn btn-ghost"
        style={styles.entryButton(presentation)}
        onClick={() => onSelectEntry?.(entry)}
        data-testid={`${testIdPrefix}-${entryId}`}
        data-change-category={presentation.category}
      >
        <span
          style={styles.changeIconWrap(presentation)}
          data-testid={`session-git-change-icon-${entryId}`}
          aria-hidden="true"
        >
          <IconFrame size={14}>{presentation.icon}</IconFrame>
        </span>
        <span style={styles.entryMain}>
          <span style={styles.pathMeta}>
            <span style={styles.pathIcon} data-testid={`session-git-file-icon-${entryId}`}>
              <IconFrame size={14}>{getFileIcon(entry.path)}</IconFrame>
            </span>
            <span style={styles.path}>{entry.path}</span>
          </span>
          <span style={styles.entryHint}>{isTracked ? 'Tracked change' : 'Unversioned file'}</span>
        </span>
        <span
          style={styles.statusBadge(presentation)}
          data-testid={`session-git-change-badge-${entryId}`}
        >
          {presentation.badgeLabel}
        </span>
      </button>
    </li>
  );
}

export default function SessionGitPanel({
  gitStatus,
  loading,
  error,
  sessionLabel,
  onSelectEntry,
}: SessionGitPanelProps) {
  const trackedChanges = gitStatus?.trackedChanges ?? [];
  const unversionedFiles = gitStatus?.unversionedFiles ?? [];
  const isClean = trackedChanges.length === 0 && unversionedFiles.length === 0;

  return (
    <div style={styles.panel} data-testid="session-git-panel">
      <div style={styles.content}>
        <h2 style={styles.heading}>{sessionLabel}</h2>

        {loading && <div className="state-message">Loading git status…</div>}
        {error && <div className="state-message error">{error}</div>}

        {!loading && !error && isClean ? (
          <div className="state-message" data-testid="session-git-clean-state">
            This worktree is clean.
          </div>
        ) : null}

        {!loading && !error && trackedChanges.length > 0 ? (
          <section style={styles.section} data-testid="session-git-tracked-changes">
            <h3 style={styles.sectionTitle}>Tracked Changes</h3>
            <ul style={styles.list}>
              {trackedChanges.map((entry) => renderEntry(entry, 'session-git-tracked-change', true, onSelectEntry))}
            </ul>
          </section>
        ) : null}

        {!loading && !error && unversionedFiles.length > 0 ? (
          <section style={styles.section} data-testid="session-git-unversioned-files">
            <h3 style={styles.sectionTitle}>Unversioned Files</h3>
            <ul style={styles.list}>
              {unversionedFiles.map((entry) => renderEntry(entry, 'session-git-unversioned-file', false, onSelectEntry))}
            </ul>
          </section>
        ) : null}
      </div>
    </div>
  );
}

const styles = {
  panel: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    overflow: 'hidden',
  },
  content: {
    overflow: 'auto' as const,
    padding: 14,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 14,
  } as React.CSSProperties,
  heading: {
    margin: 0,
    fontSize: 14,
    fontFamily: 'var(--font-mono)',
  } as React.CSSProperties,
  section: {
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    padding: 10,
    background: 'var(--surface)',
  } as React.CSSProperties,
  sectionTitle: {
    margin: '0 0 10px',
    fontSize: 12,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.04em',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  list: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  entryItem: {
    fontSize: 12,
  } as React.CSSProperties,
  path: {
    fontFamily: 'var(--font-mono)',
    wordBreak: 'break-word' as const,
    color: 'var(--text)',
  } as React.CSSProperties,
  pathIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
    marginTop: 1,
  } as React.CSSProperties,
  entryButton: (presentation: ChangePresentation) => ({
    border: 'none',
    background: presentation.softColor,
    boxShadow: `inset 0 0 0 1px ${presentation.borderColor}`,
    padding: '10px 12px',
    margin: 0,
    color: 'inherit',
    textAlign: 'left' as const,
    cursor: 'pointer',
    display: 'flex',
    alignItems: 'center',
    gap: 10,
    width: '100%',
    borderRadius: 'var(--radius-sm)',
    minWidth: 0,
  }) as React.CSSProperties,
  changeIconWrap: (presentation: ChangePresentation) => ({
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    width: 24,
    height: 24,
    borderRadius: 999,
    color: presentation.color,
    background: 'color-mix(in srgb, currentColor 12%, transparent)',
    flexShrink: 0,
  }) as React.CSSProperties,
  entryMain: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
    minWidth: 0,
    flex: 1,
  } as React.CSSProperties,
  pathMeta: {
    display: 'flex',
    alignItems: 'flex-start',
    gap: 8,
    minWidth: 0,
  } as React.CSSProperties,
  entryHint: {
    color: 'var(--text-muted)',
    fontSize: 11,
    paddingLeft: 22,
  } as React.CSSProperties,
  statusBadge: (presentation: ChangePresentation) => ({
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    alignSelf: 'flex-start',
    borderRadius: 999,
    padding: '3px 8px',
    background: 'var(--surface)',
    boxShadow: `inset 0 0 0 1px ${presentation.borderColor}`,
    color: presentation.color,
    fontSize: 11,
    fontWeight: 600,
    whiteSpace: 'nowrap' as const,
    marginLeft: 8,
    maxWidth: '40%',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
  }) as React.CSSProperties,
};
