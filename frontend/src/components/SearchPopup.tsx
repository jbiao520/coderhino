import React, { useState, useEffect, useRef, useCallback } from 'react';
import { useSearchDirectories } from '../hooks/useSearchDirectories';
import type { SearchResult } from '../types/api';
import { IconFrame, SearchIcon, SpinnerIcon } from './Icons';

interface SearchPopupProps {
  isOpen: boolean;
  onClose: () => void;
  onSelect: (result: SearchResult) => void;
}

export default function SearchPopup({ isOpen, onClose, onSelect }: SearchPopupProps) {
  const [query, setQuery] = useState('');
  const [selectedIndex, setSelectedIndex] = useState(-1);
  const inputRef = useRef<HTMLInputElement>(null);
  const listRef = useRef<HTMLDivElement>(null);
  const overlayRef = useRef<HTMLDivElement>(null);
  const { results, loading, error, search } = useSearchDirectories();

  useEffect(() => {
    if (isOpen) {
      setQuery('');
      setSelectedIndex(-1);
      setTimeout(() => inputRef.current?.focus(), 0);
    }
  }, [isOpen]);

  useEffect(() => {
    setSelectedIndex(-1);
  }, [results]);

  useEffect(() => {
    if (selectedIndex >= 0 && listRef.current) {
      const items = listRef.current.querySelectorAll('[data-role="search-result-item"]');
      (items[selectedIndex] as HTMLElement)?.scrollIntoView({ block: 'nearest' });
    }
  }, [selectedIndex]);

  const handleInputChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => {
      const value = e.target.value;
      setQuery(value);
      search(value);
    },
    [search],
  );

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Escape') {
        e.preventDefault();
        onClose();
        return;
      }
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev < results.length - 1 ? prev + 1 : 0));
        return;
      }
      if (e.key === 'ArrowUp') {
        e.preventDefault();
        setSelectedIndex((prev) => (prev > 0 ? prev - 1 : results.length - 1));
        return;
      }
      if (e.key === 'Enter' && selectedIndex >= 0 && selectedIndex < results.length) {
        e.preventDefault();
        onSelect(results[selectedIndex]!);
        onClose();
        return;
      }
    },
    [results, selectedIndex, onSelect, onClose],
  );

  const handleOverlayClick = useCallback(
    (e: React.MouseEvent) => {
      if (e.target === overlayRef.current) {
        onClose();
      }
    },
    [onClose],
  );

  if (!isOpen) return null;

  const hasQuery = query.trim().length > 0;

  return (
    <div
      ref={overlayRef}
      className="modal-overlay"
      style={styles.overlay}
      onClick={handleOverlayClick}
      data-testid="search-popup-overlay"
    >
      <div className="card modal-card" style={styles.modal} data-testid="search-popup-modal">
        <div style={styles.inputRow}>
          <span style={styles.searchIcon}><IconFrame><SearchIcon /></IconFrame></span>
          <input
            className="input-field"
            ref={inputRef}
            type="text"
            value={query}
            onChange={handleInputChange}
            onKeyDown={handleKeyDown}
            placeholder="Type to search directories..."
            style={styles.input}
            data-testid="search-popup-input"
            aria-label="Search directories"
            role="combobox"
            aria-expanded={hasQuery}
            aria-haspopup="listbox"
          />
          {loading && <span style={styles.spinner}><IconFrame><SpinnerIcon /></IconFrame></span>}
        </div>

        <div ref={listRef} style={styles.resultsList} role="listbox" data-testid="search-popup-results">
          {error && (
            <div style={styles.message} data-testid="search-popup-error">
              {error}
            </div>
          )}

          {!error && !hasQuery && (
            <div style={styles.message} data-testid="search-popup-placeholder">
              Type to search...
            </div>
          )}

          {!error && hasQuery && !loading && results.length === 0 && (
            <div style={styles.message} data-testid="search-popup-empty">
              No results found
            </div>
          )}

          {results.map((result, i) => (
            <div
              key={result.path}
              data-role="search-result-item"
              style={{
                ...styles.resultItem,
                background: i === selectedIndex ? 'var(--surface-accent)' : 'transparent',
              }}
              onClick={() => {
                onSelect(result);
                onClose();
              }}
              onMouseEnter={() => setSelectedIndex(i)}
              role="option"
              aria-selected={i === selectedIndex}
              data-testid={`search-popup-result-${i}`}
            >
              <span style={styles.resultName}>{result.name}</span>
              <span style={styles.resultPath}>{result.path}</span>
            </div>
          ))}
        </div>

        <div style={styles.footer}>
          <span style={styles.footerHint}>
            ↑↓ navigate · ↵ select · esc close
          </span>
        </div>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    alignItems: 'flex-start',
    paddingTop: '15vh',
  },
  modal: {
    maxWidth: 560,
    maxHeight: '60vh',
    overflow: 'hidden',
  },
  inputRow: {
    display: 'flex',
    alignItems: 'center',
    padding: '12px 16px',
    boxShadow: '0 1px 0 var(--border)',
    gap: 10,
    position: 'relative',
  },
  searchIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  },
  input: {
    flex: 1,
    background: 'transparent',
    border: 'none',
    color: 'var(--text)',
    fontSize: 15,
    outline: 'none',
    padding: 0,
    boxShadow: 'none',
  },
  spinner: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--accent)',
    flexShrink: 0,
  },
  resultsList: {
    flex: 1,
    overflowY: 'auto',
    padding: '4px 0',
  },
  message: {
    padding: '24px 20px',
    textAlign: 'center',
    color: 'var(--text-muted)',
    fontSize: 13,
  },
  resultItem: {
    display: 'flex',
    flexDirection: 'column',
    gap: 2,
    padding: '10px 20px',
    cursor: 'pointer',
    transition: 'background 0.1s',
  },
  resultName: {
    fontSize: 14,
    fontWeight: 600,
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
  },
  resultPath: {
    fontSize: 12,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
    wordBreak: 'break-all',
  },
  footer: {
    padding: '8px 20px',
    boxShadow: '0 -1px 0 var(--border)',
    display: 'flex',
    justifyContent: 'center',
  },
  footerHint: {
    fontSize: 11,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-sans)',
    letterSpacing: 0.3,
  },
};
