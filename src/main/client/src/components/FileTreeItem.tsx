import React, { useState, useCallback, useMemo } from 'react';
import type { FileNode } from '../types/api';
import { ChevronDownIcon, ChevronRightIcon, FileIcon, FolderIcon, IconFrame, JavaFileIcon, JsonFileIcon, MarkdownFileIcon, TypescriptFileIcon } from './Icons';

interface FileTreeItemProps {
  node: FileNode;
  depth: number;
  projectPath: string;
  onFileSelect: (file: FileNode) => void;
  fetchDirectory: (projectPath: string, dirPath: string) => Promise<{ children: FileNode[] }>;
  filter?: string;
}

export function getFileIcon(filename: string): JSX.Element {
  const lower = filename.toLowerCase();
  if (lower.endsWith('.ts') || lower.endsWith('.tsx')) {
    return <TypescriptFileIcon />;
  }
  if (lower.endsWith('.java')) {
    return <JavaFileIcon />;
  }
  if (lower.endsWith('.md')) {
    return <MarkdownFileIcon />;
  }
  if (lower.endsWith('.json')) {
    return <JsonFileIcon />;
  }
  return <FileIcon />;
}

export default function FileTreeItem({
  node,
  depth,
  projectPath,
  onFileSelect,
  fetchDirectory,
  filter,
}: FileTreeItemProps) {
  const [expanded, setExpanded] = useState(false);
  const [children, setChildren] = useState<FileNode[]>([]);
  const [loading, setLoading] = useState(false);

  const handleToggle = useCallback(async () => {
    if (!node.isDirectory) return;

    if (!expanded && children.length === 0) {
      setLoading(true);
      try {
        const listing = await fetchDirectory(projectPath, node.path);
        setChildren(listing.children);
      } catch {
        return;
      } finally {
        setLoading(false);
      }
    }
    setExpanded((e) => !e);
  }, [expanded, children.length, node.path, node.isDirectory, projectPath, fetchDirectory]);

  const handleClick = useCallback(() => {
    if (!node.isDirectory) {
      onFileSelect(node);
    }
  }, [node, onFileSelect]);

  const matchesSelf = filter ? node.name.toLowerCase().includes(filter.toLowerCase()) : true;

  const filteredChildren = useMemo(() => {
    if (!filter || !expanded || children.length === 0) return children;
    const lower = filter.toLowerCase();
    return children.filter((child) => nodeMatchesFilter(child, lower));
  }, [children, filter, expanded]);

  if (filter && !matchesSelf && filteredChildren.length === 0) {
    return null;
  }

  const indent = depth * 16;

  return (
    <li style={styles.item} role="treeitem" aria-expanded={node.isDirectory ? expanded : undefined}>
      <div
        style={styles.row(indent)}
        onClick={node.isDirectory ? handleToggle : handleClick}
      >
        {node.isDirectory ? (
          <span style={styles.chevron}>
            <IconFrame size={12}>{expanded ? <ChevronDownIcon size={12} /> : <ChevronRightIcon size={12} />}</IconFrame>
          </span>
        ) : (
          <span style={styles.chevronPlaceholder} />
        )}
        <span style={styles.icon}>
          <IconFrame>{node.isDirectory ? <FolderIcon /> : getFileIcon(node.name)}</IconFrame>
        </span>
        <span style={styles.name} title={node.path}>
          {node.name}
        </span>
        {loading && <span style={styles.loading}>…</span>}
      </div>

      {node.isDirectory && expanded && filteredChildren.length > 0 && (
        <ul style={styles.childList} role="group">
          {filteredChildren.map((child) => (
            <FileTreeItem
              key={child.path}
              node={child}
              depth={depth + 1}
              projectPath={projectPath}
              onFileSelect={onFileSelect}
              fetchDirectory={fetchDirectory}
              filter={filter}
            />
          ))}
        </ul>
      )}
    </li>
  );
}

function nodeMatchesFilter(node: FileNode, lowerFilter: string): boolean {
  if (node.name.toLowerCase().includes(lowerFilter)) return true;
  return false;
}

const styles = {
  item: {
    listStyle: 'none',
  } as React.CSSProperties,
  row: (indent: number) =>
    ({
      display: 'flex',
      alignItems: 'center',
      gap: 4,
      paddingLeft: indent + 4,
      paddingRight: 8,
      paddingTop: 3,
      paddingBottom: 3,
      cursor: 'pointer',
      fontSize: 12,
      fontFamily: 'var(--font-sans)',
      color: 'var(--text)',
      borderRadius: 'var(--radius-sm)',
      userSelect: 'none' as const,
    }) as React.CSSProperties,
  chevron: {
    color: 'var(--text-muted)',
    width: 12,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    textAlign: 'center' as const,
    flexShrink: 0,
  } as React.CSSProperties,
  chevronPlaceholder: {
    width: 12,
    flexShrink: 0,
  } as React.CSSProperties,
  icon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  } as React.CSSProperties,
  name: {
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
    color: 'var(--text)',
  } as React.CSSProperties,
  loading: {
    color: 'var(--text-muted)',
    fontSize: 11,
    marginLeft: 4,
  } as React.CSSProperties,
  childList: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
  } as React.CSSProperties,
};
