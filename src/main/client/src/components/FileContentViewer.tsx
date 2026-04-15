import React from 'react';
import type { FileContent } from '../types/api';
import { IconFrame, PackageIcon, WarningIcon } from './Icons';

interface FileContentViewerProps {
  file: FileContent | null;
  loading?: boolean;
}

const KEYWORDS = new Set([
  'public', 'private', 'protected', 'static', 'final', 'abstract', 'class',
  'interface', 'extends', 'implements', 'new', 'this', 'super', 'void',
  'boolean', 'byte', 'char', 'short', 'int', 'long', 'float', 'double',
  'package', 'import', 'throws', 'throw', 'try', 'catch', 'finally',
  'instanceof', 'enum', 'synchronized', 'volatile', 'transient', 'native',
  'assert', 'default',
  'const', 'let', 'var', 'function', 'return', 'if', 'else', 'for',
  'while', 'do', 'switch', 'case', 'break', 'continue', 'typeof',
  'async', 'await', 'yield', 'export', 'from', 'as', 'type',
  'readonly', 'declare', 'namespace', 'module', 'require',
  'true', 'false', 'null', 'undefined',
]);

function highlightLine(raw: string): { text: string; type: string | null }[] {
  const segments: { text: string; type: string | null }[] = [];
  let i = 0;
  const len = raw.length;

  while (i < len) {
    const ch = raw[i] ?? '';
    const next = raw[i + 1] ?? '';

    if (ch === '/' && next === '/') {
      segments.push({ text: raw.slice(i), type: 'comment' });
      return segments;
    }

    if (ch === '/' && next === '*') {
      const end = raw.indexOf('*/', i + 2);
      const closeIdx = end === -1 ? len : end + 2;
      segments.push({ text: raw.slice(i, closeIdx), type: 'comment' });
      i = closeIdx;
      continue;
    }

    if (ch === '"' || ch === "'" || ch === '`') {
      const quote = ch;
      let j = i + 1;
      while (j < len && (raw[j] ?? '') !== quote) {
        if ((raw[j] ?? '') === '\\') j++;
        j++;
      }
      if (j < len) j++;
      segments.push({ text: raw.slice(i, j), type: 'string' });
      i = j;
      continue;
    }

    if (/[0-9]/.test(ch) && (i === 0 || /[^a-zA-Z_]/.test(raw[i - 1] ?? ''))) {
      let j = i;
      while (j < len && /[0-9a-fA-FxXoObBeE_.]/.test(raw[j] ?? '')) j++;
      segments.push({ text: raw.slice(i, j), type: 'number' });
      i = j;
      continue;
    }

    if (/[a-zA-Z_$]/.test(ch)) {
      let j = i;
      while (j < len && /[a-zA-Z0-9_$]/.test(raw[j] ?? '')) j++;
      const word = raw.slice(i, j);
      segments.push({ text: word, type: KEYWORDS.has(word) ? 'keyword' : null });
      i = j;
      continue;
    }

    segments.push({ text: ch, type: null });
    i++;
  }

  return segments;
}

function segmentColor(type: string | null): string {
  switch (type) {
    case 'keyword': return 'var(--accent)';
    case 'string':  return 'var(--green)';
    case 'comment': return 'var(--text-muted)';
    case 'number':  return 'var(--red)';
    default:        return 'var(--text)';
  }
}

function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function Breadcrumbs({ path }: { path: string }) {
  const segments = path.split('/').filter(Boolean);
  return (
    <span style={styles.breadcrumbs} data-testid="file-breadcrumbs">
      {segments.map((seg, idx) => (
        <React.Fragment key={idx}>
          {idx > 0 && <span style={styles.breadcrumbSep}>/</span>}
          <span
            style={
              idx === segments.length - 1
                ? styles.breadcrumbLast
                : styles.breadcrumbSeg
            }
          >
            {seg}
          </span>
        </React.Fragment>
      ))}
    </span>
  );
}

