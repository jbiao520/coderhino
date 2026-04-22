import type React from 'react';

interface SessionGitDiffViewProps {
  diff: string;
}

interface DiffLine {
  leftLineNumber: number | null;
  rightLineNumber: number | null;
  leftText: string;
  rightText: string;
  leftType: 'context' | 'remove' | 'empty';
  rightType: 'context' | 'add' | 'empty';
}

interface ParsedDiff {
  hunks: Array<{
    header: string;
    lines: DiffLine[];
  }>;
}

function parseUnifiedDiff(diff: string): ParsedDiff | null {
  const lines = diff.split('\n');
  const hunks: ParsedDiff['hunks'] = [];
  let currentHunk: ParsedDiff['hunks'][number] | null = null;
  let leftLine = 0;
  let rightLine = 0;

  for (const rawLine of lines) {
    if (rawLine.startsWith('@@')) {
      const match = rawLine.match(/^@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@/);
      if (!match) {
        return null;
      }
      leftLine = Number(match[1]);
      rightLine = Number(match[3]);
      currentHunk = { header: rawLine, lines: [] };
      hunks.push(currentHunk);
      continue;
    }

    if (!currentHunk) {
      continue;
    }

    if (rawLine.startsWith('-')) {
      const pending = currentHunk.lines[currentHunk.lines.length - 1];
      if (pending && pending.leftType === 'remove' && pending.rightType === 'empty') {
        pending.leftText = `${pending.leftText}\n${rawLine.slice(1)}`;
      } else {
        currentHunk.lines.push({
          leftLineNumber: leftLine,
          rightLineNumber: null,
          leftText: rawLine.slice(1),
          rightText: '',
          leftType: 'remove',
          rightType: 'empty',
        });
      }
      leftLine += 1;
      continue;
    }

    if (rawLine.startsWith('+')) {
      const pending = currentHunk.lines[currentHunk.lines.length - 1];
      if (pending && pending.leftType === 'remove' && pending.rightType === 'empty') {
        pending.rightLineNumber = rightLine;
        pending.rightText = rawLine.slice(1);
        pending.rightType = 'add';
      } else if (pending && pending.leftType === 'empty' && pending.rightType === 'add') {
        pending.rightText = `${pending.rightText}\n${rawLine.slice(1)}`;
      } else {
        currentHunk.lines.push({
          leftLineNumber: null,
          rightLineNumber: rightLine,
          leftText: '',
          rightText: rawLine.slice(1),
          leftType: 'empty',
          rightType: 'add',
        });
      }
      rightLine += 1;
      continue;
    }

    if (rawLine.startsWith(' ')) {
      currentHunk.lines.push({
        leftLineNumber: leftLine,
        rightLineNumber: rightLine,
        leftText: rawLine.slice(1),
        rightText: rawLine.slice(1),
        leftType: 'context',
        rightType: 'context',
      });
      leftLine += 1;
      rightLine += 1;
      continue;
    }

    if (rawLine.startsWith('\\')) {
      const lastLine = currentHunk.lines[currentHunk.lines.length - 1];
      if (!lastLine) {
        return null;
      }
      if (lastLine.leftType !== 'empty') {
        lastLine.leftText = `${lastLine.leftText} ${rawLine}`.trim();
      }
      if (lastLine.rightType !== 'empty') {
        lastLine.rightText = `${lastLine.rightText} ${rawLine}`.trim();
      }
      continue;
    }
  }

  return hunks.length > 0 ? { hunks } : null;
}

export default function SessionGitDiffView({ diff }: SessionGitDiffViewProps) {
  const parsed = parseUnifiedDiff(diff);

  if (!parsed) {
    return <pre style={styles.rawDiff} data-testid="session-git-diff-content">{diff}</pre>;
  }

  return (
    <div style={styles.viewer} data-testid="session-git-diff-viewer">
      {parsed.hunks.map((hunk, hunkIndex) => (
        <section key={`${hunk.header}-${hunkIndex}`} style={styles.hunk}>
          <div style={styles.hunkHeader}>{hunk.header}</div>
          <div style={styles.columns}>
            <div style={styles.column} data-testid="session-git-diff-left-column">
              {hunk.lines.map((line, lineIndex) => (
                <div key={`left-${hunkIndex}-${lineIndex}`} style={lineRowStyle(line.leftType)}>
                  <span style={styles.lineNumber}>{line.leftLineNumber ?? ''}</span>
                  <code style={styles.lineText}>{line.leftText || ' '}</code>
                </div>
              ))}
            </div>
            <div style={styles.column} data-testid="session-git-diff-right-column">
              {hunk.lines.map((line, lineIndex) => (
                <div key={`right-${hunkIndex}-${lineIndex}`} style={lineRowStyle(line.rightType)}>
                  <span style={styles.lineNumber}>{line.rightLineNumber ?? ''}</span>
                  <code style={styles.lineText}>{line.rightText || ' '}</code>
                </div>
              ))}
            </div>
          </div>
        </section>
      ))}
    </div>
  );
}

function lineRowStyle(type: DiffLine['leftType'] | DiffLine['rightType']): React.CSSProperties {
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

const styles: Record<string, React.CSSProperties> = {
  viewer: {
    display: 'flex',
    flexDirection: 'column',
    gap: 16,
    padding: 20,
    overflow: 'auto',
    maxHeight: 'calc(100vh - 120px)',
  },
  hunk: {
    display: 'flex',
    flexDirection: 'column',
    gap: 8,
  },
  hunkHeader: {
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
  rawDiff: {
    margin: 0,
    padding: 20,
    overflow: 'auto',
    maxHeight: 'calc(100vh - 120px)',
    whiteSpace: 'pre',
    fontFamily: 'var(--font-mono)',
    fontSize: 12,
    lineHeight: 1.5,
    color: 'var(--text)',
  },
};
