import { render, screen, waitFor, fireEvent, act } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import SearchPopup from '../components/SearchPopup';

const mockResults = [
  { path: '/home/user/my-project', name: 'my-project', matchType: 'EXACT' as const },
  { path: '/home/user/my-app', name: 'my-app', matchType: 'STARTS_WITH' as const },
  { path: '/home/user/my-lib', name: 'my-lib', matchType: 'CONTAINS' as const },
];

function renderPopup(props = {}) {
  const defaults = {
    isOpen: true,
    onClose: vi.fn(),
    onSelect: vi.fn(),
  };
  return render(
    <MemoryRouter>
      <SearchPopup {...defaults} {...props} />
    </MemoryRouter>,
  );
}

async function searchAndWait(query: string) {
  const input = screen.getByTestId('search-popup-input');
  await act(async () => {
    fireEvent.change(input, { target: { value: query } });
  });
  await waitFor(() => {
    expect(screen.getByTestId('search-popup-results')).toBeTruthy();
  }, { timeout: 2000 });
}

describe('SearchPopup', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
    Element.prototype.scrollIntoView = vi.fn();
  });

  it('renders nothing when isOpen=false', () => {
    renderPopup({ isOpen: false });
    expect(screen.queryByTestId('search-popup-overlay')).toBeNull();
  });

  it('renders search input when isOpen=true', () => {
    renderPopup();
    expect(screen.getByTestId('search-popup-input')).toBeTruthy();
  });

  it('shows Type to search... placeholder initially', () => {
    renderPopup();
    expect(screen.getByTestId('search-popup-placeholder')).toBeTruthy();
    expect(screen.getByText('Type to search...')).toBeTruthy();
  });

  it('typing in input triggers search after debounce', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    renderPopup();
    await searchAndWait('my');

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-result-0')).toBeTruthy();
    }, { timeout: 2000 });

    expect(screen.getByText('my-project')).toBeTruthy();
    expect(screen.getByText('my-app')).toBeTruthy();
    expect(screen.getByText('my-lib')).toBeTruthy();
  });

  it('shows No results found when query returns empty', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => [],
    });

    renderPopup();
    const input = screen.getByTestId('search-popup-input');

    await act(async () => {
      fireEvent.change(input, { target: { value: 'xyz' } });
    });

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-empty')).toBeTruthy();
    }, { timeout: 2000 });
    expect(screen.getByText('No results found')).toBeTruthy();
  });

  it('ArrowDown moves selection down', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    renderPopup();
    await searchAndWait('my');

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-result-0')).toBeTruthy();
    }, { timeout: 2000 });

    const input = screen.getByTestId('search-popup-input');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowDown' });
    });
    expect(screen.getByTestId('search-popup-result-0')).toHaveAttribute('aria-selected', 'true');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowDown' });
    });
    expect(screen.getByTestId('search-popup-result-1')).toHaveAttribute('aria-selected', 'true');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowDown' });
    });
    expect(screen.getByTestId('search-popup-result-2')).toHaveAttribute('aria-selected', 'true');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowDown' });
    });
    expect(screen.getByTestId('search-popup-result-0')).toHaveAttribute('aria-selected', 'true');
  });

  it('ArrowUp moves selection up', async () => {
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    renderPopup();
    await searchAndWait('my');

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-result-0')).toBeTruthy();
    }, { timeout: 2000 });

    const input = screen.getByTestId('search-popup-input');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowUp' });
    });
    expect(screen.getByTestId('search-popup-result-2')).toHaveAttribute('aria-selected', 'true');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowUp' });
    });
    expect(screen.getByTestId('search-popup-result-1')).toHaveAttribute('aria-selected', 'true');
  });

  it('Enter on selected item calls onSelect', async () => {
    const onSelect = vi.fn();
    const onClose = vi.fn();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    renderPopup({ onSelect, onClose });
    await searchAndWait('my');

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-result-0')).toBeTruthy();
    }, { timeout: 2000 });

    const input = screen.getByTestId('search-popup-input');

    await act(async () => {
      fireEvent.keyDown(input, { key: 'ArrowDown' });
    });
    await act(async () => {
      fireEvent.keyDown(input, { key: 'Enter' });
    });

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'my-project', path: '/home/user/my-project' }),
    );
    expect(onClose).toHaveBeenCalled();
  });

  it('Escape calls onClose', () => {
    const onClose = vi.fn();
    renderPopup({ onClose });
    const input = screen.getByTestId('search-popup-input');

    fireEvent.keyDown(input, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
  });

  it('clicking result item calls onSelect', async () => {
    const onSelect = vi.fn();
    const onClose = vi.fn();
    (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
      ok: true,
      json: async () => mockResults,
    });

    renderPopup({ onSelect, onClose });
    await searchAndWait('my');

    await waitFor(() => {
      expect(screen.getByTestId('search-popup-result-1')).toBeTruthy();
    }, { timeout: 2000 });

    await act(async () => {
      fireEvent.click(screen.getByTestId('search-popup-result-1'));
    });

    expect(onSelect).toHaveBeenCalledWith(
      expect.objectContaining({ name: 'my-app' }),
    );
    expect(onClose).toHaveBeenCalled();
  });
});
