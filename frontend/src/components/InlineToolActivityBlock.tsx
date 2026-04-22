import React, { useState } from 'react';
import type { ToolActivity } from '../hooks/useStreamingSession';
import { CheckIcon, ChevronDownIcon, ChevronUpIcon, IconFrame, SpinnerIcon } from './Icons';

interface InlineToolActivityBlockProps {
  tool: ToolActivity;
  testId?: string;
}

export default function InlineToolActivityBlock({ tool, testId }: InlineToolActivityBlockProps) {
  const [expanded, setExpanded] = useState(false);
  const done = tool.output !== undefined;

  return (
    <div style={styles.block} data-testid={testId ?? `inline-tool-block-${tool.toolName}`}>
      <button
        type="button"
        className="btn"
        style={styles.header(done)}
        onClick={() => setExpanded((value) => !value)}
        aria-expanded={expanded}
      >
        <span style={styles.icon(done)}>
          <IconFrame>{done ? <CheckIcon /> : <SpinnerIcon />}</IconFrame>
        </span>
        <span style={styles.toolName}>{tool.toolName}</span>
        <span style={styles.status}>{done ? 'Completed' : 'Running'}</span>
        <span style={styles.chevron}>
          <IconFrame size={10}>{expanded ? <ChevronUpIcon size={10} /> : <ChevronDownIcon size={10} />}</IconFrame>
        </span>
      </button>

      {expanded && (
        <div style={styles.body}>
          <div style={styles.section}>
            <span style={styles.sectionLabel}>Input</span>
            <pre style={styles.code}>{JSON.stringify(tool.input, null, 2)}</pre>
          </div>
          {done && (
            <div style={styles.section}>
              <span style={styles.sectionLabel}>Output</span>
              <pre style={styles.code}>{tool.output}</pre>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

const styles = {
  block: {
    borderRadius: 'var(--radius-sm)',
    overflow: 'hidden',
    maxWidth: '100%',
    border: '1px solid var(--border)',
  } as React.CSSProperties,
  header: (done: boolean) => ({
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    width: '100%',
    padding: '5px 8px',
    border: 'none',
    background: done ? 'color-mix(in srgb, var(--green-soft) 82%, var(--surface))' : 'color-mix(in srgb, var(--accent-soft) 82%, var(--surface))',
    color: 'var(--text)',
    textAlign: 'left' as const,
    fontSize: 10,
  }) as React.CSSProperties,
  icon: (done: boolean) => ({
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: done ? 'var(--green)' : 'var(--accent)',
    flexShrink: 0,
  }) as React.CSSProperties,
  toolName: {
    flex: 1,
    minWidth: 0,
    fontWeight: 600,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
  } as React.CSSProperties,
  status: {
    display: 'inline-flex',
    alignItems: 'center',
    padding: '1px 5px',
    borderRadius: 999,
    background: 'rgba(255,255,255,0.5)',
    fontSize: 9,
    fontWeight: 700,
    letterSpacing: 0.3,
    textTransform: 'uppercase' as const,
    color: 'var(--text-muted)',
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,
  chevron: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  } as React.CSSProperties,
  body: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 6,
    padding: '6px 8px',
    background: 'var(--surface)',
  } as React.CSSProperties,
  section: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 3,
    minWidth: 0,
  } as React.CSSProperties,
  sectionLabel: {
    fontSize: 9,
    fontWeight: 700,
    textTransform: 'uppercase' as const,
    letterSpacing: 0.8,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  code: {
    margin: 0,
    fontFamily: 'var(--font-mono)',
    fontSize: 9,
    lineHeight: 1.35,
    color: 'var(--text)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
    overflowX: 'auto' as const,
    maxHeight: 128,
  } as React.CSSProperties,
};
