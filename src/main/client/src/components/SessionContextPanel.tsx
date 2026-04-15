import { useEffect, useState } from 'react';
import type React from 'react';
import type { SessionContextDto } from '../types/api';
import { ChevronDownIcon, ChevronRightIcon, IconFrame } from './Icons';

interface SessionContextPanelProps {
  context: SessionContextDto | null;
  loading: boolean;
  error: string | null;
  sessionLabel: string;
}

function renderMetric(value: number | null | undefined): string {
  if (value == null) {
    return 'Unavailable';
  }
  return value.toLocaleString();
}

function rawHistoryTitle(direction: SessionContextDto['rawAiHistory'][number]['direction']): string {
  return direction === 'request' ? 'Request' : 'Response';
}

type RawAiHistoryEntry = SessionContextDto['rawAiHistory'][number];

interface RawAiHistoryGroup {
  key: string;
  request?: RawAiHistoryEntry;
  response?: RawAiHistoryEntry;
}

function groupRawAiHistory(rawAiHistory: SessionContextDto['rawAiHistory']): RawAiHistoryGroup[] {
  const groups: RawAiHistoryGroup[] = [];

  for (let index = 0; index < rawAiHistory.length; index += 1) {
    const entry = rawAiHistory[index];
    const nextEntry = rawAiHistory[index + 1];

    if (!entry) {
      continue;
    }

    if (entry.direction === 'request' && nextEntry?.direction === 'response') {
      groups.push({
        key: `request-response-${entry.timestamp ?? index}-${nextEntry.timestamp ?? index + 1}-${index}`,
        request: entry,
        response: nextEntry,
      });
      index += 1;
      continue;
    }

    groups.push({
      key: `${entry.direction}-${entry.timestamp ?? index}-${index}`,
      request: entry.direction === 'request' ? entry : undefined,
      response: entry.direction === 'response' ? entry : undefined,
    });
  }

  return groups;
}

function groupTitle(group: RawAiHistoryGroup): string {
  if (group.request && group.response) {
    return 'Request + Response';
  }
  if (group.request) {
    return 'Request';
  }
  return 'Response';
}

function groupTimestamp(group: RawAiHistoryGroup): string {
  const timestamp = group.request?.timestamp ?? group.response?.timestamp;
  return timestamp ? new Date(timestamp).toLocaleString() : 'Unknown time';
}

