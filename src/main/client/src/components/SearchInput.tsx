import React, { useState, useCallback, useRef, useEffect } from 'react';
import { IconFrame, SpinnerIcon } from './Icons';

interface SearchInputProps {
  value: string;
  onChange: (v: string) => void;
  results: string[];
  onSelect: (item: string) => void;
  placeholder?: string;
  isLoading?: boolean;
}

export function SearchInput({
  value,
  onChange,
  results,
  onSelect,
  placeholder = 'Search…',
  isLoading = false,
}: SearchInputProps) {
  const [highlightedIndex, setHighlightedIndex] = useState(-1);
  const listRef = useRef<HTMLUListElement>(null);

  useEffect(() => {
    setHighlightedIndex(-1);
  }, [results]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'ArrowDown') {
        e.preventDefault();
        setHighlightedIndex((prev) =>
          prev < results.length - 1 ? prev + 1 : 0,
        );
      } else if (e.key === 'ArrowUp') {
        e.preventDefault();
        setHighlightedIndex((prev) =>
          prev > 0 ? prev - 1 : results.length - 1,
        );
      } else if (e.key === 'Enter' && highlightedIndex >= 0 && highlightedIndex < results.length) {
        e.preventDefault();
        onSelect(results[highlightedIndex]!);
      } else if (e.key === 'Escape') {
        if (value.length > 0) {
          onChange('');
        }
        setHighlightedIndex(-1);
      }
    },
    [results, highlightedIndex, onSelect, value, onChange],
  );

  useEffect(() => {
    if (highlightedIndex >= 0 && listRef.current) {
      const item = listRef.current.children[highlightedIndex] as HTMLElement;
      item?.scrollIntoView({ block: 'nearest' });
    }
  }, [highlightedIndex]);

  return (
    <div style={styles.wrapper} data-testid="search-input-wrapper">
      <div style={styles.inputRow}>
        <input
          type="text"
          value={value}
          onChange={(e) => onChange(e.target.value)}
          onKeyDown={handleKeyDown}
          placeholder={placeholder}
          className="input-field"
          style={styles.input}
          data-testid="search-input"
          aria-label={placeholder}
        />
        {isLoading && <span style={styles.spinner} data-testid="search-spinner"><IconFrame><SpinnerIcon /></IconFrame></span>}
      </div>
      {results.length > 0 && (
        <ul ref={listRef} style={styles.list} data-testid="search-results" role="listbox">
          {results.map((item, i) => (
            <li
              key={item}
              style={{
                ...styles.listItem,
                background: i === highlightedIndex ? 'var(--accent-soft)' : 'transparent',
              }}
              onClick={() => onSelect(item)}
              onMouseEnter={() => setHighlightedIndex(i)}
              role="option"
              aria-selected={i === highlightedIndex}
              data-testid={`search-result-${i}`}
            >
              {item}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  wrapper: {
    position: 'relative',
    width: '100%',
  },
  inputRow: {
    position: 'relative',
    display: 'flex',
    alignItems: 'center',
  },
  input: {
    boxSizing: 'border-box' as const,
  },
  spinner: {
    position: 'absolute' as const,
    right: 12,
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--accent)',
  },
  list: {
    position: 'absolute' as const,
    top: '100%',
    left: 0,
    right: 0,
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    boxShadow: 'var(--shadow-md)',
    borderRadius: 'var(--radius-md)',
    marginTop: 4,
    maxHeight: 240,
    overflowY: 'auto' as const,
    listStyle: 'none',
    margin: '4px 0 0',
    padding: 0,
    zIndex: 100,
  },
  listItem: {
    padding: '8px 12px',
    fontSize: 13,
    color: 'var(--text)',
    cursor: 'pointer',
    transition: 'background 0.1s',
  },
};
