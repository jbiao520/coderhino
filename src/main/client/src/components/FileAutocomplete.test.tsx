import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import FileAutocomplete from '../components/FileAutocomplete';
import type { FileNode } from '../types/api';

const makeFileNode = (name: string, path: string, isDirectory = false): FileNode => ({
  name,
  path,
  isDirectory,
  size: 0,
  lastModified: '',
});

const mockItems: FileNode[] = [
  makeFileNode('src', 'src', true),
  makeFileNode('CLAUDE.md', 'CLAUDE.md'),
  makeFileNode('pom.xml', 'pom.xml'),
  makeFileNode('Main.java', 'src/main/java/Main.java'),
];

const mockPosition = { top: 100, left: 50, lineHeight: 20 };

function renderAutocomplete(props = {}) {
  const textareaRef = { current: document.createElement('textarea') };
  const defaults = {
    isVisible: true,
    items: mockItems,
    selectedIndex: -1,
    onSelect: vi.fn(),
    onHover: vi.fn(),
    position: mockPosition,
    loading: false,
    textareaRef,
  };
  return render(<FileAutocomplete {...defaults} {...props} />);
}

describe('FileAutocomplete', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders nothing when isVisible is false', () => {
    renderAutocomplete({ isVisible: false });
    expect(screen.queryByTestId('file-autocomplete')).toBeNull();
  });

  it('renders nothing when position is null', () => {
    renderAutocomplete({ position: null });
    expect(screen.queryByTestId('file-autocomplete')).toBeNull();
  });

  it('renders items when visible with position', () => {
    renderAutocomplete();
    expect(screen.getByTestId('file-autocomplete')).toBeTruthy();
    expect(screen.getByTestId('file-autocomplete-list')).toBeTruthy();
  });

  it('shows file and folder icons', () => {
    renderAutocomplete();
    const items = screen.getAllByRole('option');
    expect(items[0]?.querySelector('svg')).toBeTruthy();
    expect(items[1]?.querySelector('svg')).toBeTruthy();
  });

  it('shows file paths', () => {
    renderAutocomplete();
    expect(screen.getByText('src')).toBeTruthy();
    expect(screen.getByText('CLAUDE.md')).toBeTruthy();
    expect(screen.getByText('pom.xml')).toBeTruthy();
    expect(screen.getByText('src/main/java/Main.java')).toBeTruthy();
  });

  it('shows loading state', () => {
    renderAutocomplete({ items: [], loading: true });
    expect(screen.getByTestId('file-autocomplete-loading')).toBeTruthy();
    expect(screen.getByText('Loading files…')).toBeTruthy();
  });

  it('shows empty state when no items match', () => {
    renderAutocomplete({ items: [], loading: false });
    expect(screen.getByTestId('file-autocomplete-empty')).toBeTruthy();
    expect(screen.getByText('No files found')).toBeTruthy();
  });

  it('highlights selected item', () => {
    renderAutocomplete({ selectedIndex: 0 });
    const item = screen.getByTestId('file-autocomplete-item-0');
    expect(item).toHaveAttribute('aria-selected', 'true');
  });

  it('calls onSelect when item is clicked', () => {
    const onSelect = vi.fn();
    renderAutocomplete({ onSelect });

    fireEvent.click(screen.getByTestId('file-autocomplete-item-1'));

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'CLAUDE.md' }),
    );
  });

  it('calls onHover when item is hovered', () => {
    const onHover = vi.fn();
    renderAutocomplete({ onHover });

    fireEvent.mouseEnter(screen.getByTestId('file-autocomplete-item-2'));

    expect(onHover).toHaveBeenCalledWith(2);
  });

  it('renders with aria roles for accessibility', () => {
    renderAutocomplete();
    const list = screen.getByTestId('file-autocomplete-list');
    expect(list).toHaveAttribute('role', 'listbox');

    const items = screen.getAllByRole('option');
    expect(items).toHaveLength(4);
  });
});
