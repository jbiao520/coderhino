import React, { useEffect, useState, useCallback, useMemo } from 'react';
import type { FileNode } from '../types/api';
import FileTreeItem from './FileTreeItem';
import { IconFrame, SearchIcon } from './Icons';
import { useFileTree } from '../hooks/useFileTree';

interface FileExplorerProps {
  projectPath: string;
  onFileSelect?: (file: FileNode) => void;
}

export default function FileExplorer({ projectPath, onFileSelect }: FileExplorerProps) {
  const [rootChildren, setRootChildren] = useState<FileNode[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [filter, setFilter] = useState('');
  const { fetchDirectory } = useFileTree();

  const loadRoot = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const listing = await fetchDirectory(projectPath, '.');
      setRootChildren(listing.children);
    } catch (e) {
      setError(e instanceof Error ? e.message : 'Failed to load files');
    } finally {
      setLoading(false);
    }
  }, [projectPath, fetchDirectory]);

  useEffect(() => {
    if (rootChildren.length === 0) {
      loadRoot();
    }
  }, [rootChildren.length, loadRoot]);

  const handleFileSelect = useCallback(
    (file: FileNode) => {
      onFileSelect?.(file);
    },
    [onFileSelect],
  );

  const filteredChildren = useMemo(() => {
    if (!filter) return rootChildren;
    const lower = filter.toLowerCase();
    return rootChildren.filter((node) =>
      matchesFilter(node, lower),
    );
  }, [rootChildren, filter]);

  return (
    <div style={styles.container} data-testid="file-explorer">
      <div style={styles.filterRow}>
        <span style={styles.filterIcon}><IconFrame><SearchIcon /></IconFrame></span>
        <input
          className="input-field"
          style={styles.filterInput}
          type="text"
          placeholder="Filter files…"
          value={filter}
          onChange={(e) => setFilter(e.target.value)}
          data-testid="file-filter-input"
        />
      </div>

      <div style={styles.body}>
        {loading && <div style={styles.state}>Loading…</div>}
        {error && <div style={styles.error}>{error}</div>}
        {!loading && !error && filteredChildren.length > 0 && (
          <ul style={styles.tree} role="tree">
            {filteredChildren.map((node) => (
              <FileTreeItem
                key={node.path}
                node={node}
                depth={0}
                projectPath={projectPath}
                onFileSelect={handleFileSelect}
                fetchDirectory={fetchDirectory}
                filter={filter}
              />
            ))}
          </ul>
        )}
        {!loading && !error && filteredChildren.length === 0 && rootChildren.length > 0 && (
          <div style={styles.state} data-testid="no-matching-files">No matching files</div>
        )}
        {!loading && !error && rootChildren.length === 0 && (
          <div style={styles.state}>Empty directory</div>
        )}
      </div>
    </div>
  );
}

function matchesFilter(node: FileNode, lowerFilter: string): boolean {
  if (node.name.toLowerCase().includes(lowerFilter)) return true;
  return false;
}

const styles = {
  container: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    overflow: 'hidden',
  } as React.CSSProperties,
  filterRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    padding: '8px 10px',
    boxShadow: '0 1px 0 var(--border)',
  } as React.CSSProperties,
  filterIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  } as React.CSSProperties,
  filterInput: {
    flex: 1,
    fontSize: 11,
    padding: '4px 8px',
  } as React.CSSProperties,
  body: {
    flex: 1,
    overflowY: 'auto' as const,
    padding: '8px 0',
  } as React.CSSProperties,
  tree: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
  } as React.CSSProperties,
  state: {
    padding: '16px 12px',
    color: 'var(--text-muted)',
    fontSize: 12,
    textAlign: 'center' as const,
  } as React.CSSProperties,
  error: {
    padding: '16px 12px',
    color: 'var(--red)',
    fontSize: 12,
    textAlign: 'center' as const,
  } as React.CSSProperties,
};
