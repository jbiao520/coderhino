import type React from 'react';

interface SessionGitFullFileCompareViewProps {
  previousContent: string | null;
  currentContent: string | null;
}

interface CompareLine {
  leftLineNumber: number | null;
  rightLineNumber: number | null;
  leftText: string;
  rightText: string;
  leftType: 'context' | 'remove' | 'empty';
  rightType: 'context' | 'add' | 'empty';
}

function computeLineDiff(previousContent: string | null, currentContent: string | null): CompareLine[] {
  const prevLines = previousContent !== null ? previousContent.split('\n') : [];
  const currLines = currentContent !== null ? currentContent.split('\n') : [];

  const result: CompareLine[] = [];
  let leftLine = 1;
  let rightLine = 1;

  const maxLen = Math.max(prevLines.length, currLines.length);
  for (let i = 0; i < maxLen; i++) {
    const prevLine = i < prevLines.length ? prevLines[i]! : null;
    const currLine = i < currLines.length ? currLines[i]! : null;

    if (prevLine === null) {
      result.push({
        leftLineNumber: null,
        rightLineNumber: rightLine,
        leftText: '',
        rightText: currLine ?? '',
        leftType: 'empty',
        rightType: 'add',
      });
      rightLine++;
    } else if (currLine === null) {
      result.push({
        leftLineNumber: leftLine,
        rightLineNumber: null,
        leftText: prevLine,
        rightText: '',
        leftType: 'remove',
        rightType: 'empty',
      });
      leftLine++;
    } else if (prevLine === currLine) {
      result.push({
        leftLineNumber: leftLine,
        rightLineNumber: rightLine,
        leftText: prevLine,
        rightText: currLine,
        leftType: 'context',
        rightType: 'context',
      });
      leftLine++;
      rightLine++;
    } else {
      result.push({
        leftLineNumber: leftLine,
        rightLineNumber: null,
        leftText: prevLine,
        rightText: '',
        leftType: 'remove',
        rightType: 'empty',
      });
      leftLine++;
      result.push({
        leftLineNumber: null,
        rightLineNumber: rightLine,
        leftText: '',
        rightText: currLine,
        leftType: 'empty',
        rightType: 'add',
      });
      rightLine++;
    }
  }

  return result;
}

function lineRowStyle(type: CompareLine['leftType'] | CompareLine['rightType']): React.CSSProperties {
  const background = type === 'add'
    ? 'rgba(15, 123, 108, 0.12)'
    : type === 'remove'
      ? 'rgba(230, 70, 70, 0.12)'
      : 'transparent';
  return {
    ...styles.lineRow,
    background,
  };
}

export default function SessionGitFullFileCompareView({
  previousContent,
  currentContent,
}: SessionGitFullFileCompareViewProps) {
  if (previousContent === null && currentContent === null) {
    return (
      <div style={styles.placeholder}>
        <pre style={styles.rawText}>No file content available for comparison.</pre>
      </div>
    );
  }

  const lines = computeLineDiff(previousContent, currentContent);

  return (
    <div style={styles.viewer} data-testid="session-git-full-file-compare-view">
      <div style={styles.columns}>
        <div style={styles.column} data-testid="session-git-full-file-compare-left-column">
          <div style={styles.columnHeader}>Previous (HEAD)</div>
          {lines.map((line, lineIndex) => (
            <div key={`left-${lineIndex}`} style={lineRowStyle(line.leftType)}>
              <span style={styles.lineNumber}>{line.leftLineNumber ?? ''}</span>
              <code style={styles.lineText}>{line.leftText || ' '}</code>
            </div>
          ))}
        </div>
        <div style={styles.column} data-testid="session-git-full-file-compare-right-column">
          <div style={styles.columnHeader}>Current (Worktree)</div>
          {lines.map((line, lineIndex) => (
            <div key={`right-${lineIndex}`} style={lineRowStyle(line.rightType)}>
              <span style={styles.lineNumber}>{line.rightLineNumber ?? ''}</span>
              <code style={styles.lineText}>{line.rightText || ' '}</code>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  viewer: {
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
    padding: 20,
    overflow: 'auto',
    maxHeight: 'calc(100vh - 120px)',
  },
  placeholder: {
    padding: 20,
  },
  rawText: {
    margin: 0,
    whiteSpace: 'pre-wrap',
    fontFamily: 'var(--font-mono)',
    fontSize: 12,
    color: 'var(--text-muted)',
  },
  columns: {
    display: 'grid',
    gridTemplateColumns: 'minmax(0, 1fr) minmax(0, 1fr)',
    gap: 12,
    minWidth: 0,
  },
  column: {
    border: '1px solid var(--border)',
    borderRadius: 'var(--radius-md)',
    overflow: 'hidden',
    minWidth: 0,
  },
  columnHeader: {
    padding: '8px 10px',
    fontSize: 11,
    fontWeight: 600,
    textTransform: 'uppercase' as const,
    letterSpacing: '0.08em',
    color: 'var(--text-muted)',
    background: 'var(--bg-secondary)',
    borderBottom: '1px solid var(--border)',
  },
  lineRow: {
    display: 'grid',
    gridTemplateColumns: '56px minmax(0, 1fr)',
    gap: 10,
    padding: '4px 10px',
    minHeight: 24,
    alignItems: 'start',
  },
  lineNumber: {
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
    color: 'var(--text-muted)',
    textAlign: 'right',
  },
  lineText: {
    fontFamily: 'var(--font-mono)',
    fontSize: 12,
    lineHeight: 1.5,
    color: 'var(--text)',
    whiteSpace: 'pre-wrap',
    wordBreak: 'break-word',
  },
};
