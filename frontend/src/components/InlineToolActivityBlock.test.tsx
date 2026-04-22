import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import InlineToolActivityBlock from '../components/InlineToolActivityBlock';
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

describe('InlineToolActivityBlock', () => {
  it('renders collapsed by default', () => {
    render(<InlineToolActivityBlock tool={pendingTool} />);

    expect(screen.getByText('glob')).toBeTruthy();
    expect(screen.getByText('Running')).toBeTruthy();
    expect(screen.queryByText('Input')).toBeNull();
  });

  it('expands to show input and output details', () => {
    render(<InlineToolActivityBlock tool={completedTool} />);

    fireEvent.click(screen.getByRole('button', { name: /bash/i }));

    expect(screen.getByText('Input')).toBeTruthy();
    expect(screen.getByText(/ls -la/)).toBeTruthy();
    expect(screen.getByText('Output')).toBeTruthy();
    expect(screen.getByText(/file1\.ts/)).toBeTruthy();
  });
});
