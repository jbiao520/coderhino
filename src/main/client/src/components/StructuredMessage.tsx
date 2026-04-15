import React, { useMemo, useState } from 'react';
import RichMessageContent from './RichMessageContent';
import { parseStructuredMessage } from '../utils/structuredMessage';

interface StructuredMessageProps {
  text: string;
  showCursor?: boolean;
}

export default function StructuredMessage({ text, showCursor = false }: StructuredMessageProps) {
  const structured = useMemo(() => parseStructuredMessage(text), [text]);
  const [showDetails, setShowDetails] = useState(false);

  if (!structured.isStructured) {
    return (
      <div style={styles.messageBody}>
        <RichMessageContent text={structured.plainText} />
        {showCursor ? <span style={styles.cursor} data-testid="structured-message-cursor" /> : null}
      </div>
    );
  }

  return (
    <div style={styles.structuredBody} data-testid="structured-message-body">
      {structured.summary ? (
        <div style={styles.summaryBlock} data-testid="structured-summary">
          {structured.summary}
        </div>
      ) : null}
      {structured.overviewText.length > 0 ? (
        <div style={styles.sectionBody}>
          <RichMessageContent text={structured.overviewText} />
        </div>
      ) : null}
      {structured.sections.map((section) => {
        if (section.collapsible) {
          return (
            <div key={section.title} style={styles.sectionCard} data-testid="brainstorming-section">
              <div style={styles.sectionTitle}>{section.title}</div>
              <button
                type="button"
                className="btn btn-ghost"
                style={styles.detailsToggle}
                onClick={() => setShowDetails((current) => !current)}
                data-testid="brainstorming-toggle"
              >
                {showDetails ? 'Hide Details' : 'Show Details'}
              </button>
              {showDetails ? (
                <div style={styles.sectionBody} data-testid="brainstorming-content">
                  <RichMessageContent text={section.content} />
                </div>
              ) : null}
            </div>
          );
        }

        return (
          <div key={section.title} style={styles.sectionCard} data-testid={`structured-section-${slug(section.title)}`}>
            <div style={styles.sectionTitle}>{section.title}</div>
            <div style={styles.sectionBody}>
              <RichMessageContent text={section.content} />
            </div>
          </div>
        );
      })}
      {showCursor ? <span style={styles.cursor} data-testid="structured-message-cursor" /> : null}
    </div>
  );
}

function slug(value: string): string {
  return value.toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-+|-+$/g, '');
}

const styles = {
  messageBody: {
    paddingLeft: 24,
  } as React.CSSProperties,
  structuredBody: {
    paddingLeft: 24,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 12,
  } as React.CSSProperties,
  summaryBlock: {
    fontFamily: 'var(--font-sans)',
    fontSize: 16,
    lineHeight: 1.5,
    fontWeight: 700,
    color: 'var(--text)',
  } as React.CSSProperties,
  sectionCard: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
    padding: '12px 14px',
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    background: 'color-mix(in srgb, var(--surface) 78%, transparent)',
  } as React.CSSProperties,
  sectionTitle: {
    fontFamily: 'var(--font-sans)',
    fontSize: 13,
    lineHeight: 1.4,
    fontWeight: 700,
    color: 'var(--text)',
  } as React.CSSProperties,
  sectionBody: {
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 8,
  } as React.CSSProperties,
  detailsToggle: {
    alignSelf: 'flex-start',
    padding: '4px 10px',
    borderRadius: 'var(--radius-sm)',
    border: '1px solid var(--border)',
    color: 'var(--text-muted)',
    fontSize: 12,
    fontWeight: 600,
  } as React.CSSProperties,
  cursor: {
    display: 'inline-block',
    width: 8,
    height: 14,
    background: 'var(--accent)',
    verticalAlign: 'text-bottom',
    marginLeft: 2,
    borderRadius: 1,
  } as React.CSSProperties,
};