export default function SessionContextPanel({ context, loading, error, sessionLabel }: SessionContextPanelProps) {
  const [expandedEntries, setExpandedEntries] = useState<Record<string, boolean>>({});
  const [expandedSections, setExpandedSections] = useState<Record<string, boolean>>({});
  const summary = context?.summary ?? null;
  const rawAiHistory = context?.rawAiHistory ?? [];
  const rawAiHistoryGroups = groupRawAiHistory(rawAiHistory);
  const sessionTotals = summary?.sessionTotals ?? null;

  useEffect(() => {
    setExpandedEntries({});
    setExpandedSections({});
  }, [summary?.sessionId]);

  function toggleEntry(key: string) {
    setExpandedEntries((prev) => ({ ...prev, [key]: !prev[key] }));
  }

  function toggleSection(key: string) {
    setExpandedSections((prev) => ({ ...prev, [key]: !prev[key] }));
  }

  return (
    <div style={styles.panel} data-testid="session-context-panel">
      <div style={styles.content}>
        <h2 style={styles.heading}>{sessionLabel}</h2>

        {loading && <div className="state-message">Loading context…</div>}
        {error && <div className="state-message error">{error}</div>}

        {!loading && !error && summary && (
          <section style={styles.section} data-testid="session-context-summary">
            <h3 style={styles.sectionTitle}>Session Information</h3>
            <div style={styles.grid}>
              <div style={styles.item}><span style={styles.label}>Model</span><span>{summary.model ?? 'Unavailable'}</span></div>
              <div style={styles.item}><span style={styles.label}>Provider</span><span>{summary.providerId ?? 'Unavailable'}</span></div>
              <div style={styles.item}><span style={styles.label}>Status</span><span>{summary.status}</span></div>
              <div style={styles.item}><span style={styles.label}>Permission</span><span>{summary.permissionMode ?? 'Unavailable'}</span></div>
              <div style={styles.item}><span style={styles.label}>Messages</span><span>{summary.messageCount}</span></div>
              <div style={styles.item}><span style={styles.label}>Created</span><span>{new Date(summary.createdAt).toLocaleString()}</span></div>
            </div>

            <div style={styles.metricSection}>
              <h4 style={styles.metricTitle}>Session Usage</h4>
              {sessionTotals ? (
                <div style={styles.grid}>
                  <div style={styles.item}><span style={styles.label}>Input Tokens</span><span>{renderMetric(sessionTotals.inputTokens)}</span></div>
                  <div style={styles.item}><span style={styles.label}>Output Tokens</span><span>{renderMetric(sessionTotals.outputTokens)}</span></div>
                  <div style={styles.item}><span style={styles.label}>Cache Read</span><span>{renderMetric(sessionTotals.cacheReadTokens)}</span></div>
                  <div style={styles.item}><span style={styles.label}>Cache Write</span><span>{renderMetric(sessionTotals.cacheWriteTokens)}</span></div>
                  <div style={styles.item}><span style={styles.label}>Tool Uses</span><span>{renderMetric(sessionTotals.toolUses)}</span></div>
                </div>
              ) : (
                <div className="state-message">Unavailable</div>
              )}
            </div>
          </section>
        )}

        {!loading && !error && (
          <section style={styles.section} data-testid="session-context-history">
            <h3 style={styles.sectionTitle}>AI History</h3>
            {rawAiHistoryGroups.length === 0 ? (
              <div className="state-message" data-testid="session-context-history-empty">
                No raw AI request or response history yet.
              </div>
            ) : (
              <ul style={styles.historyList}>
                {rawAiHistoryGroups.map((group, index) => {
                  const entryKey = group.key;
                  const expanded = !!expandedEntries[entryKey];
                  const requestKey = `${entryKey}-request`;
                  const responseKey = `${entryKey}-response`;
                  return (
                  <li key={entryKey} style={styles.historyItem} data-testid={`session-context-history-${index}`}>
                    <button
                      type="button"
                      className="btn"
                      onClick={() => toggleEntry(entryKey)}
                      style={styles.historyToggle}
                      data-testid={`session-context-history-toggle-${index}`}
                      aria-expanded={expanded}
                      aria-label={expanded ? `Collapse AI history entry ${index + 1}` : `Expand AI history entry ${index + 1}`}
                    >
                      <div style={styles.historyHead}>
                        <strong>{groupTitle(group)}</strong>
                        <span style={styles.time}>{groupTimestamp(group)}</span>
                      </div>
                      <span style={styles.chevron}>
                        <IconFrame size={10}>{expanded ? <ChevronDownIcon size={10} /> : <ChevronRightIcon size={10} />}</IconFrame>
                      </span>
                    </button>
                    {expanded && (
                      <div style={styles.historyBody}>
                        {group.request?.content && (
                          <div style={styles.payloadSection}>
                            <button
                              type="button"
                              className="btn"
                              onClick={() => toggleSection(requestKey)}
                              style={styles.payloadToggle}
                              aria-expanded={!!expandedSections[requestKey]}
                              aria-label={expandedSections[requestKey] ? `Collapse request for AI history entry ${index + 1}` : `Expand request for AI history entry ${index + 1}`}
                            >
                              <div style={styles.payloadLabel}>{rawHistoryTitle(group.request.direction)}</div>
                              <span style={styles.chevron}>
                                <IconFrame size={10}>{expandedSections[requestKey] ? <ChevronDownIcon size={10} /> : <ChevronRightIcon size={10} />}</IconFrame>
                              </span>
                            </button>
                            {expandedSections[requestKey] && <pre style={styles.contentBlock}>{group.request.content}</pre>}
                          </div>
                        )}
                        {group.response?.content && (
                          <div style={styles.payloadSection}>
                            <button
                              type="button"
                              className="btn"
                              onClick={() => toggleSection(responseKey)}
                              style={styles.payloadToggle}
                              aria-expanded={!!expandedSections[responseKey]}
                              aria-label={expandedSections[responseKey] ? `Collapse response for AI history entry ${index + 1}` : `Expand response for AI history entry ${index + 1}`}
                            >
                              <div style={styles.payloadLabel}>{rawHistoryTitle(group.response.direction)}</div>
                              <span style={styles.chevron}>
                                <IconFrame size={10}>{expandedSections[responseKey] ? <ChevronDownIcon size={10} /> : <ChevronRightIcon size={10} />}</IconFrame>
                              </span>
                            </button>
                            {expandedSections[responseKey] && <pre style={styles.contentBlock}>{group.response.content}</pre>}
                          </div>
                        )}
                      </div>
                    )}
                   </li>
                 )})}
              </ul>
            )}
          </section>
        )}
      </div>
    </div>
  );
}

const styles = {
  panel: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    overflow: 'hidden',
  },
  content: {
    overflow: 'auto' as const,
    padding: 14,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 14,
  } as React.CSSProperties,
  heading: {
    margin: 0,
    fontSize: 14,
    fontFamily: 'var(--font-mono)',
  } as React.CSSProperties,
  section: {
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    padding: 10,
    background: 'var(--surface)',
  } as React.CSSProperties,
  sectionTitle: {
    margin: '0 0 10px',
    fontSize: 12,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.04em',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  metricSection: {
    marginTop: 12,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  metricTitle: {
    margin: 0,
    fontSize: 12,
    color: 'var(--text)',
  } as React.CSSProperties,
  grid: {
    display: 'grid',
    gridTemplateColumns: '1fr',
    gap: 8,
  } as React.CSSProperties,
  item: {
    display: 'flex',
    justifyContent: 'space-between',
    gap: 12,
    fontSize: 12,
  } as React.CSSProperties,
  label: {
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  historyList: {
    listStyle: 'none',
    margin: 0,
    padding: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  historyItem: {
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-sm)',
    padding: 8,
    background: 'var(--bg)',
  } as React.CSSProperties,
  historyHead: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    fontSize: 12,
    flex: 1,
  } as React.CSSProperties,
  time: {
    color: 'var(--text-muted)',
    fontSize: 11,
  } as React.CSSProperties,
  historyToggle: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    width: '100%',
    border: 'none',
    background: 'transparent',
    color: 'var(--text)',
    padding: 0,
    textAlign: 'left' as const,
  } as React.CSSProperties,
  chevron: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  } as React.CSSProperties,
  historyBody: {
    marginTop: 8,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  payloadSection: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 4,
  } as React.CSSProperties,
  payloadToggle: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    width: '100%',
    border: 'none',
    background: 'transparent',
    color: 'var(--text)',
    padding: 0,
    textAlign: 'left' as const,
  } as React.CSSProperties,
  payloadLabel: {
    display: 'flex',
    alignItems: 'center',
    flex: 1,
    fontSize: 10,
    fontWeight: 700,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.08em',
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  contentBlock: {
    margin: 0,
    whiteSpace: 'pre-wrap' as const,
    wordBreak: 'break-word' as const,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
    color: 'var(--text)',
  } as React.CSSProperties,
};
