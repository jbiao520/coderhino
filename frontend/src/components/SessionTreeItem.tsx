import { CloseIcon, IconFrame, StatusActiveIcon, StatusIdleIcon } from './Icons';

interface SessionTreeItemProps {
  sessionId: string;
  label?: string;
  status: string;
  isActive: boolean;
  isPendingDelete?: boolean;
  onClick: () => void;
  onDelete?: () => void;
  onDeleteConfirm?: () => void;
  onDeleteCancel?: () => void;
}

export default function SessionTreeItem({
  sessionId,
  label,
  status,
  isActive,
  isPendingDelete,
  onClick,
  onDelete,
  onDeleteConfirm,
  onDeleteCancel,
}: SessionTreeItemProps) {
  const shortId = sessionId.length > 8 ? sessionId.slice(0, 8) + '…' : sessionId;

  return (
    <div
      style={{
        ...styles.row,
        ...(isActive ? styles.active : {}),
      }}
    >
      <button
        type="button"
        className="btn"
        style={styles.item}
        onClick={onClick}
        data-testid={`sidebar-session-${sessionId}`}
      >
        <span style={styles.id}>{label || shortId}</span>
        <span style={status === 'ACTIVE' ? styles.statusActive : styles.statusIdle}>
          <IconFrame size={8}>{status === 'ACTIVE' ? <StatusActiveIcon size={8} /> : <StatusIdleIcon size={8} />}</IconFrame>
        </span>
      </button>
      {isPendingDelete ? (
        <div style={styles.deleteConfirm}>
          <button
            type="button"
            style={styles.deleteConfirmBtn}
            onClick={(event) => {
              event.stopPropagation();
              onDeleteConfirm?.();
            }}
            data-testid={`sidebar-session-confirm-delete-${sessionId}`}
            title="Confirm delete"
            aria-label="Confirm delete"
          >
            Delete
          </button>
          <button
            type="button"
            style={styles.cancelBtn}
            onClick={(event) => {
              event.stopPropagation();
              onDeleteCancel?.();
            }}
            data-testid={`sidebar-session-cancel-delete-${sessionId}`}
            title="Cancel"
            aria-label="Cancel delete"
          >
            Cancel
          </button>
        </div>
      ) : onDelete ? (
        <button
          type="button"
          style={styles.deleteBtn}
          onClick={(event) => {
            event.stopPropagation();
            onDelete();
          }}
          data-testid={`sidebar-session-delete-${sessionId}`}
          title="Delete session"
          aria-label="Delete session"
        >
          <IconFrame size={14}><CloseIcon size={14} /></IconFrame>
        </button>
      ) : null}
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  row: {
    display: 'flex',
    alignItems: 'center',
    width: '100%',
    paddingRight: 8,
    gap: 6,
    borderRadius: 'var(--radius-md)',
  },
  item: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    flex: 1,
    minWidth: 0,
    padding: '5px 0 5px 28px',
    background: 'transparent',
    border: 'none',
    textAlign: 'left' as const,
    transition: 'background 0.12s',
  },
  active: {
    background: 'var(--surface-accent)',
  },
  id: {
    fontSize: 'calc(var(--sidebar-font-size) - 1px)',
    fontFamily: 'var(--sidebar-font-family)',
    color: 'var(--text)',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
    minWidth: 0,
    flex: 1,
  },
  statusActive: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--green)',
    flexShrink: 0,
  },
  statusIdle: {
    display: 'inline-flex',
    alignItems: 'center',
    justifyContent: 'center',
    color: 'var(--text-muted)',
    flexShrink: 0,
  },
  deleteBtn: {
    border: 'none',
    background: 'transparent',
    color: 'var(--text-muted)',
    width: 20,
    height: 20,
    padding: 0,
    lineHeight: 1,
    borderRadius: 'var(--radius-sm)',
    fontFamily: 'var(--font-sans)',
    flexShrink: 0,
    cursor: 'pointer',
  },
  deleteConfirm: {
    display: 'flex',
    alignItems: 'center',
    gap: 4,
    flexShrink: 0,
  },
  deleteConfirmBtn: {
    border: 'none',
    background: 'var(--red)',
    color: 'white',
    fontSize: 'calc(var(--sidebar-font-size) - 3px)',
    fontWeight: 600,
    padding: '2px 6px',
    lineHeight: 1,
    borderRadius: 'var(--radius-sm)',
    fontFamily: 'var(--sidebar-font-family)',
    cursor: 'pointer',
  },
  cancelBtn: {
    border: 'none',
    background: 'transparent',
    color: 'var(--text-muted)',
    fontSize: 'calc(var(--sidebar-font-size) - 3px)',
    fontWeight: 500,
    padding: '2px 4px',
    lineHeight: 1,
    borderRadius: 'var(--radius-sm)',
    fontFamily: 'var(--sidebar-font-family)',
    cursor: 'pointer',
  },
};
