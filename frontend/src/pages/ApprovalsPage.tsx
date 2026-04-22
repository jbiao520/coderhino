import { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api/client';
import { CheckIcon, IconFrame } from '../components/Icons';
import type { ApprovalRecord } from '../types/api';

export default function ApprovalsPage() {
  const [approvals, setApprovals] = useState<ApprovalRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [currentSessionId, setCurrentSessionId] = useState<string | null>(null);
  const [resolving, setResolving] = useState<Set<string>>(new Set());

  useEffect(() => {
    setLoading(false);
    setError(null);
    setApprovals([]);
  }, []);

  const fetchForSession = async (sessionId: string) => {
    setLoading(true);
    setError(null);
    try {
      const data = await api.sessions.approvals(sessionId);
      setApprovals(data);
      setCurrentSessionId(sessionId);
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to load approvals');
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (approvalId: string) => {
    if (!currentSessionId) return;
    setResolving((prev) => new Set([...prev, approvalId]));
    try {
      const result = await api.sessions.approveApproval(currentSessionId, approvalId);
      setApprovals((prev) =>
        prev.map((a) => (a.approvalId === approvalId ? result.approval : a)),
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to approve');
    } finally {
      setResolving((prev) => {
        const next = new Set(prev);
        next.delete(approvalId);
        return next;
      });
    }
  };

  const handleDeny = async (approvalId: string) => {
    if (!currentSessionId) return;
    setResolving((prev) => new Set([...prev, approvalId]));
    try {
      const result = await api.sessions.denyApproval(currentSessionId, approvalId);
      setApprovals((prev) =>
        prev.map((a) => (a.approvalId === approvalId ? result.approval : a)),
      );
    } catch (err: unknown) {
      setError(err instanceof Error ? err.message : 'Failed to deny');
    } finally {
      setResolving((prev) => {
        const next = new Set(prev);
        next.delete(approvalId);
        return next;
      });
    }
  };

  return (
    <div style={styles.page}>
      <div style={styles.header}>
        <h1 style={styles.title}>Approvals</h1>
        <p style={styles.subtitle}>
          Tool approval requests are per-session.{' '}
          <Link to="/sessions" style={styles.link}>
            Go to Sessions
          </Link>{' '}
          to view a session and manage its pending approvals.
        </p>
      </div>

      <div className="card" style={styles.infoCard}>
        <span style={styles.infoIcon}><IconFrame size={22}><CheckIcon size={22} /></IconFrame></span>
        <div>
          <p style={styles.infoTitle}>Session-scoped approvals</p>
          <p style={styles.infoBody}>
            Approval requests appear within their session context. Select a session from the sidebar
            to view pending approvals inline.
          </p>
        </div>
      </div>

      <div style={styles.devPanel} data-testid="approvals-dev-panel">
        <p style={styles.devLabel}>Quick lookup by Session ID</p>
        <form
          style={styles.devForm}
          onSubmit={(e) => {
            e.preventDefault();
            const fd = new FormData(e.currentTarget);
            const sid = (fd.get('sessionId') as string)?.trim();
            if (sid) fetchForSession(sid);
          }}
        >
          <input
            name="sessionId"
            placeholder="Session ID…"
            className="input-field"
            style={styles.devInput}
            data-testid="session-id-lookup"
          />
          <button type="submit" className="btn btn-primary" style={styles.devBtn}>
            Fetch
          </button>
        </form>

        {loading && <p style={styles.stateText}>Loading…</p>}
        {error && <p style={styles.errorText}>{error}</p>}

        {!loading && approvals.length > 0 && (
          <ul style={styles.list} role="list">
            {approvals.map((a) => {
              const isPending = a.status === 'PENDING';
              const isResolving = resolving.has(a.approvalId);
              return (
                <li key={a.approvalId} style={styles.approvalItem} data-testid={`approval-item-${a.approvalId}`}>
                  <div style={styles.approvalHeader}>
                    <code style={styles.approvalId}>{a.approvalId}</code>
                    <span
                      style={styles.approvalStatus(a.status)}
                      data-testid={`approval-status-${a.approvalId}`}
                    >
                      {a.status}
                    </span>
                  </div>
                  <p style={styles.approvalAction}>{a.action}</p>
                  <p style={styles.approvalSummary}>{a.summary}</p>
                  {isPending && (
                    <div style={styles.actionRow}>
                      <button
                        className="btn btn-primary"
                        style={styles.approveBtn}
                        disabled={isResolving}
                        onClick={() => handleApprove(a.approvalId)}
                        data-testid={`approve-btn-${a.approvalId}`}
                      >
                        {isResolving ? 'Working…' : 'Approve'}
                      </button>
                      <button
                        className="btn btn-danger"
                        style={styles.denyBtn}
                        disabled={isResolving}
                        onClick={() => handleDeny(a.approvalId)}
                        data-testid={`deny-btn-${a.approvalId}`}
                      >
                        {isResolving ? 'Working…' : 'Deny'}
                      </button>
                    </div>
                  )}
                </li>
              );
            })}
          </ul>
        )}
      </div>
    </div>
  );
}

const styles = {
  page: {
    padding: '32px 40px',
    fontFamily: 'var(--font-sans)',
    color: 'var(--text)',
    maxWidth: 800,
  } as React.CSSProperties,
  header: {
    marginBottom: 28,
  } as React.CSSProperties,
  title: {
    fontSize: 22,
    fontWeight: 600,
    margin: '0 0 6px',
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  subtitle: {
    fontSize: 13,
    color: 'var(--text-muted)',
    margin: 0,
    lineHeight: 1.6,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  link: {
    color: 'var(--accent)',
    textDecoration: 'none',
  } as React.CSSProperties,
  infoCard: {
    display: 'flex',
    gap: 16,
    padding: '20px 24px',
    marginBottom: 24,
  } as React.CSSProperties,
  infoIcon: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--green)',
    flexShrink: 0,
  } as React.CSSProperties,
  infoTitle: {
    fontSize: 14,
    fontWeight: 600,
    margin: '0 0 4px',
    color: 'var(--text)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  infoBody: {
    fontSize: 13,
    color: 'var(--text-muted)',
    margin: 0,
    lineHeight: 1.6,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  devPanel: {
    background: 'var(--surface)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    padding: '20px 24px',
  } as React.CSSProperties,
  devLabel: {
    fontSize: 13,
    fontWeight: 500,
    color: 'var(--text-muted)',
    margin: '0 0 10px',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  devForm: {
    display: 'flex',
    gap: 10,
    marginBottom: 16,
  } as React.CSSProperties,
  devInput: {
    padding: '7px 12px',
    fontSize: 13,
  } as React.CSSProperties,
  devBtn: {
    padding: '7px 16px',
    fontSize: 13,
    fontWeight: 600,
  } as React.CSSProperties,
  stateText: {
    fontSize: 13,
    color: 'var(--text-muted)',
    margin: 0,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  errorText: {
    fontSize: 13,
    color: 'var(--red)',
    margin: 0,
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  list: {
    listStyle: 'none',
    padding: 0,
    margin: 0,
    display: 'flex',
    flexDirection: 'column' as const,
    gap: 10,
  } as React.CSSProperties,
  approvalItem: {
    background: 'var(--bg)',
    boxShadow: 'var(--shadow-sm)',
    borderRadius: 'var(--radius-md)',
    padding: '12px 16px',
  } as React.CSSProperties,
  approvalHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 6,
  } as React.CSSProperties,
  approvalId: {
    fontFamily: 'var(--font-mono)',
    fontSize: 11,
    color: 'var(--text-muted)',
  } as React.CSSProperties,
  approvalStatus: (status: string) => ({
    fontSize: 11,
    fontWeight: 600,
    padding: '2px 8px',
    borderRadius: 'var(--radius-sm)',
    background:
      status === 'PENDING'
        ? 'rgba(35,131,226,0.12)'
        : status === 'APPROVED'
          ? 'rgba(15,123,108,0.12)'
          : 'rgba(235,87,87,0.12)',
    color:
      status === 'PENDING' ? 'var(--accent)' : status === 'APPROVED' ? 'var(--green)' : 'var(--red)',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties),
  approvalAction: {
    fontSize: 13,
    fontWeight: 500,
    color: 'var(--text)',
    margin: '0 0 4px',
    fontFamily: 'var(--font-mono)',
  } as React.CSSProperties,
  approvalSummary: {
    fontSize: 12,
    color: 'var(--text-muted)',
    margin: '0 0 10px',
    fontFamily: 'var(--font-sans)',
  } as React.CSSProperties,
  actionRow: {
    display: 'flex',
    gap: 8,
    marginTop: 4,
  } as React.CSSProperties,
  approveBtn: {
    padding: '5px 14px',
    fontSize: 12,
    fontWeight: 600,
  } as React.CSSProperties,
  denyBtn: {
    padding: '5px 14px',
    fontSize: 12,
    fontWeight: 600,
  } as React.CSSProperties,
};
