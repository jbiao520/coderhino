import React, { useState } from 'react';
import type { ToolActivity } from '../hooks/useStreamingSession';
import { CheckIcon, ChevronDownIcon, ChevronUpIcon, IconFrame, SpinnerIcon } from './Icons';

interface ToolActivityPaneProps {
  tools: ToolActivity[];
}

function ToolCard({ tool }: { tool: ToolActivity }) {
  const [expanded, setExpanded] = useState(false);
  const done = tool.output !== undefined;

  return (
    <div className="card card-soft" style={styles.card} data-testid={`tool-card-${tool.toolName}`}>
      <button
        className="btn"
        style={styles.cardHeader(done)}
        onClick={() => setExpanded((e) => !e)}
        aria-expanded={expanded}
      >
        <span style={styles.toolIcon(done)}>
          <IconFrame>{done ? <CheckIcon /> : <SpinnerIcon />}</IconFrame>
        </span>
        <span style={styles.toolName}>{tool.toolName}</span>
        <span style={styles.chevron}><IconFrame size={10}>{expanded ? <ChevronUpIcon size={10} /> : <ChevronDownIcon size={10} />}</IconFrame></span>
      </button>

      {expanded && (
        <div style={styles.cardBody}>
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

export default function ToolActivityPane({ tools }: ToolActivityPaneProps) {
  const [collapsed, setCollapsed] = useState(false);

  if (tools.length === 0) return null;

  return (
    <div style={styles.pane} data-testid="tool-activity-pane">
      <button className="btn" style={styles.paneHeader} onClick={() => setCollapsed((c) => !c)}>
        <span style={styles.paneTitle}>Tool Activity</span>
        <span style={styles.paneCount}>{tools.length}</span>
        <span style={styles.chevron}><IconFrame size={10}>{collapsed ? <ChevronDownIcon size={10} /> : <ChevronUpIcon size={10} />}</IconFrame></span>
      </button>
      {!collapsed && (
        <div style={styles.paneBody}>
          {tools.map((tool) => (
            <ToolCard key={tool.toolName} tool={tool} />
          ))}
        </div>
      )}
    </div>
  );
}

const styles = {
  pane: {
    boxShadow: 'var(--shadow-md)',
    borderRadius: 'var(--radius-md)',
    background: 'var(--surface)',
    overflow: 'hidden',
  } as React.CSSProperties,
  paneHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    width: '100%',
    padding: '10px 16px',
    background: 'transparent',
    border: 'none',
    color: 'var(--text)',
    fontSize: 13,
    fontWeight: 600,
    textAlign: 'left' as const,
  } as React.CSSProperties,
  paneTitle: {
    flex: 1,
    color: 'var(--accent)',
  } as React.CSSProperties,
  paneCount: {
    background: 'var(--accent-soft)',
    color: 'var(--accent)',
    borderRadius: 10,
    padding: '1px 7px',
    fontSize: 11,
    fontWeight: 600,
  } as React.CSSProperties,
  paneBody: {
    padding: '0 12px 12px',
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  card: {
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    overflow: 'hidden',
  } as React.CSSProperties,
  cardHeader: (done: boolean) =>
    ({
      display: 'flex',
      alignItems: 'center',
      gap: 8,
      width: '100%',
      padding: '8px 12px',
       background: done ? 'var(--green-soft)' : 'var(--accent-soft)',
       border: 'none',
       color: 'var(--text)',
       fontSize: 12,
       textAlign: 'left' as const,
     }) as React.CSSProperties,
  toolIcon: (done: boolean) =>
    ({
      fontSize: 13,
      width: 16,
      textAlign: 'center' as const,
      flexShrink: 0,
       color: done ? 'var(--green)' : 'var(--accent)',
     }) as React.CSSProperties,
  toolName: {
    flex: 1,
    fontWeight: 600,
    color: 'var(--text)',
    fontFamily: 'var(--font-mono)',
  } as React.CSSProperties,
  chevron: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  cardBody: {
    padding: '10px 12px',
    background: 'var(--bg)',
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 10,
  } as React.CSSProperties,
  section: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
  } as React.CSSProperties,
  sectionLabel: {
    fontSize: 10,
    fontWeight: 700,
    textTransform: 'uppercase' as const,
    letterSpacing: 1,
    color: 'var(--text-muted)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  code: {
    margin: 0,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
    color: 'var(--text)',
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-all' as const,
    maxHeight: 160,
    overflowY: 'auto' as const,
  } as React.CSSProperties,
};