export default function FileContentViewer({ file, loading }: FileContentViewerProps) {
  if (!file) return null;

  if (loading) {
    return (
      <div style={styles.panel} data-testid="file-content-viewer">
        <div style={styles.header}>
          <div style={styles.headerLeft}>
            <Breadcrumbs path={file.path} />
          </div>
        </div>
        <div style={styles.loadingBody}>
          <span style={styles.loadingText}>Loading…</span>
        </div>
      </div>
    );
  }

  const content = file.content ?? null;
  const lines = content ? content.split('\n') : [];
  const lineCountWidth = String(lines.length).length;
  const gutterPad = Math.max(lineCountWidth + 2, 4);

  if (file.binary) {
    return (
      <div style={styles.panel} data-testid="file-content-viewer">
        <div style={styles.header}>
          <div style={styles.headerLeft}>
            <span style={styles.fileName}>{file.name}</span>
          </div>
        </div>
        <div style={styles.binaryBody}>
          <span style={styles.binaryIcon}><IconFrame size={28}><PackageIcon size={28} /></IconFrame></span>
          <p style={styles.binaryText}>Binary file — cannot display</p>
          <p style={styles.binarySize}>{formatSize(file.size)}</p>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.panel} data-testid="file-content-viewer">
      <div style={styles.header}>
        <div style={styles.headerLeft}>
          <Breadcrumbs path={file.path} />
        </div>
      </div>

      {file.truncated && (
        <div style={styles.truncationBanner} data-testid="truncation-warning">
          <span style={styles.truncationIcon}><IconFrame size={14}><WarningIcon size={14} /></IconFrame></span>
          File truncated at 1 MB (actual size: {formatSize(file.size)})
        </div>
      )}

      {content !== null && (
        <div style={styles.codeArea} data-testid="file-code-area">
          <pre style={styles.pre}>
            <code>
              {lines.map((line, idx) => {
                const lineNum = idx + 1;
                return (
                  <div key={idx} style={styles.lineRow}>
                    <span style={styles.lineNumber(gutterPad)}>{lineNum}</span>
                    <span style={styles.lineContent}>
                      {highlightLine(line).map((seg, si) => (
                        <span key={si} style={{ color: segmentColor(seg.type) }}>
                          {seg.text}
                        </span>
                      ))}
                    </span>
                  </div>
                );
              })}
            </code>
          </pre>
        </div>
      )}
    </div>
  );
}

const styles = {
  panel: {
    display: 'flex',
    flexDirection: 'column' as const,
    height: '100%',
    overflow: 'hidden',
  } as React.CSSProperties,

  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '10px 12px',
    boxShadow: '0 1px 0 var(--border)',
    gap: 8,
    flexShrink: 0,
  } as React.CSSProperties,

  headerLeft: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    overflow: 'hidden',
    flex: 1,
    minWidth: 0,
  } as React.CSSProperties,

  breadcrumbs: {
    display: 'flex',
    alignItems: 'center',
    gap: 2,
    overflow: 'hidden',
    fontFamily: 'var(--font-sans)',
    fontSize: 11,
  } as React.CSSProperties,

  breadcrumbSep: {
    color: 'var(--text-muted)',
    opacity: 0.5,
  } as React.CSSProperties,

  breadcrumbSeg: {
    color: 'var(--text-muted)',
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,

  breadcrumbLast: {
    color: 'var(--text)',
    fontWeight: 600,
    whiteSpace: 'nowrap' as const,
  } as React.CSSProperties,

  fileName: {
    fontFamily: 'var(--font-sans)',
    fontSize: 12,
    fontWeight: 600,
    color: 'var(--text)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap' as const,
    flexShrink: 0,
  } as React.CSSProperties,

  truncationBanner: {
    display: 'flex',
    alignItems: 'center',
    gap: 8,
    padding: '8px 12px',
    background: 'rgba(235, 87, 87, 0.08)',
    boxShadow: '0 1px 0 rgba(235, 87, 87, 0.2)',
    color: 'var(--red)',
    fontSize: 11,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,

  truncationIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  } as React.CSSProperties,

  loadingBody: {
    flex: 1,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  } as React.CSSProperties,

  loadingText: {
    color: 'var(--text-muted)',
    fontSize: 13,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,

  binaryBody: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
    alignItems: 'center',
    justifyContent: 'center',
    gap: 8,
    padding: 32,
  } as React.CSSProperties,

  binaryIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
  } as React.CSSProperties,

  binaryText: {
    color: 'var(--text-muted)',
    fontSize: 13,
    fontFamily: 'var(--font-sans)',
    margin: 0,
  } as React.CSSProperties,

  binarySize: {
    color: 'var(--text-muted)',
    fontSize: 11,
    fontFamily: 'var(--font-sans)',
    margin: 0,
    opacity: 0.7,
  } as React.CSSProperties,

  codeArea: {
    flex: 1,
    overflow: 'auto',
    padding: '8px 0',
  } as React.CSSProperties,

  pre: {
    margin: 0,
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
    lineHeight: '18px',
    tabSize: 2,
  } as React.CSSProperties,

  lineRow: {
    display: 'flex',
    paddingLeft: 4,
    paddingRight: 8,
  } as React.CSSProperties,

  lineNumber: (gutterPad: number) =>
    ({
      display: 'inline-block',
      minWidth: gutterPad * 7.2,
      textAlign: 'right' as const,
      paddingRight: 8,
      color: 'var(--text-muted)',
      opacity: 0.6,
      userSelect: 'none' as const,
      flexShrink: 0,
    }) as React.CSSProperties,

  lineContent: {
    color: 'var(--text)',
    whiteSpace: 'pre' as const,
    wordBreak: 'break-all' as const,
  } as React.CSSProperties,
};
