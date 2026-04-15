import { fireEvent, render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import CommandPalette from './CommandPalette';
import type { CommandDto } from '../types/api';

const commands: CommandDto[] = [
  { name: 'review', description: 'Review changes', aliases: ['rev'], webCompatible: true, promptBacked: false },
  { name: 'rewind', description: 'Rewind session', aliases: ['back'], webCompatible: true, promptBacked: false },
  { name: 'vim', description: 'Vim mode', aliases: [], webCompatible: false, promptBacked: false },
];

describe('CommandPalette', () => {
  beforeEach(() => {
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders all commands when visible and query is empty', () => {
    render(
      <CommandPalette commands={commands} query="" onSelect={vi.fn()} onDismiss={vi.fn()} visible />,
    );

    expect(screen.getByTestId('command-palette')).toBeTruthy();
    expect(screen.getAllByRole('option')).toHaveLength(3);
  });

  it('filters commands by name and alias', () => {
    render(
      <CommandPalette commands={commands} query="rev" onSelect={vi.fn()} onDismiss={vi.fn()} visible />,
    );

    expect(screen.getByText('/review')).toBeTruthy();
    expect(screen.queryByText('/rewind')).toBeNull();
    expect(screen.queryByText('/vim')).toBeNull();
  });

  it('supports keyboard navigation and enter selection', () => {
    const onSelect = vi.fn();
    render(
      <CommandPalette commands={commands} query="" onSelect={onSelect} onDismiss={vi.fn()} visible />,
    );

    fireEvent.keyDown(document, { key: 'ArrowDown' });
    fireEvent.keyDown(document, { key: 'Enter' });

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ name: 'rewind' }));
  });

  it('dismisses on escape', () => {
    const onDismiss = vi.fn();
    render(
      <CommandPalette commands={commands} query="" onSelect={vi.fn()} onDismiss={onDismiss} visible />,
    );

    fireEvent.keyDown(document, { key: 'Escape' });

    expect(onDismiss).toHaveBeenCalled();
  });

  it('selects on click and marks non-web-compatible commands', () => {
    const onSelect = vi.fn();
    render(
      <CommandPalette commands={commands} query="vim" onSelect={onSelect} onDismiss={vi.fn()} visible />,
    );

    expect(screen.getByText('Web unavailable')).toBeTruthy();
    fireEvent.click(screen.getByTestId('command-palette-item-0'));

    expect(onSelect).toHaveBeenCalledWith(expect.objectContaining({ name: 'vim', webCompatible: false }));
  });
});
