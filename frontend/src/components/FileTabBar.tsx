import type { ReactNode } from 'react';
import { useRef, useEffect } from 'react';
import type { OpenFile } from '../hooks/useOpenFiles';
import { CloseIcon, FolderIcon, IconFrame } from './Icons';

export interface AuxiliaryTab {
  id: string;
  label: string;
  title?: string;
  icon?: ReactNode;
  actionLabel?: string;
}

interface FileTabBarProps {
  openFiles: OpenFile[];
  activeTabId: string;
  onSelectTab: (id: string) => void;
  onCloseTab: (id: string) => void;
  extraTabs?: AuxiliaryTab[];
  showTreeTab?: boolean;
  treeActionLabel?: string;
}

export default function FileTabBar({
  openFiles,
  activeTabId,
  onSelectTab,
  onCloseTab,
  extraTabs = [],
  showTreeTab = true,
  treeActionLabel,
}: FileTabBarProps) {
  const scrollRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = scrollRef.current;
    if (!container) return;
    const activeEl = container.querySelector(`[data-tab-id="${activeTabId}"]`) as HTMLElement | null;
    if (activeEl && typeof activeEl.scrollIntoView === 'function') {
      activeEl.scrollIntoView({ behavior: 'smooth', block: 'nearest', inline: 'nearest' });
    }
  }, [activeTabId]);

  return (
    <>
      <div ref={scrollRef} style={styles.bar} data-testid="file-tab-bar">
        {showTreeTab && (
          <button
            data-tab-id="tree"
            className="btn"
            style={{
              ...styles.tab,
              ...(activeTabId === 'tree' ? styles.tabActive : {}),
            }}
            onClick={() => onSelectTab('tree')}
          >
            <span style={styles.tabIcon}><IconFrame><FolderIcon /></IconFrame></span>
            <span>Files</span>
            {treeActionLabel ? (
              <span
                className="tab-close-btn"
                style={styles.closeBtn}
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab('tree');
                }}
                aria-label={treeActionLabel}
              >
                <IconFrame size={13}><CloseIcon size={13} /></IconFrame>
              </span>
            ) : null}
          </button>
        )}

        {extraTabs.map((tab) => (
          <button
            key={tab.id}
            data-tab-id={tab.id}
            className="btn"
            style={{
              ...styles.tab,
              ...(activeTabId === tab.id ? styles.tabActive : {}),
            }}
            onClick={() => onSelectTab(tab.id)}
          >
            {tab.icon ? <span style={styles.tabIcon}><IconFrame>{tab.icon}</IconFrame></span> : null}
            <span style={styles.tabLabel} title={tab.title ?? tab.label}>
              {tab.label}
            </span>
            {tab.actionLabel ? (
              <span
                className="tab-close-btn"
                style={styles.closeBtn}
                onClick={(e) => {
                  e.stopPropagation();
                  onCloseTab(tab.id);
                }}
                aria-label={tab.actionLabel}
              >
                <IconFrame size={13}><CloseIcon size={13} /></IconFrame>
              </span>
            ) : null}
          </button>
        ))}

        {openFiles.map((file) => (
          <button
            key={file.path}
            data-tab-id={file.path}
            className="btn"
            style={{
              ...styles.tab,
              ...(activeTabId === file.path ? styles.tabActive : {}),
            }}
            onClick={() => onSelectTab(file.path)}
          >
            <span style={styles.tabLabel} title={file.path}>
              {file.name}
            </span>
            <span
              className="tab-close-btn"
              style={styles.closeBtn}
              onClick={(e) => {
                e.stopPropagation();
                onCloseTab(file.path);
              }}
              aria-label={`Close ${file.name}`}
            >
              <IconFrame size={13}><CloseIcon size={13} /></IconFrame>
            </span>
          </button>
        ))}
      </div>
    </>
  );
}

const styles = {
  bar: {
    display: 'flex',
    overflowX: 'auto' as const,
    boxShadow: '0 1px 0 var(--border)',
    background: 'var(--bg)',
    flexShrink: 0,
  } as React.CSSProperties,
  tab: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    padding: '6px 10px',
    background: 'transparent',
    borderTop: 'none',
    borderRight: 'none',
    borderBottomWidth: 2,
    borderBottomStyle: 'solid',
    borderBottomColor: 'transparent',
    borderLeft: 'none',
    color: 'var(--text-muted)',
    fontSize: 11,
    whiteSpace: 'nowrap' as const,
    position: 'relative' as const,
    transition: 'background 0.15s, color 0.15s, border-color 0.15s',
    flexShrink: 0,
  } as React.CSSProperties,
  tabActive: {
    color: 'var(--text)',
    background: 'var(--surface)',
    borderBottomColor: 'var(--accent)',
  } as React.CSSProperties,
  tabIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'currentColor',
  } as React.CSSProperties,
  tabLabel: {
    maxWidth: 120,
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
    minWidth: 0,
  } as React.CSSProperties,
  closeBtn: {
    display: 'none',
    marginLeft: 2,
    fontSize: 13,
    lineHeight: 1,
    color: 'var(--text-muted)',
    fontWeight: 700,
  } as React.CSSProperties,
};
