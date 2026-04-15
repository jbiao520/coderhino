import { render, screen } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import FileContentViewer from '../components/FileContentViewer';
import type { FileContent } from '../types/api';

const textFile: FileContent = {
  name: 'test.ts',
  path: 'src/test.ts',
  content: 'const x = 1;\n// comment\nconsole.log("hello");',
  size: 100,
  truncated: false,
  binary: false,
};

const binaryFile: FileContent = {
  name: 'image.png',
  path: 'assets/image.png',
  content: null,
  size: 2048,
  truncated: false,
  binary: true,
};

const truncatedFile: FileContent = {
  name: 'large.log',
  path: 'logs/large.log',
  content: 'x'.repeat(100),
  size: 2 * 1024 * 1024,
  truncated: true,
  binary: false,
};

function renderViewer(props: { file: FileContent | null; loading?: boolean }) {
  return render(
    <MemoryRouter>
      <FileContentViewer file={props.file} loading={props.loading} />
    </MemoryRouter>,
  );
}

describe('FileContentViewer', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('renders nothing when file is null', () => {
    renderViewer({ file: null });
    expect(screen.queryByTestId('file-content-viewer')).toBeNull();
  });

  it('renders file name and content for text file', () => {
    renderViewer({ file: textFile });
    expect(screen.getByTestId('file-content-viewer')).toBeTruthy();
    expect(screen.getByTestId('file-breadcrumbs')).toBeTruthy();
    expect(screen.getByTestId('file-code-area')).toBeTruthy();
    expect(screen.getByText('const')).toBeTruthy();
    expect(screen.getByText('console')).toBeTruthy();
  });

  it('renders breadcrumb segments with last segment bold', () => {
    renderViewer({ file: textFile });
    const breadcrumbs = screen.getByTestId('file-breadcrumbs');
    expect(breadcrumbs).toBeTruthy();
    expect(screen.getByText('src')).toBeTruthy();
    expect(screen.getByText('test.ts')).toBeTruthy();
  });

  it('shows loading state', () => {
    renderViewer({ file: textFile, loading: true });
    expect(screen.getByText('Loading…')).toBeTruthy();
  });

  it('shows line numbers', () => {
    renderViewer({ file: textFile });
    expect(screen.getByText('2')).toBeTruthy();
    expect(screen.getByText('3')).toBeTruthy();
  });

  it('shows Binary file — cannot display for binary file', () => {
    renderViewer({ file: binaryFile });
    expect(screen.getByText('Binary file — cannot display')).toBeTruthy();
    expect(screen.getByText('2.0 KB')).toBeTruthy();
  });

  it('shows truncation warning when truncated=true', () => {
    renderViewer({ file: truncatedFile });
    expect(screen.getByTestId('truncation-warning')).toBeTruthy();
  });

  it('does not show truncation warning for non-truncated files', () => {
    renderViewer({ file: textFile });
    expect(screen.queryByTestId('truncation-warning')).toBeNull();
  });
});
