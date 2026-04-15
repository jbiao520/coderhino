import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import SessionGitPanel from './SessionGitPanel';

describe('SessionGitPanel', () => {
  it('renders tracked changes and unversioned files', () => {
    render(
      <SessionGitPanel
        gitStatus={{
          trackedChanges: [
            { kind: 'tracked', path: 'src/App.tsx', status: 'modified' },
            { kind: 'tracked', path: 'README.md', status: 'staged added' },
            { kind: 'tracked', path: 'src/OldFile.ts', status: 'deleted' },
          ],
          unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }],
        }}
        loading={false}
        error={null}
        sessionLabel="Git"
      />,
    );

    expect(screen.getByTestId('session-git-tracked-changes')).toBeTruthy();
    expect(screen.getByTestId('session-git-unversioned-files')).toBeTruthy();
    expect(screen.getByText('src/App.tsx')).toBeTruthy();
    expect(screen.getByText('notes/todo.md')).toBeTruthy();
    expect(screen.getByTestId('session-git-tracked-change-src-app-tsx')).toHaveAttribute('data-change-category', 'modified');
    expect(screen.getByTestId('session-git-tracked-change-readme-md')).toHaveAttribute('data-change-category', 'added');
    expect(screen.getByTestId('session-git-tracked-change-src-oldfile-ts')).toHaveAttribute('data-change-category', 'deleted');
    expect(screen.getByTestId('session-git-unversioned-file-notes-todo-md')).toHaveAttribute('data-change-category', 'unversioned');
    expect(screen.getByTestId('session-git-change-badge-src-app-tsx')).toHaveTextContent('modified');
    expect(screen.getByTestId('session-git-change-badge-readme-md')).toHaveTextContent('staged added');
    expect(screen.getByTestId('session-git-change-badge-src-oldfile-ts')).toHaveTextContent('deleted');
    expect(screen.getByTestId('session-git-change-badge-notes-todo-md')).toHaveTextContent('unversioned');
    expect(screen.getByTestId('session-git-file-icon-src-app-tsx')).toBeTruthy();
    expect(screen.getAllByText('Tracked change')).toHaveLength(3);
    expect(screen.getByText('Unversioned file')).toBeTruthy();
  });

  it('renders clean state when there are no changes', () => {
    render(
      <SessionGitPanel
        gitStatus={{ trackedChanges: [], unversionedFiles: [] }}
        loading={false}
        error={null}
        sessionLabel="Git"
      />,
    );

    expect(screen.getByTestId('session-git-clean-state')).toBeTruthy();
  });

  it('renders error state', () => {
    render(
      <SessionGitPanel
        gitStatus={null}
        loading={false}
        error="Resolved worktree is not a git repository."
        sessionLabel="Git"
      />,
    );

    expect(screen.getByText('Resolved worktree is not a git repository.')).toBeTruthy();
  });

  it('notifies selection when a tracked file is clicked', () => {
    const onSelectEntry = vi.fn();

    render(
      <SessionGitPanel
        gitStatus={{ trackedChanges: [{ kind: 'tracked', path: 'src/App.tsx', status: 'modified' }], unversionedFiles: [] }}
        loading={false}
        error={null}
        sessionLabel="Git"
        onSelectEntry={onSelectEntry}
      />,
    );

    fireEvent.click(screen.getByTestId('session-git-tracked-change-src-app-tsx'));
    expect(onSelectEntry).toHaveBeenCalledWith({ kind: 'tracked', path: 'src/App.tsx', status: 'modified' });
  });

  it('notifies selection when an unversioned file is clicked', () => {
    const onSelectEntry = vi.fn();

    render(
      <SessionGitPanel
        gitStatus={{ trackedChanges: [], unversionedFiles: [{ kind: 'unversioned', path: 'notes/todo.md' }] }}
        loading={false}
        error={null}
        sessionLabel="Git"
        onSelectEntry={onSelectEntry}
      />,
    );

    fireEvent.click(screen.getByTestId('session-git-unversioned-file-notes-todo-md'));
    expect(onSelectEntry).toHaveBeenCalledWith({ kind: 'unversioned', path: 'notes/todo.md' });
  });
});
