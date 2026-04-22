import React, { useRef, useEffect, useCallback, useMemo } from 'react';
import { createPortal } from 'react-dom';
import type { FileNode } from '../types/api';
import { FileIcon, FolderIcon, IconFrame } from './Icons';

interface AutocompletePosition {
  top: number;
  left: number;
  lineHeight: number;
}

interface FileAutocompleteProps {
  isVisible: boolean;
  items: FileNode[];
  selectedIndex: number;
  onSelect: (item: FileNode) => void;
  onHover: (index: number) => void;
  position: AutocompletePosition | null;
  loading: boolean;
  textareaRef: React.RefObject<HTMLTextAreaElement | null>;
}

const DROPDOWN_MAX_HEIGHT = 240;

export default function FileAutocomplete({
  isVisible,
  items,
  selectedIndex,
  onSelect,
  onHover,
  position,
  loading,
  textareaRef,
}: FileAutocompleteProps) {
  const dropdownRef = useRef<HTMLDivElement>(null);
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    if (selectedIndex >= 0 && listRef.current) {
      const items = listRef.current.querySelectorAll('[data-role="autocomplete-item"]');
      (items[selectedIndex] as HTMLElement)?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  const dropdownStyle = useMemo((): React.CSSProperties => {
    if (!position || !textareaRef.current) {
      return { display: 'none' };
    }

    const textareaRect = textareaRef.current.getBoundingClientRect();
    const dropdownTop = textareaRect.top + position.top + position.lineHeight;
    const spaceBelow = window.innerHeight - dropdownTop;
    const showAbove = spaceBelow < DROPDOWN_MAX_HEIGHT && dropdownTop > DROPDOWN_MAX_HEIGHT;

    return {
      position: 'fixed',
      top: showAbove
        ? textareaRect.top + position.top - DROPDOWN_MAX_HEIGHT
        : dropdownTop,
      left: textareaRect.left + position.left,
      maxHeight: DROPDOWN_MAX_HEIGHT,
      width: Math.max(360, textareaRect.width * 0.5),
      zIndex: 1000,
    };
  }, [position, textareaRef]);

  const handleItemClick = useCallback(
    (item: FileNode) => {
      onSelect(item);
    },
    [onSelect],
  );

  if (!isVisible || !position) return null;

  return createPortal(
    <div
      ref={dropdownRef}
      style={{ ...styles.dropdown, ...dropdownStyle }}
      data-testid="file-autocomplete"
    >
      {loading && (
        <div style={styles.loading} data-testid="file-autocomplete-loading">
          Loading files…
        </div>
      )}

      {!loading && items.length === 0 && (
        <div style={styles.empty} data-testid="file-autocomplete-empty">
          No files found
        </div>
      )}

      {!loading && items.length > 0 && (
        <ul ref={listRef} style={styles.list} role="listbox" data-testid="file-autocomplete-list">
          {items.map((item, i) => (
            <li
              key={item.path}
              data-role="autocomplete-item"
              role="option"
              aria-selected={i === selectedIndex}
              style={{
                ...styles.item,
                background: i === selectedIndex ? 'rgba(35, 131, 226, 0.1)' : 'transparent',
              }}
              onClick={() => handleItemClick(item)}
              onMouseEnter={() => onHover(i)}
              data-testid={`file-autocomplete-item-${i}`}
            >
              <span style={styles.icon}>
                <IconFrame>{item.isDirectory ? <FolderIcon /> : <FileIcon />}</IconFrame>
              </span>
              <span style={styles.path}>{item.path}</span>
            </li>
          ))}
        </ul>
      )}
    </div>,
    document.body,
  );
}

const styles: Record<string, React.CSSProperties> = {
  dropdown: {
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-lg)',
    borderRadius: 'var(--radius-md)',
    overflow: 'hidden',
    display: 'flex',
    flexDirection: 'column',
    border: '1px solid var(--border)',
  },
  loading: {
    padding: '16px 20px',
    textAlign: 'center',
    color: 'var(--text-muted)',
    fontSize: 13,
    fontFamily: 'var(--font-sans)',
  },
  empty: {
    padding: '16px 20px',
    textAlign: 'center',
    color: 'var(--text-muted)',
    fontSize: 13,
    fontFamily: 'var(--font-sans)',
  },
  list: {
    listStyle: 'none',
    margin: 0,
    padding: '4px 0',
    overflowY: 'auto',
    maxHeight: DROPDOWN_MAX_HEIGHT,
  },
  item: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '6px 16px',
    cursor: 'pointer',
    transition: 'background 0.1s',
    fontSize: 13,
    fontFamily: 'var(--font-mono)',
    color: 'var(--text)',
  },
  icon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  },
  path: {
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
};
