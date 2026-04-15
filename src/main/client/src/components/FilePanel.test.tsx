import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import FilePanel from './FilePanel';

describe('FilePanel', () => {
  beforeEach(() => {
    const store = new Map<string, string>();
    Object.defineProperty(globalThis, 'localStorage', {
      configurable: true,
      value: {
        getItem: (key: string) => store.get(key) ?? null,
        setItem: (key: string, value: string) => {
          store.set(key, value);
        },
        removeItem: (key: string) => {
          store.delete(key);
        },
      },
    });
    try {
      localStorage.removeItem('claude-file-panel-width-v2');
      localStorage.removeItem('claude-file-panel-width');
    } catch {
    }
  });

  it('renders with a custom panel test id', () => {
    render(
      <FilePanel isOpen panelTestId="shared-side-panel">
        <div>panel content</div>
      </FilePanel>,
    );

    expect(screen.getByTestId('shared-side-panel')).toBeTruthy();
  });

  it('restores panel width from localStorage', () => {
    localStorage.setItem('claude-file-panel-width-v2', '360');

    render(
      <FilePanel isOpen>
        <div>panel content</div>
      </FilePanel>,
    );

    const panel = screen.getByTestId('file-panel');
    expect(panel).toHaveStyle({ width: '360px' });
  });

  it('supports resizing with drag handle', () => {
    render(
      <FilePanel isOpen>
        <div>panel content</div>
      </FilePanel>,
    );

    const panel = screen.getByTestId('file-panel');
    const handle = screen.getByTestId('file-panel-drag-handle');

    fireEvent.mouseDown(handle, { clientX: 900 });
    fireEvent.mouseMove(document, { clientX: 640 });
    fireEvent.mouseUp(document);

    expect(panel).toHaveStyle({ width: '384px' });
  });

  it('uses the narrower default width', () => {
    render(
      <FilePanel isOpen>
        <div>panel content</div>
      </FilePanel>,
    );

    expect(screen.getByTestId('file-panel')).toHaveStyle({ width: '320px' });
  });

  it('clamps restored width to new bounds', () => {
    localStorage.setItem('claude-file-panel-width-v2', '999');

    render(
      <FilePanel isOpen>
        <div>panel content</div>
      </FilePanel>,
    );

    expect(screen.getByTestId('file-panel')).toHaveStyle({ width: '640px' });
  });
});
