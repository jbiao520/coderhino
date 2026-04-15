## ADDED Requirements

### Requirement: Multi-project sidebar with session tree
The system SHALL display a sidebar containing all open projects, each showing a collapsible list of its sessions. Selecting a session SHALL navigate to that session's chat view. The sidebar SHALL be visible at all times alongside the chat view. The project icon rail SHALL present the active project as a clearly selected desktop-style object, while inactive project items remain visually lighter and secondary utility actions in the footer remain visually subordinate to project navigation. The sidebar SHALL support a folded mode from the main toolbar that collapses the project/session column to a compact rail while keeping the chat view visible and wider. While expanded, the sidebar width SHALL be adjustable by dragging its right edge. The expanded width SHALL be clamped within implementation-defined minimum and maximum bounds, persisted locally for the browser, and restored on later visits.

#### Scenario: Display open projects with sessions
- **WHEN** the user has projects P1 and P2 open, and P1 has sessions S1, S2 and P2 has session S3
- **THEN** the sidebar shows P1 (expanded) with S1 and S2 listed, and P2 (collapsed)

#### Scenario: Expand a collapsed project
- **WHEN** the user clicks on a collapsed project P2 in the sidebar
- **THEN** P2 expands to show its sessions, and the previous project remains expanded

#### Scenario: Select a session from sidebar
- **WHEN** the user clicks on session S3 under project P2 in the sidebar
- **THEN** the URL updates to `/projects/P2/sessions/S3` and the chat view loads S3

#### Scenario: Show active project with desktop-style selection
- **WHEN** project P1 is the active project in the icon rail
- **THEN** P1 is rendered with a distinct selected-object treatment using stronger structure than inactive projects

#### Scenario: Render lighter inactive project avatars
- **WHEN** the sidebar renders an inactive project avatar in the icon rail
- **THEN** the avatar uses a lighter tinted project color treatment instead of a dark filled button block

#### Scenario: De-emphasize utility actions relative to projects
- **WHEN** the sidebar shows project icons along with footer utility actions such as opening a project or navigating to settings
- **THEN** the footer actions remain available without sharing the same emphasized selected-object treatment used for the active project

#### Scenario: Fold the project sidebar from the toolbar
- **WHEN** the user clicks the fold button in the workspace toolbar while viewing a project chat session
- **THEN** the sidebar collapses to its compact rail state instead of keeping the full project/session column width
- **THEN** the project and session list content is hidden
- **THEN** project actions and settings remain accessible from the compact rail
- **THEN** the chat workspace expands to use the horizontal space freed by the collapsed sidebar

#### Scenario: Resize the expanded project sidebar wider
- **WHEN** the user drags the expanded sidebar's resize handle to the right
- **THEN** the sidebar width increases up to the configured maximum width
- **THEN** the chat workspace shrinks to accommodate the wider sidebar

#### Scenario: Resize the expanded project sidebar narrower
- **WHEN** the user drags the expanded sidebar's resize handle to the left while the sidebar is expanded
- **THEN** the sidebar width decreases down to the configured minimum width
- **THEN** the project/session content remains visible because the sidebar stays expanded

#### Scenario: Restore the expanded sidebar width
- **WHEN** the user reloads the web UI after previously resizing the expanded sidebar
- **THEN** the expanded sidebar renders using the stored width value within the allowed bounds

#### Scenario: Ignore invalid stored expanded widths
- **WHEN** the stored expanded sidebar width is smaller than the minimum or larger than the maximum
- **THEN** the sidebar uses the nearest allowed width instead of the invalid stored value

#### Scenario: Preserve resize preference across folding
- **WHEN** the user resizes the expanded sidebar, folds it to the compact rail, and then expands it again
- **THEN** the folded state uses the compact rail width
- **THEN** the re-expanded sidebar restores the user's last valid expanded width

### Requirement: Open and close projects
The system SHALL allow users to add a project to the sidebar (open it) and remove a project from the sidebar (close it). Closing a project SHALL NOT delete the project or its sessions from the backend.

#### Scenario: Open a new project
- **WHEN** the user adds a project by path or selects it from recent projects
- **THEN** the project appears in the sidebar with its sessions loaded, and becomes the active project

#### Scenario: Close an open project
- **WHEN** the user closes project P1 from the sidebar
- **THEN** P1 and its sessions are removed from the sidebar, and if P1 was active, the most recently used remaining project becomes active

### Requirement: Quick session creation within project context
The system SHALL allow users to create a new session scoped to the currently active project directly from the sidebar without navigating to a separate page.

#### Scenario: Create session in active project
- **WHEN** the user clicks "New Session" while project P1 is active
- **THEN** a new session is created for P1, the session appears in P1's session list in the sidebar, and the chat view navigates to the new session

