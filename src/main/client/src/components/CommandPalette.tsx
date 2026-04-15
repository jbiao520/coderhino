import React, { useEffect, useMemo, useRef, useState } from 'react';
import type { CommandDto } from '../types/api';

interface CommandPaletteProps {
  commands: CommandDto[];
  query: string;
  onSelect: (command: CommandDto) => void;
  onDismiss: () => void;
  visible: boolean;
}

export default function CommandPalette({ commands, query, onSelect, onDismiss, visible }: CommandPaletteProps) {
  const containerRef = useRef<HTMLDivElement | null>(null);
  const listRef = useRef<HTMLDivElement | null>(null);
  const [highlightedIndex, setHighlightedIndex] = useState(0);

  const filteredCommands = useMemo(() => {
    const commandQuery = query.trim().toLowerCase().split(/\s+/, 1)[0] ?? '';
    if (!commandQuery) {
      return commands;
    }
    return commands.filter((command) =>
      command.name.toLowerCase().includes(commandQuery)
      || command.aliases.some((alias) => alias.toLowerCase().includes(commandQuery)),
    );
  }, [commands, query]);

  useEffect(() => {
    if (!visible) {
      return;
    }
    setHighlightedIndex(0);
  }, [visible, query]);

  useEffect(() => {
    if (!visible) {
      return;
    }
    if (highlightedIndex < filteredCommands.length) {
      return;
    }
    setHighlightedIndex(Math.max(filteredCommands.length - 1, 0));
  }, [filteredCommands, highlightedIndex, visible]);

  useEffect(() => {
    if (!visible) {
      return;
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'ArrowDown') {
        event.preventDefault();
        setHighlightedIndex((current) =>
          filteredCommands.length === 0 ? 0 : (current + 1) % filteredCommands.length,
        );
      } else if (event.key === 'ArrowUp') {
        event.preventDefault();
        setHighlightedIndex((current) =>
          filteredCommands.length === 0
            ? 0
            : (current - 1 + filteredCommands.length) % filteredCommands.length,
        );
      } else if (event.key === 'Enter') {
        if (filteredCommands.length === 0) {
          return;
        }
        event.preventDefault();
        onSelect(filteredCommands[highlightedIndex] ?? filteredCommands[0]!);
      } else if (event.key === 'Escape') {
        event.preventDefault();
        onDismiss();
      }
    };

    const handleClickOutside = (event: MouseEvent) => {
      if (containerRef.current?.contains(event.target as Node)) {
        return;
      }
      onDismiss();
    };

    document.addEventListener('keydown', handleKeyDown);
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('keydown', handleKeyDown);
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [filteredCommands, highlightedIndex, onDismiss, onSelect, visible]);

  useEffect(() => {
    if (!visible || !listRef.current) {
      return;
    }
    const item = listRef.current.querySelector<HTMLElement>(`[data-command-index="${highlightedIndex}"]`);
    item?.scrollIntoView({ block: 'nearest' });
  }, [highlightedIndex, visible]);

  if (!visible) {
    return null;
  }

  return (
    <div ref={containerRef} style={styles.palette} data-testid="command-palette">
      <div style={styles.header}>Slash commands</div>
      {filteredCommands.length === 0 ? (
        <div style={styles.empty} data-testid="command-palette-empty">No matching commands</div>
      ) : (
        <div ref={listRef} style={styles.list} role="listbox" data-testid="command-palette-list">
          {filteredCommands.map((command, index) => {
            const selected = index === highlightedIndex;
            return (
              <button
                key={command.name}
                type="button"
                role="option"
                aria-selected={selected}
                data-command-index={index}
                data-testid={`command-palette-item-${index}`}
                onClick={() => onSelect(command)}
                style={{
                  ...styles.item,
                  ...(selected ? styles.itemSelected : {}),
                  ...(!command.webCompatible ? styles.itemDisabled : {}),
                }}
              >
                <div style={styles.itemTopRow}>
                  <span style={styles.commandName}>/{command.name}</span>
                  {!command.webCompatible && <span style={styles.badge}>Web unavailable</span>}
                </div>
                <div style={styles.description}>{command.description}</div>
                {command.aliases.length > 0 && (
                  <div style={styles.aliases}>Aliases: {command.aliases.join(', ')}</div>
                )}
              </button>
            );
          })}
        </div>
      )}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  palette: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 'calc(100% + 8px)',
    zIndex: 20,
    background: 'var(--surface)',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    boxShadow: 'var(--shadow-lg)',
    overflow: 'hidden',
  },
  header: {
    padding: '10px 12px',
    borderBottom: '1px solid var(--border)',
    color: 'var(--text-muted)',
    fontSize: 12,
    fontWeight: 600,
    letterSpacing: '0.02em',
    textTransform: 'uppercase',
  },
  empty: {
    padding: '16px 12px',
    color: 'var(--text-muted)',
    fontSize: 13,
  },
  list: {
    display: 'flex',
    flexDirection: 'column',
    maxHeight: 280,
    overflowY: 'auto',
  },
  item: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: 4,
    width: '100%',
    padding: '12px',
    border: 'none',
    borderBottom: '1px solid var(--border)',
    background: 'transparent',
    color: 'var(--text)',
    textAlign: 'left',
    cursor: 'pointer',
  },
  itemSelected: {
    background: 'var(--surface-accent)',
  },
  itemDisabled: {
    color: 'var(--text-muted)',
  },
  itemTopRow: {
    display: 'flex',
    width: '100%',
    justifyContent: 'space-between',
    gap: 8,
    alignItems: 'center',
  },
  commandName: {
    fontFamily: 'var(--font-mono)',
    fontSize: 13,
    fontWeight: 600,
  },
  badge: {
    fontSize: 11,
    color: 'var(--orange)',
    background: 'rgba(217, 115, 13, 0.12)',
    borderRadius: '999px',
    padding: '2px 8px',
  },
  description: {
    fontSize: 13,
    lineHeight: 1.4,
  },
  aliases: {
    fontSize: 12,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-mono)',
  },
};
