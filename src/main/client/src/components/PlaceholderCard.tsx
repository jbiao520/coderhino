interface PlaceholderCardProps {
  title: string;
  icon: string;
  description: string;
  hint: string;
}

export function PlaceholderCard({ title, icon, description, hint }: PlaceholderCardProps) {
  return (
    <div style={styles.page}>
      <div className="card" style={styles.card}>
        <div style={styles.headerRow}>
          <span style={styles.icon}>{icon}</span>
          <h1 style={styles.title}>{title}</h1>
        </div>
        <p style={styles.description}>{description}</p>
        <div className="card-soft" style={styles.terminal}>
          <div style={styles.terminalBar}>
            <span style={styles.terminalDot} />
            <span style={styles.terminalDot2} />
            <span style={styles.terminalDot3} />
            <span style={styles.terminalLabel}>preview</span>
          </div>
          <div style={styles.terminalBody}>
            <span style={styles.prompt}>$</span>
            <span style={styles.cursor}>code-rhino {title.toLowerCase()}</span>
            <span style={styles.blink}>_</span>
          </div>
        </div>
        <p style={styles.hint}>{hint}</p>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  page: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    padding: 32,
  },
  card: {
    maxWidth: 480,
    width: '100%',
    padding: 32,
  },
  headerRow: {
    display: 'flex',
    alignItems: 'center',
    gap: 12,
    marginBottom: 8,
  },
  icon: {
    fontSize: 24,
  },
  title: {
    fontSize: 20,
    fontWeight: 600,
    color: 'var(--text)',
    margin: 0,
    letterSpacing: -0.3,
  },
  description: {
    fontSize: 14,
    color: 'var(--text-muted)',
    margin: '0 0 20px',
    lineHeight: 1.5,
  },
  terminal: {
    overflow: 'hidden',
    marginBottom: 16,
  },
  terminalBar: {
    display: 'flex',
    alignItems: 'center',
    gap: 6,
    padding: '8px 12px',
    boxShadow: '0 1px 0 var(--border)',
    background: 'var(--surface)',
  },
  terminalDot: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    background: '#ff5f57',
  },
  terminalDot2: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    background: '#febc2e',
  },
  terminalDot3: {
    width: 8,
    height: 8,
    borderRadius: '50%',
    background: '#28c840',
  },
  terminalLabel: {
    fontSize: 11,
    color: 'var(--text-muted)',
    marginLeft: 8,
  },
  terminalBody: {
    padding: '12px 16px',
    fontFamily: 'var(--font-mono)',
    fontSize: 13,
    display: 'flex',
    alignItems: 'center',
    gap: 8,
  },
  prompt: {
    color: 'var(--green)',
    fontWeight: 700,
  },
  cursor: {
    color: 'var(--text)',
  },
  blink: {
    color: 'var(--accent)',
    fontWeight: 700,
    animation: 'blink 1s step-end infinite',
  },
  hint: {
    fontSize: 12,
    color: 'var(--text-muted)',
    margin: 0,
    lineHeight: 1.6,
    opacity: 0.8,
  },
};
