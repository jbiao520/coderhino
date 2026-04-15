import { useState } from 'react';
import type { ProjectDto, SearchResult } from '../types/api';
import SearchPopup from './SearchPopup';
import { FolderIcon, IconFrame } from './Icons';

/**
 * @deprecated This component is replaced by ProjectSessionTree.tsx for multi-project support.
 * Use MultiProjectContext and ProjectSessionTree instead.
 */
interface ProjectSectionProps {
  activeProject: ProjectDto | null;
  recentProjects: ProjectDto[];
  onSetActive: (project: ProjectDto) => void;
  onClearActive: () => void;
  onCreateProject: (path: string) => Promise<ProjectDto>;
}

/**
 * @deprecated This component is replaced by ProjectSessionTree.tsx for multi-project support.
 * Use MultiProjectContext and ProjectSessionTree instead.
 */
export default function ProjectSection({
  activeProject,
  recentProjects,
  onSetActive,
  onClearActive,
  onCreateProject,
}: ProjectSectionProps) {
  const [searchOpen, setSearchOpen] = useState(false);
  const [pathInput, setPathInput] = useState('');
  const [expanded, setExpanded] = useState(false);
  const [creating, setCreating] = useState(false);

  const handleSearchSelect = async (result: SearchResult) => {
    setCreating(true);
    try {
      const project = await onCreateProject(result.path);
      onSetActive(project);
    } catch {
    } finally {
      setCreating(false);
      setSearchOpen(false);
    }
  };

  const handleCreate = async () => {
    const trimmed = pathInput.trim();
    if (!trimmed) return;
    setCreating(true);
    try {
      const project = await onCreateProject(trimmed);
      onSetActive(project);
      setPathInput('');
    } catch {
    } finally {
      setCreating(false);
    }
  };

  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') handleCreate();
  };

  return (
    <div style={styles.section}>
      <div style={styles.sectionHeader}>
        <span style={styles.sectionIcon}><IconFrame><FolderIcon /></IconFrame></span>
        <span style={styles.sectionLabel}>Project</span>
      </div>

      {activeProject ? (
        <div style={styles.activeProject}>
          <div style={styles.activeName} title={activeProject.path}>
            {activeProject.name}
          </div>
          <div style={styles.activePath} title={activeProject.path}>
            {activeProject.path}
          </div>
          <button className="btn btn-ghost" style={styles.closeBtn} onClick={onClearActive}>
            Close Project
          </button>
        </div>
      ) : (
        <>
          <button className="btn btn-secondary" style={styles.openBtn} onClick={() => setSearchOpen(true)}>
            Open Project
          </button>

          <SearchPopup
            isOpen={searchOpen}
            onClose={() => setSearchOpen(false)}
            onSelect={handleSearchSelect}
          />

          <button className="btn" style={styles.manualToggle} onClick={() => setExpanded((e) => !e)}>
            {expanded ? 'Hide path input' : 'Or type a path…'}
          </button>

          {expanded && (
            <div style={styles.pathInputArea}>
              <input
                className="input-field"
                type="text"
                value={pathInput}
                onChange={(e) => setPathInput(e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="/path/to/project"
                style={styles.pathInput}
                disabled={creating}
              />
              <button
                className="btn btn-primary"
                style={{
                  ...styles.confirmBtn,
                  opacity: pathInput.trim() && !creating ? 1 : 0.4,
                }}
                onClick={handleCreate}
                disabled={!pathInput.trim() || creating}
              >
                {creating ? '…' : 'Open'}
              </button>
            </div>
          )}

          {recentProjects.length > 0 && (
            <div style={styles.recentArea}>
              <div style={styles.recentLabel}>Recent</div>
              {recentProjects.map((p) => (
                <button
                  key={p.id}
                  className="btn"
                  style={styles.recentItem}
                  onClick={() => onSetActive(p)}
                  title={p.path}
                >
                  <span style={styles.recentName}>{p.name}</span>
                  <span style={styles.recentPath}>{shortenPath(p.path)}</span>
                </button>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

function shortenPath(path: string): string {
  const parts = path.split('/').filter(Boolean);
  if (parts.length <= 2) return path;
  return '…/' + parts.slice(-2).join('/');
}

const styles: Record<string, React.CSSProperties> = {
  section: {
    padding: '8px 8px',
    boxShadow: '0 1px 0 var(--border)',
  },
  sectionHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '4px 12px 8px',
  },
  sectionIcon: {
    width: 20,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    textAlign: 'center',
    flexShrink: 0,
  },
  sectionLabel: {
    fontSize: 11,
    fontWeight: 700,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-sans)',
  },
  activeProject: {
    padding: '4px 12px 8px',
  },
  activeName: {
    fontSize: 13,
    fontWeight: 600,
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  activePath: {
    fontSize: 11,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    marginTop: 2,
    marginBottom: 8,
  },
  closeBtn: {
    width: '100%',
    fontSize: 11,
  },
  openBtn: {
    display: 'block',
    width: 'calc(100% - 24px)',
    margin: '0 12px',
    borderStyle: 'dashed',
    color: 'var(--accent)',
    fontSize: 12,
    fontWeight: 500,
    textAlign: 'center' as const,
  },
  manualToggle: {
    display: 'block',
    width: 'calc(100% - 24px)',
    margin: '4px 12px 0',
    padding: 0,
    border: 'none',
    background: 'transparent',
    color: 'var(--text-muted)',
    fontSize: 10,
    textAlign: 'center' as const,
    opacity: 0.7,
  },
  pathInputArea: {
    display: 'flex',
    gap: 4,
    padding: '6px 12px',
  },
  pathInput: {
    flex: 1,
    padding: '4px 8px',
    fontSize: 11,
    fontFamily: 'var(--font-mono)',
  },
  confirmBtn: {
    padding: '4px 10px',
    fontSize: 11,
    fontWeight: 600,
  },
  recentArea: {
    padding: '6px 12px',
  },
  recentLabel: {
    fontSize: 10,
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.6,
    color: 'var(--text-muted)',
    marginBottom: 4,
    fontFamily: 'var(--font-sans)',
  },
  recentItem: {
    display: 'flex',
    flexDirection: 'column' as const,
    width: '100%',
    padding: '5px 8px',
    border: 'none',
    borderRadius: 'var(--radius-sm)',
    background: 'transparent',
    textAlign: 'left' as const,
    transition: 'background 0.12s',
  },
  recentName: {
    fontSize: 12,
    fontWeight: 500,
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  recentPath: {
    fontSize: 10,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
};