#### Scenario: Create session with no active project
- **WHEN** the user clicks "New Session" with no project active
- **THEN** a new unscoped session is created and the chat view navigates to it

### Requirement: Persist open projects and active sessions across reloads
The system SHALL persist the set of open project IDs and the active project ID in backend workspace state. The system SHALL persist the last active session ID per project and recent session ordering in localStorage. On page reload or server restart, the system SHALL restore the open projects from backend workspace state and navigate to the most recently relevant persisted session for the restored active project. If the user opens a project during the initial workspace bootstrap window, that project SHALL still be included in the persisted workspace state used by a subsequent browser refresh. The backend SHALL expose a `GET /api/projects/{id}` endpoint so the frontend can validate restored project IDs. If all restored IDs fail validation, the system SHALL fall back to the server's project list (`GET /api/projects`) and auto-open the most recently used project.

#### Scenario: Restore state after page reload
- **WHEN** the user reloads the page with projects P1 and P2 open and the browser has persisted active-session metadata for those projects
- **THEN** both P1 and P2 appear in the sidebar after reload
- **THEN** the restored active project matches the persisted workspace state when it is still valid
- **THEN** the chat view loads the most recently relevant persisted session for the restored project

#### Scenario: Immediate refresh after opening a project
- **WHEN** the user opens project P1 during the web UI's initial workspace-state bootstrap and refreshes the page immediately afterward
- **THEN** P1 remains present in persisted workspace state
- **THEN** the refreshed sidebar restores P1 as an open project instead of dropping back to an empty workspace

#### Scenario: Restore state after server restart
- **WHEN** the server restarts and the user reopens the web UI with projects P1 and P2 previously open
- **THEN** both P1 and P2 appear in the sidebar restored from backend workspace state via `GET /api/projects/{id}`
- **THEN** the chat view loads the last active session available for the restored project state

#### Scenario: Handle deleted project on restore
- **WHEN** the page reloads and project P1 was deleted from the backend since last visit
- **THEN** P1 is silently removed from the restored open projects list
- **THEN** the sidebar shows only the remaining valid projects

#### Scenario: Fallback when restored workspace state is empty or stale
- **WHEN** the page loads and backend workspace state has no valid project IDs
- **THEN** the system calls `GET /api/projects` and auto-opens the most recently used project from the server's response

### Requirement: Project lookup by ID endpoint
The backend SHALL expose a `GET /api/projects/{id}` endpoint that returns the project DTO for a valid ID, or 404 if the project does not exist.

#### Scenario: Lookup existing project
- **WHEN** the client sends `GET /api/projects/{id}` with a valid project ID
- **THEN** the server responds with 200 and the project DTO (id, name, path, lastOpened, createdAt)

#### Scenario: Lookup non-existent project
- **WHEN** the client sends `GET /api/projects/{id}` with an unknown project ID
- **THEN** the server responds with 404

### Requirement: Project workspace state endpoint
The backend SHALL expose project workspace-state endpoints that return and update the persisted set of open projects for the web workspace.

#### Scenario: Load persisted workspace state
- **WHEN** the client sends `GET /api/projects/workspace-state`
- **THEN** the server SHALL respond with the persisted open project IDs and active project ID after filtering out unknown projects

#### Scenario: Update persisted workspace state
- **WHEN** the client sends `PUT /api/projects/workspace-state` with an updated open project list and active project ID
- **THEN** the server SHALL persist the provided valid workspace state and use it for subsequent restore operations

### Requirement: URL-based project and session routing
The system SHALL use URLs in the format `/projects/:projectId/sessions/:sessionId` to identify the active project and session. Navigating directly to such a URL SHALL open the project and load the session.

#### Scenario: Direct URL navigation
- **WHEN** the user navigates to `/projects/P1/sessions/S1`
- **THEN** project P1 is opened in the sidebar, P1's sessions are loaded, and S1 is displayed in the chat view

#### Scenario: Legacy session URL redirect
- **WHEN** the user navigates to `/sessions/S1` and S1 is associated with project P1
- **THEN** the browser redirects to `/projects/P1/sessions/S1`

#### Scenario: Legacy session URL without project
- **WHEN** the user navigates to `/sessions/S1` and S1 has no associated project
- **THEN** the session loads in project-less mode with the chat view displayed

### Requirement: Keyboard shortcut for session switching
The system SHALL provide keyboard shortcuts to switch between recent sessions across projects.

#### Scenario: Switch to next session
- **WHEN** the user presses Ctrl+Tab (or Cmd+Tab on macOS)
- **THEN** the system switches to the next most recently used session across all open projects

#### Scenario: Switch to previous session
- **WHEN** the user presses Ctrl+Shift+Tab (or Cmd+Shift+Tab on macOS)
- **THEN** the system switches to the previous most recently used session
