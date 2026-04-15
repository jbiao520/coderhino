import { render, screen, fireEvent } from '@testing-library/react';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { MemoryRouter } from 'react-router-dom';
import ProjectSection from '../components/ProjectSection';

const mockProject = {
  id: '1',
  name: 'test-project',
  path: '/tmp/test-project',
  lastOpened: '2026-04-08T00:00:00Z',
  createdAt: '2026-04-08T00:00:00Z',
};

const mockRecentProjects = [
  mockProject,
  {
    id: '2',
    name: 'old-project',
    path: '/tmp/old-project',
    lastOpened: '2026-04-01T00:00:00Z',
    createdAt: '2026-03-01T00:00:00Z',
  },
];

function renderSection(props = {}) {
  const defaults = {
    activeProject: null,
    recentProjects: [],
    onSetActive: vi.fn(),
    onClearActive: vi.fn(),
    onCreateProject: vi.fn().mockResolvedValue(mockProject),
  };
  return render(
    <MemoryRouter>
      <ProjectSection {...defaults} {...props} />
    </MemoryRouter>,
  );
}

describe('ProjectSection', () => {
  beforeEach(() => {
    globalThis.fetch = vi.fn() as typeof fetch;
  });

  it('renders Project heading', () => {
    renderSection();
    expect(screen.getByText('Project')).toBeTruthy();
  });

  it('renders recent project list from props', () => {
    renderSection({ recentProjects: mockRecentProjects });
    expect(screen.getByText('test-project')).toBeTruthy();
    expect(screen.getByText('old-project')).toBeTruthy();
  });

  it('clicking a recent project calls onSetActive', () => {
    const onSetActive = vi.fn();
    renderSection({ recentProjects: mockRecentProjects, onSetActive });

    fireEvent.click(screen.getByText('test-project'));
    expect(onSetActive).toHaveBeenCalledWith(
      expect.objectContaining({ id: '1', name: 'test-project' }),
    );
  });

  it('shows Close Project button when activeProject is set', () => {
    renderSection({ activeProject: mockProject });
    expect(screen.getByText('test-project')).toBeTruthy();
    expect(screen.getByText('/tmp/test-project')).toBeTruthy();
    expect(screen.getByText('Close Project')).toBeTruthy();
  });

  it('clicking Close Project calls onClearActive', () => {
    const onClearActive = vi.fn();
    renderSection({ activeProject: mockProject, onClearActive });

    fireEvent.click(screen.getByText('Close Project'));
    expect(onClearActive).toHaveBeenCalled();
  });

  it('shows Open Project button when no active project', () => {
    renderSection();
    expect(screen.getByText('Open Project')).toBeTruthy();
  });

  it('does not render recent section when recentProjects is empty', () => {
    renderSection({ recentProjects: [] });
    expect(screen.queryByText('Recent')).toBeNull();
  });
});
