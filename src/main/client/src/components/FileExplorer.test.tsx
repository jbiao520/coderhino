import { render, screen, waitFor, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import FileExplorer from '../components/FileExplorer';

const mockListing = {
  path: '.',
  children: [
    { name: 'src', path: 'src', isDirectory: true, size: 0, lastModified: '1234567890000' },
    { name: 'package.json', path: 'package.json', isDirectory: false, size: 1024, lastModified: '1234567890000' },
  ],
};

function renderExplorer(props = {}) {
  const defaults = {
    projectPath: '/tmp/test-project',
    onFileSelect: vi.fn(),
  };
  return render(
    <MemoryRouter>
      <FileExplorer {...defaults} {...props} />
    </MemoryRouter>,
  );
}

describe('FileExplorer', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('renders file explorer panel', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByTestId('file-explorer')).toBeTruthy());
  });

  it('shows Loading initially, then loads file tree', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer();
    expect(screen.getByText('Loading…')).toBeTruthy();

    await waitFor(() => expect(screen.getByText('src')).toBeTruthy());
    expect(screen.getByText('package.json')).toBeTruthy();
    expect(screen.queryByText('Loading…')).toBeNull();
  });

  it('shows Empty directory when no files', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => ({ path: '.', children: [] }),
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByText('Empty directory')).toBeTruthy());
  });

  it('shows error message on fetch failure', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: false,
      status: 500,
      json: async () => ({}),
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByText(/API GET .* failed/)).toBeTruthy());
  });

  it('renders filter input', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByTestId('file-filter-input')).toBeTruthy());
  });

  it('filters files by name', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByText('src')).toBeTruthy());

    const input = screen.getByTestId('file-filter-input');
    fireEvent.change(input, { target: { value: 'package' } });

    expect(screen.getByText('package.json')).toBeTruthy();
    expect(screen.queryByText('src')).toBeNull();
  });

  it('shows No matching files when filter yields no results', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer();
    await waitFor(() => expect(screen.getByText('src')).toBeTruthy());

    const input = screen.getByTestId('file-filter-input');
    fireEvent.change(input, { target: { value: 'zzzzz' } });

    expect(screen.getByTestId('no-matching-files')).toBeTruthy();
  });

  it('calls onFileSelect on single-click of file', async () => {
    const onFileSelect = vi.fn();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockListing,
    });

    renderExplorer({ onFileSelect });
    await waitFor(() => expect(screen.getByText('package.json')).toBeTruthy());

    fireEvent.click(screen.getByText('package.json'));
    expect(onFileSelect).toHaveBeenCalledTimes(1);
  });

  it('expand and collapse: clicking directory toggles expansion', async () => {
    const srcListing = {
      path: 'src',
      children: [
        { name: 'index.ts', path: 'src/index.ts', isDirectory: false, size: 50, lastModified: '1234567890000' },
      ],
    };
    (globalThis.fetch as ReturnType<typeof vi.fn>)
      .mockResolvedValueOnce({ ok: true, json: async () => mockListing })
      .mockResolvedValueOnce({ ok: true, json: async () => srcListing });

    renderExplorer();
    await waitFor(() => expect(screen.getByText('src')).toBeTruthy());

    expect(screen.getByText('src').closest('[role="treeitem"]')?.querySelector('svg')).toBeTruthy();

    fireEvent.click(screen.getByText('src'));
    await waitFor(() => expect(screen.getByText('index.ts')).toBeTruthy());

    fireEvent.click(screen.getByText('src'));
    expect(screen.queryByText('index.ts')).toBeNull();
  });
});
