import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, beforeEach } from 'vitest';
import FilePanel from './FilePanel';

function stubWorkspaceRect(panel: HTMLElement) {
  Object.defineProperty(panel.parentElement, 'getBoundingClientRect', {
    configurable: true,
    value: () => ({
      x: 120,
      y: 0,
      top: 0,
      left: 120,
      bottom: 800,
      right: 1020,
      width: 900,
      height: 800,
      toJSON: () => ({}),
    }),
  });
}

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

  it('keeps the panel anchored to the workspace edge when resizing', () => {
    render(
      <div data-testid="workspace" style={{ width: 900 }}>
        <FilePanel isOpen>
          <div>panel content</div>
        </FilePanel>
      </div>,
    );

    const panel = screen.getByTestId('file-panel');
    const handle = screen.getByTestId('file-panel-drag-handle');

    stubWorkspaceRect(panel);

    fireEvent.mouseDown(handle, { clientX: 760 });
    fireEvent.mouseMove(document, { clientX: 700 });
    fireEvent.mouseUp(document);

    expect(panel).toHaveStyle({ marginLeft: 'auto' });
    expect(panel).toHaveStyle({ width: '320px' });
  });

  it('narrows the panel when dragging the handle rightward', () => {
    render(
      <div data-testid="workspace" style={{ width: 900 }}>
        <FilePanel isOpen>
          <div>panel content</div>
        </FilePanel>
      </div>,
    );

    const panel = screen.getByTestId('file-panel');
    const handle = screen.getByTestId('file-panel-drag-handle');

    stubWorkspaceRect(panel);

    fireEvent.mouseDown(handle, { clientX: 700 });
    fireEvent.mouseMove(document, { clientX: 740 });
    fireEvent.mouseUp(document);

    expect(panel).toHaveStyle({ width: '280px' });
    expect(panel).toHaveStyle({ marginLeft: 'auto' });
  });

  it('widens the panel when dragging the handle leftward', () => {
    render(
      <div data-testid="workspace" style={{ width: 900 }}>
        <FilePanel isOpen>
          <div>panel content</div>
        </FilePanel>
      </div>,
    );

    const panel = screen.getByTestId('file-panel');
    const handle = screen.getByTestId('file-panel-drag-handle');

    stubWorkspaceRect(panel);

    fireEvent.mouseDown(handle, { clientX: 700 });
    fireEvent.mouseMove(document, { clientX: 660 });
    fireEvent.mouseUp(document);

    expect(panel).toHaveStyle({ width: '360px' });
    expect(panel).toHaveStyle({ marginLeft: 'auto' });
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
