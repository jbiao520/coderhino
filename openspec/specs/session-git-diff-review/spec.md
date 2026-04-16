## Purpose
Support reviewing file-specific git changes in the web session UI, including diff display, context expansion, and full-file comparison.
## Requirements
### Requirement: Open file diff from session git panel
The system SHALL allow the user to select a tracked change from the session git panel and open a full-size popup that displays the diff for that file in the active session worktree.

#### Scenario: Open diff for a tracked file
- **WHEN** the session git panel lists a tracked file `src/main/client/src/components/SessionGitPanel.tsx` and the user clicks that file entry
- **THEN** the system opens a popup overlay for git diff review
- **THEN** the popup requests the diff for `src/main/client/src/components/SessionGitPanel.tsx` in the active session worktree
- **THEN** the popup displays the returned diff content in a scrollable review area

### Requirement: Diff popup communicates loading and failure states
The system SHALL communicate the diff request state inside the popup so the user can tell whether the selected file is loading, unavailable, or failed to load.

#### Scenario: Show loading state while fetching diff
- **WHEN** the user opens a tracked file diff and the request is still in progress
- **THEN** the popup remains visible
- **THEN** the popup shows a loading indicator instead of stale diff content

#### Scenario: Show fetch failure in popup
- **WHEN** the diff request for a tracked file fails
- **THEN** the popup remains open
- **THEN** the popup shows an error message explaining that the diff could not be loaded

#### Scenario: Show empty diff result in popup
- **WHEN** the diff request succeeds but git returns no diff content for the selected tracked file
- **THEN** the popup shows an explicit empty-state message for that file instead of a blank viewer

### Requirement: Popup supports focused review workflow
The system SHALL make the diff popup usable for extended review without forcing the user to leave the chat page.

#### Scenario: Close popup after reviewing
- **WHEN** the user closes the diff popup using the close control or overlay escape behavior
- **THEN** the popup is dismissed
- **THEN** the chat page and session git panel remain in their previous state

#### Scenario: Review long diff content
- **WHEN** the selected file diff contains content taller or wider than the popup viewport
- **THEN** the popup provides scrollable diff content without truncating lines or collapsing whitespace

### Requirement: Backend returns file-specific session diff
The backend SHALL expose an API that returns the git diff text for one tracked file path within the resolved session worktree.

#### Scenario: Request diff for tracked file
- **WHEN** the client sends a session-scoped diff request for a tracked file path in a git worktree
- **THEN** the backend responds with the file path and diff text generated from that worktree

#### Scenario: Reject diff request outside git worktree
- **WHEN** the client sends a session-scoped diff request for a session whose resolved worktree is not a git repository
- **THEN** the backend responds with an error indicating that git diff review is unavailable for that worktree

### Requirement: Open diff review for tracked and unversioned session files
The system SHALL allow users to open the session git diff review popup for both tracked changes and unversioned files listed in the session git panel.

#### Scenario: Open tracked file diff review
- **WHEN** the session git panel lists a tracked file and the user selects it
- **THEN** the system opens the git diff review popup for that file
- **THEN** the system loads and displays the file's diff content

#### Scenario: Open unversioned file diff review
- **WHEN** the session git panel lists an unversioned file and the user selects it
- **THEN** the system opens the git diff review popup for that file
- **THEN** the system loads and displays a diff view that represents the unversioned file as newly added content

#### Scenario: Reject non-openable git status entry
- **WHEN** the client requests git diff review for a path that is neither a tracked file nor an unversioned file in the session worktree
- **THEN** the server rejects the request with an error response

### Requirement: Show file-type icons in session git panel
The session git panel SHALL render an icon before each tracked and unversioned file name based on the file type represented by the path.

#### Scenario: Render icon for tracked source file
- **WHEN** the session git panel shows a tracked file with a recognized source-file extension such as `.ts`, `.tsx`, or `.java`
- **THEN** the panel displays a file-type icon before the file name

#### Scenario: Render default icon for unknown file type
- **WHEN** the session git panel shows a file whose extension is not mapped to a specialized icon
- **THEN** the panel displays the default file icon before the file name

### Requirement: Side-by-side git diff viewer
The git diff review popup SHALL present diff content in a two-column side-by-side viewer that aligns removed lines on the left and added lines on the right.

#### Scenario: Review modified tracked file
- **WHEN** the popup displays a diff for a modified tracked file
- **THEN** removed or previous lines appear in the left column
- **THEN** added or current lines appear in the right column
- **THEN** unchanged context lines remain aligned across both columns

