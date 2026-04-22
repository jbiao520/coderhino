import React, { useEffect, useRef, useCallback } from 'react';

interface PopupProps {
  isOpen: boolean;
  onClose: () => void;
  title?: string;
  headerContent?: React.ReactNode;
  showCloseButton?: boolean;
  children: React.ReactNode;
  contentStyle?: React.CSSProperties;
  bodyStyle?: React.CSSProperties;
}

export function Popup({
  isOpen,
  onClose,
  title,
  headerContent,
  showCloseButton = true,
  children,
  contentStyle,
  bodyStyle,
}: PopupProps) {
  const overlayRef = useRef<HTMLDivElement>(null);
  const contentRef = useRef<HTMLDivElement>(null);
  const onCloseRef = useRef(onClose);

  useEffect(() => {
    onCloseRef.current = onClose;
  }, [onClose]);

  const handleKeyDown = useCallback(
    (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onCloseRef.current();
        return;
      }
      if (e.key === 'Tab' && contentRef.current) {
        const focusable = contentRef.current.querySelectorAll<HTMLElement>(
          'a[href], button:not([disabled]), textarea, input, select, [tabindex]:not([tabindex="-1"])',
        );
        if (focusable.length === 0) return;
        const first = focusable[0]!;
        const last = focusable[focusable.length - 1]!;
        if (e.shiftKey) {
          if (document.activeElement === first) {
            e.preventDefault();
            last.focus();
          }
        } else {
          if (document.activeElement === last) {
            e.preventDefault();
            first.focus();
          }
        }
      }
    },
    [],
  );

  useEffect(() => {
    if (isOpen) {
      document.addEventListener('keydown', handleKeyDown);
      const preferredFocusable = contentRef.current?.querySelector<HTMLElement>(
        '[data-autofocus="true"], input:not([disabled]), textarea:not([disabled]), select:not([disabled])',
      );
      const fallbackFocusable = contentRef.current?.querySelector<HTMLElement>(
        'button:not([disabled]), a[href], [tabindex]:not([tabindex="-1"])',
      );
      if (preferredFocusable) {
        preferredFocusable.focus();
      } else if (fallbackFocusable) {
        fallbackFocusable.focus();
      } else {
        contentRef.current?.focus();
      }
      return () => document.removeEventListener('keydown', handleKeyDown);
    }
  }, [isOpen, handleKeyDown]);

  if (!isOpen) return null;

  return (
    <div
      ref={overlayRef}
      className="modal-overlay"
      style={styles.overlay}
      onClick={(e) => {
        if (e.target === overlayRef.current) onClose();
      }}
      data-testid="popup-overlay"
    >
      <div
        ref={contentRef}
        className="card modal-card"
        style={{ ...styles.content, ...contentStyle }}
        tabIndex={-1}
        data-testid="popup-content"
      >
        <div className="modal-header" style={styles.header}>
          <div style={styles.headerMain}>
            {headerContent ?? (title ? <h2 style={styles.title}>{title}</h2> : null)}
          </div>
          {showCloseButton && (
            <button
              className="btn btn-ghost"
              style={styles.closeBtn}
              onClick={onClose}
              aria-label="Close popup"
              data-testid="popup-close"
            >
              ✕
            </button>
          )}
        </div>
        <div className="modal-body" style={{ ...styles.body, ...bodyStyle }}>{children}</div>
      </div>
    </div>
  );
}

const styles: Record<string, React.CSSProperties> = {
  overlay: {
    padding: '0 16px',
  },
  content: {
    minWidth: 400,
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    gap: 12,
  },
  headerMain: {
    flex: 1,
    minWidth: 0,
  },
  title: {
    margin: 0,
    fontSize: 16,
    fontWeight: 600,
    color: 'var(--text)',
  },
  closeBtn: {
    fontSize: 16,
    lineHeight: 1,
    minWidth: 32,
    padding: '4px 8px',
  },
  body: {
    color: 'var(--text)',
    fontSize: 14,
  },
};
