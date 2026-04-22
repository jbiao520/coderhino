import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect } from 'vitest';
import ToolActivityPane from '../components/ToolActivityPane';
import type { ToolActivity } from '../hooks/useStreamingSession';

const pendingTool: ToolActivity = {
  toolName: 'glob',
  input: { pattern: '**/*.ts' },
};

const completedTool: ToolActivity = {
  toolName: 'bash',
  input: { command: 'ls -la' },
  output: 'file1.ts\nfile2.ts',
};

describe('ToolActivityPane', () => {
  it('renders nothing when tools array is empty', () => {
    const { container } = render(<ToolActivityPane tools={[]} />);
    expect(container.firstChild).toBeNull();
  });

  it('renders pane when tools are present', () => {
    render(<ToolActivityPane tools={[pendingTool]} />);
    expect(screen.getByTestId('tool-activity-pane')).toBeTruthy();
    expect(screen.getByText('Tool Activity')).toBeTruthy();
  });

  it('shows tool count badge', () => {
    render(<ToolActivityPane tools={[pendingTool, completedTool]} />);
    expect(screen.getByText('2')).toBeTruthy();
  });

  it('renders a card for each tool', () => {
    render(<ToolActivityPane tools={[pendingTool, completedTool]} />);
    expect(screen.getByTestId('tool-card-glob')).toBeTruthy();
    expect(screen.getByTestId('tool-card-bash')).toBeTruthy();
  });

  it('shows tool names in cards', () => {
    render(<ToolActivityPane tools={[pendingTool, completedTool]} />);
    expect(screen.getByText('glob')).toBeTruthy();
    expect(screen.getByText('bash')).toBeTruthy();
  });

  it('expands card to show input on click', () => {
    render(<ToolActivityPane tools={[completedTool]} />);
    const header = screen.getByRole('button', { name: /bash/i });
    fireEvent.click(header);
    expect(screen.getByText(/ls -la/)).toBeTruthy();
  });

  it('shows output after expanding a completed tool', () => {
    render(<ToolActivityPane tools={[completedTool]} />);
    const header = screen.getByRole('button', { name: /bash/i });
    fireEvent.click(header);
    expect(screen.getByText(/file1\.ts/)).toBeTruthy();
  });

  it('collapses pane when header is clicked', () => {
    render(<ToolActivityPane tools={[pendingTool]} />);
    const paneHeader = screen.getByRole('button', { name: /Tool Activity/ });
    fireEvent.click(paneHeader);
    expect(screen.queryByTestId('tool-card-glob')).toBeNull();
  });

  it('re-expands pane after collapse', () => {
    render(<ToolActivityPane tools={[pendingTool]} />);
    const paneHeader = screen.getByRole('button', { name: /Tool Activity/ });
    fireEvent.click(paneHeader);
    fireEvent.click(paneHeader);
    expect(screen.getByTestId('tool-card-glob')).toBeTruthy();
  });

  it('marks completed tools with checkmark icon', () => {
    render(<ToolActivityPane tools={[completedTool]} />);
    expect(screen.getByTestId('tool-card-bash').querySelector('svg')).toBeTruthy();
  });

  it('marks pending tools with spinner icon', () => {
    render(<ToolActivityPane tools={[pendingTool]} />);
    expect(screen.getByTestId('tool-card-glob').querySelector('svg')).toBeTruthy();
  });
});