#### Scenario: Review newly added unversioned file
- **WHEN** the popup displays a diff for an unversioned file
- **THEN** the left column renders an empty prior state
- **THEN** the right column renders the current file contents as added lines

#### Scenario: Diff content cannot be parsed for split view
- **WHEN** the popup receives diff content that cannot be rendered in the side-by-side viewer
- **THEN** the system shows a fallback state that makes the diff content or failure explicit instead of rendering a broken layout

### Requirement: Full-viewport git diff popup
The git diff review popup SHALL use the available viewport more aggressively than the standard modal sizing so users can review larger diffs without opening another tool.

#### Scenario: Open popup on desktop viewport
- **WHEN** the user opens git diff review on a desktop-sized viewport
- **THEN** the popup expands to near full-screen width and height within the browser viewport
- **THEN** the diff viewer area scrolls internally for overflow content

#### Scenario: Open popup on smaller viewport
- **WHEN** the user opens git diff review on a smaller viewport
- **THEN** the popup remains within the visible viewport bounds
- **THEN** the diff viewer remains usable without content clipping outside the screen

### Requirement: Session git panel visually differentiates change types
The session git panel SHALL render each tracked and unversioned git entry with status-aware visual treatment so users can quickly distinguish change kinds without relying on a plain file list.

#### Scenario: Render modified tracked file with distinct change treatment
- **WHEN** the session git panel shows a tracked file whose normalized status includes `modified`
- **THEN** the row displays a change-type icon or badge for the modified state
- **THEN** the row uses a distinct visual accent from added, deleted, and unversioned entries

#### Scenario: Render deleted tracked file with distinct change treatment
- **WHEN** the session git panel shows a tracked file whose normalized status includes `deleted`
- **THEN** the row displays a change-type icon or badge for the deleted state
- **THEN** the row uses a distinct visual accent from modified, added, and unversioned entries

#### Scenario: Render unversioned file with distinct change treatment
- **WHEN** the session git panel shows an unversioned file entry
- **THEN** the row displays change-type metadata indicating that the file is unversioned or new
- **THEN** the row uses a distinct visual accent from tracked modified and deleted entries

### Requirement: Session git panel preserves file-type context alongside change state
The session git panel SHALL present file-type metadata and git change metadata together for each git entry.

#### Scenario: Render file-type icon and change-type metadata together
- **WHEN** the session git panel shows a file with a recognized extension such as `.tsx` or `.md`
- **THEN** the row displays the file-type icon next to the file path
- **THEN** the row also displays separate change-type metadata without replacing the file-type icon

#### Scenario: Render fallback file-type icon with change metadata
- **WHEN** the session git panel shows a file whose extension is not mapped to a specialized icon
- **THEN** the row displays the default file icon
- **THEN** the row still displays the change-type metadata for the git entry

### Requirement: Expand session git diff context on demand
The system SHALL allow the user to request additional surrounding lines for the currently selected file while reviewing a session git diff.

#### Scenario: Expand tracked file diff context
- **WHEN** the user opens git diff review for a tracked file and requests more context
- **THEN** the system keeps the popup open for the same file
- **THEN** the system loads a new diff response with more surrounding lines than the current view
- **THEN** the popup displays the expanded diff content after loading completes

#### Scenario: Expand unversioned file diff context
- **WHEN** the user opens git diff review for an unversioned file and requests more context
- **THEN** the system keeps the popup open for the same file
- **THEN** the system loads a diff response that includes additional surrounding lines from the file content when available
- **THEN** the popup updates to the expanded diff view without closing or switching files

### Requirement: Diff popup communicates context expansion availability
The system SHALL show whether additional diff context can still be requested for the selected file.

#### Scenario: More diff context is available
- **WHEN** the popup shows a diff response that indicates more surrounding lines are available
- **THEN** the popup displays an action that allows the user to request more context

#### Scenario: Diff is fully expanded
- **WHEN** the popup shows a diff response that indicates no more surrounding lines are available
- **THEN** the popup does not present an active expand action for that file

### Requirement: Backend accepts session diff context requests
The backend SHALL accept file-specific session diff requests with an explicit context size and return the applied context metadata with the diff payload.

#### Scenario: Request file diff with custom context
- **WHEN** the client requests session git diff review for a valid file path and includes a context size
- **THEN** the backend responds with the file path, diff text, and the applied context size for that file

#### Scenario: Request file diff with no remaining context
- **WHEN** the client requests session git diff review for a valid file path and the response already includes the full available context
- **THEN** the backend indicates in the response that no more context is available for additional expansion

