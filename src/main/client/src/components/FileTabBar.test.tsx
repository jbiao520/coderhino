import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import FileTabBar from '../components/FileTabBar';
import type { OpenFile } from '../hooks/useOpenFiles';
import { InfoIcon, PackageIcon } from '../components/Icons';

const mockFiles: OpenFile[] = [
  { path: 'src/a.ts', name: 'a.ts', content: null, loading: false },
  { path: 'src/b.ts', name: 'b.ts', content: null, loading: true },
];

function renderTabBar(props = {}) {
  const defaults = {
    openFiles: mockFiles,
    activeTabId: 'src/a.ts',
    onSelectTab: vi.fn(),
    onCloseTab: vi.fn(),
  };
  return render(
    <MemoryRouter>
      <FileTabBar {...defaults} {...props} />
    </MemoryRouter>,
  );
}

describe('FileTabBar', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    window.HTMLElement.prototype.scrollIntoView = vi.fn();
  });

  it('renders tree tab and file tabs', () => {
    renderTabBar();
    expect(screen.getByText('Tree')).toBeTruthy();
    expect(screen.getByText('a.ts')).toBeTruthy();
    expect(screen.getByText('b.ts')).toBeTruthy();
  });

  it('renders data-testid', () => {
    renderTabBar();
    expect(screen.getByTestId('file-tab-bar')).toBeTruthy();
  });

  it('calls onSelectTab when a tab is clicked', () => {
    const onSelectTab = vi.fn();
    renderTabBar({ onSelectTab });

    fireEvent.click(screen.getByText('Tree'));
    expect(onSelectTab).toHaveBeenCalledWith('tree');

    fireEvent.click(screen.getByText('b.ts'));
    expect(onSelectTab).toHaveBeenCalledWith('src/b.ts');
  });

  it('calls onCloseTab when close button is clicked', () => {
    const onCloseTab = vi.fn();
    renderTabBar({ onCloseTab });

    const closeButtons = screen.getAllByLabelText(/Close /);
    fireEvent.click(closeButtons[0]!);
    expect(onCloseTab).toHaveBeenCalledWith('src/a.ts');
  });

  it('does not render close button for tree tab', () => {
    renderTabBar();
    const treeTab = screen.getByText('Tree').closest('button')!;
    expect(treeTab.querySelector('.tab-close-btn')).toBeNull();
  });

  it('renders file tabs with close buttons', () => {
    renderTabBar();
    expect(screen.getAllByLabelText(/Close /)).toHaveLength(2);
  });

  it('renders extra closable tabs and forwards close events', () => {
    const onSelectTab = vi.fn();
    const onCloseTab = vi.fn();

    renderTabBar({
      activeTabId: 'context',
      onSelectTab,
      onCloseTab,
      extraTabs: [{ id: 'context', label: 'Context', actionLabel: 'Close Context', icon: <InfoIcon /> }],
    });

    fireEvent.click(screen.getByText('Context'));
    expect(onSelectTab).toHaveBeenCalledWith('context');

    fireEvent.click(screen.getByLabelText('Close Context'));
    expect(onCloseTab).toHaveBeenCalledWith('context');
  });

  it('renders multiple auxiliary tabs', () => {
    renderTabBar({
      activeTabId: 'git',
      extraTabs: [
        { id: 'context', label: 'Context', actionLabel: 'Close Context', icon: <InfoIcon /> },
        { id: 'git', label: 'Git', actionLabel: 'Close Git', icon: <PackageIcon /> },
      ],
    });

    expect(screen.getByText('Context')).toBeTruthy();
    expect(screen.getByText('Git')).toBeTruthy();
    expect(screen.getByLabelText('Close Git')).toBeTruthy();
  });

  it('can hide the tree tab for context-only panels', () => {
    renderTabBar({ showTreeTab: false, openFiles: [], extraTabs: [{ id: 'context', label: 'Context' }] });

    expect(screen.queryByText('Tree')).toBeNull();
    expect(screen.getByText('Context')).toBeTruthy();
  });

  it('can render a tree tab action button', () => {
    const onCloseTab = vi.fn();
    renderTabBar({ activeTabId: 'tree', onCloseTab, treeActionLabel: 'Close Files' });

    fireEvent.click(screen.getByLabelText('Close Files'));
    expect(onCloseTab).toHaveBeenCalledWith('tree');
  });
});
