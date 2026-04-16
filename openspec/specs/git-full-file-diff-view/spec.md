# git-full-file-diff-view Specification

## Purpose
TBD - created by archiving change git-full-file-compare-view. Update Purpose after archive.
## Requirements
### Requirement: Backend returns both previous and current file content for git comparison
The backend SHALL expose an API that returns both the previous (HEAD) and current content of a file path within the resolved session worktree for side-by-side comparison.

#### Scenario: Request full file content for tracked file
- **WHEN** the client sends a session-scoped file content request for a tracked file path in a git worktree with `compare=true`
- **THEN** the backend responds with an object containing `previousContent` (content at HEAD) and `currentContent` (content in worktree)

#### Scenario: Reject full file content request for file not in git worktree
- **WHEN** the client sends a session-scoped file content request for a session whose resolved worktree is not a git repository
- **THEN** the backend responds with an error indicating that git file content comparison is unavailable

#### Scenario: Handle missing previous content for new file
- **WHEN** the client requests full file content comparison for a newly added tracked file
- **THEN** the backend returns `previousContent` as null or empty and `currentContent` as the file's current content

#### Scenario: Handle missing current content for deleted file
- **WHEN** the client requests full file content comparison for a tracked file that was deleted in the worktree
- **THEN** the backend returns `previousContent` as the file's content at HEAD and `currentContent` as null or empty

### Requirement: Full file side-by-side compare view displays complete before/after content
The git diff review popup SHALL display a two-column side-by-side view of the complete file content before and after changes when the user selects the full file compare option.

#### Scenario: Display full file comparison for modified tracked file
- **WHEN** the user selects the full file compare view for a modified tracked file
- **THEN** the left column displays the complete previous file content (at HEAD)
- **THEN** the right column displays the complete current file content (in worktree)
- **THEN** removed or changed lines are highlighted in the left column
- **THEN** added or changed lines are highlighted in the right column
- **THEN** unchanged lines are displayed without highlighting

#### Scenario: Display full file comparison for newly added file
- **WHEN** the user selects the full file compare view for a newly added tracked file
- **THEN** the left column renders an empty or placeholder state
- **THEN** the right column displays the complete current file content as added lines

#### Scenario: User can switch between diff view and full file compare view
- **WHEN** the user is viewing the git diff in diff mode
- **THEN** the user can switch to full file compare view via a toggle or tab
- **WHEN** the user is viewing the full file compare view
- **THEN** the user can switch back to traditional diff view

#### Scenario: Full file compare view is not available for unversioned files
- **WHEN** the user selects an unversioned file in the session git panel
- **THEN** the full file compare option is disabled or hidden
- **THEN** the diff view and single file view remain available

