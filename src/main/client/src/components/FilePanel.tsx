import { useState, useCallback, useRef, useEffect } from 'react';

const STORAGE_KEY = 'claude-file-panel-width-v2';
const LEGACY_STORAGE_KEY = 'claude-file-panel-width';
const MIN_WIDTH = 220;
const MAX_WIDTH = 640;
const DEFAULT_WIDTH = 320;

function getStoredWidth(): number {
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    const legacyRaw = localStorage.getItem(LEGACY_STORAGE_KEY);
    if (!raw && !legacyRaw) return DEFAULT_WIDTH;
    const val = parseInt(raw ?? legacyRaw ?? '', 10);
    if (isNaN(val)) return DEFAULT_WIDTH;
    const normalized = raw ? val : Math.round(val / 2);
    return Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, normalized));
  } catch {
    return DEFAULT_WIDTH;
  }
}

interface FilePanelProps {
  isOpen: boolean;
  children: React.ReactNode;
  panelTestId?: string;
}

export default function FilePanel({ isOpen, children, panelTestId = 'file-panel' }: FilePanelProps) {
  const [width, setWidth] = useState(getStoredWidth);
  const dragging = useRef(false);

  const handleMouseDown = useCallback((e: React.MouseEvent) => {
    e.preventDefault();
    dragging.current = true;
    document.body.style.cursor = 'col-resize';
    document.body.style.userSelect = 'none';

    const handleMouseMove = (ev: MouseEvent) => {
      if (!dragging.current) return;
      const newWidth = Math.max(MIN_WIDTH, Math.min(MAX_WIDTH, window.innerWidth - ev.clientX));
      setWidth(newWidth);
    };

    const handleMouseUp = () => {
      dragging.current = false;
      document.body.style.cursor = '';
      document.body.style.userSelect = '';
      document.removeEventListener('mousemove', handleMouseMove);
      document.removeEventListener('mouseup', handleMouseUp);
      try {
        localStorage.setItem(STORAGE_KEY, String(width));
      } catch {}
    };

    document.addEventListener('mousemove', handleMouseMove);
    document.addEventListener('mouseup', handleMouseUp);
  }, [width]);

  useEffect(() => {
    if (!dragging.current) {
      try {
        localStorage.setItem(STORAGE_KEY, String(width));
      } catch {}
    }
  }, [width]);

  if (!isOpen) return null;

  return (
    <div style={{ ...styles.panel, width }} data-testid={panelTestId}>
      <div
        style={styles.dragHandle}
        onMouseDown={handleMouseDown}
        data-testid="file-panel-drag-handle"
      />
      <div style={styles.content}>{children}</div>
    </div>
  );
}

const styles = {
  panel: {
    display: 'flex',
    boxShadow: '-2px 0 8px rgba(0, 0, 0, 0.04)',
    background: 'var(--surface)',
    overflow: 'hidden',
    flexShrink: 0,
    position: 'relative' as const,
  } as React.CSSProperties,
  dragHandle: {
    position: 'absolute' as const,
    left: 0,
    top: 0,
    bottom: 0,
    width: 4,
    cursor: 'col-resize',
    zIndex: 10,
  } as React.CSSProperties,
  content: {
    flex: 1,
    display: 'flex',
    flexDirection: 'column' as const,
    overflow: 'hidden',
    marginLeft: 4,
  } as React.CSSProperties,
};
