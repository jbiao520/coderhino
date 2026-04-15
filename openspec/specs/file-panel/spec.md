# File Panel Specification

### Requirement: Resizable file panel
The system SHALL render a file panel on the right side of the chat view. The panel width SHALL be adjustable by dragging a handle on its left edge. The panel width SHALL be clamped between 280px and 800px. The default width SHALL be 480px. The panel width SHALL be persisted to localStorage and restored on subsequent loads.

#### Scenario: User drags the resize handle to widen the panel
- **WHEN** the file panel is open and the user presses the mouse button on the drag handle and moves the mouse rightward
- **THEN** the panel width increases, clamped to a maximum of 800px

#### Scenario: User drags the resize handle to narrow the panel
- **WHEN** the user presses the mouse button on the drag handle and moves the mouse leftward
- **THEN** the panel width decreases, clamped to a minimum of 280px

#### Scenario: Panel width is restored from localStorage
- **WHEN** the user opens the file panel after previously resizing it
- **THEN** the panel renders at the width stored in localStorage

#### Scenario: Panel width localStorage value is out of bounds
- **WHEN** the stored width is less than 280px or greater than 800px
- **THEN** the panel clamps to the nearest valid bound (280 or 800)

### Requirement: Tab bar with persistent tree tab
The file panel SHALL display a tab bar at the top. The leftmost tab SHALL be a non-closable "Tree" tab. Each opened file SHALL appear as a closable tab to the right of the tree tab. Only one tab SHALL be active at a time. The active tab SHALL be visually distinct.

#### Scenario: File panel opens with no files
- **WHEN** the file panel is opened for the first time or after all file tabs are closed
- **THEN** only the "Tree" tab is visible and active

#### Scenario: User clicks the Tree tab
- **WHEN** the user clicks the "Tree" tab
- **THEN** the tree view is displayed in the content area

#### Scenario: User clicks a file tab
- **WHEN** the user clicks on a file tab
- **THEN** that file's content viewer is displayed in the content area

#### Scenario: Tabs exceed panel width
- **WHEN** the number of open file tabs exceeds the visible width of the tab bar
- **THEN** the tab bar SHALL scroll horizontally to reveal overflow tabs

### Requirement: Open and close file tabs
The system SHALL allow users to open a file tab by clicking a file in the tree. The system SHALL allow users to close a file tab by clicking its close button. When a tab is closed, the system SHALL activate the nearest remaining tab, or the tree tab if no file tabs remain.

#### Scenario: User opens a file from the tree
- **WHEN** the user single-clicks a file node in the tree
- **THEN** a new tab is created with the file's name, the file content is fetched and displayed, and the new tab becomes active

#### Scenario: User opens an already-open file
- **WHEN** the user clicks a file in the tree that already has an open tab
- **THEN** no duplicate tab is created and the existing tab becomes active

#### Scenario: User closes the active file tab
- **WHEN** the user clicks the close button on the active file tab and other file tabs exist
- **THEN** the closed tab is removed and the nearest tab (right if available, otherwise left) becomes active

#### Scenario: User closes the last file tab
- **WHEN** the user clicks the close button on the only open file tab
- **THEN** the file tab is removed and the tree tab becomes active

#### Scenario: User closes a non-active file tab
- **WHEN** the user clicks the close button on a file tab that is not currently active
- **THEN** that tab is removed and the active tab remains unchanged

### Requirement: File tree with filter
The file tree SHALL display the project directory structure with lazy-loaded directories. The tree SHALL include a text input at the top that filters visible nodes by name. The filter SHALL match case-insensitively against file and directory names.

#### Scenario: User types in the filter input
- **WHEN** the user types "App" in the filter input
- **THEN** only files and directories whose names contain "app" (case-insensitive) are visible in the tree

#### Scenario: Filter input is cleared
- **WHEN** the user clears the filter input
- **THEN** all files and directories are visible again

#### Scenario: Filter with no matches
- **WHEN** the user types a query that matches no files or directories
- **THEN** the tree displays a "No matching files" message

### Requirement: File type icons
Each file node in the tree SHALL display an icon determined by its file extension. Directories SHALL display a folder icon.

#### Scenario: Java file icon
- **WHEN** a file has the `.java` extension
- **THEN** the tree displays the icon next to the file name

#### Scenario: TypeScript file icon
- **WHEN** a file has the `.ts` or `.tsx` extension
- **THEN** the tree displays the icon next to the file name

#### Scenario: Unknown file type icon
- **WHEN** a file has an extension not in the known mapping
- **THEN** the tree displays the icon next to the file name

### Requirement: File content viewer with breadcrumbs
The file content viewer SHALL display the file's content with syntax highlighting and line numbers. The viewer header SHALL show a breadcrumb path (the file's relative path). The viewer SHALL handle binary files, truncated files, and loading states.

#### Scenario: Viewing a text file
- **WHEN** the user opens a text file tab
- **THEN** the viewer displays the file content with line numbers and syntax highlighting, and the header shows the relative path as breadcrumbs

#### Scenario: Viewing a binary file
- **WHEN** the user opens a binary file tab
- **THEN** the viewer displays a "Binary file" message with the file size

#### Scenario: Viewing a truncated file
- **WHEN** the user opens a file larger than 1MB
- **THEN** the viewer displays the content up to 1MB and shows a truncation warning banner

#### Scenario: File content is loading
- **WHEN** a file tab is active but the content has not yet been fetched
- **THEN** the viewer displays a loading indicator

### Requirement: Active project resolution from URL
ChatPage SHALL resolve the active project from the URL's `:projectId` parameter using the `MultiProjectContext`. The file panel SHALL use the resolved project's path for all file API calls.

#### Scenario: User navigates to a project session URL
- **WHEN** the user navigates to `/projects/{projectId}/sessions/{sessionId}`
- **THEN** ChatPage resolves the ProjectDto from MultiProjectContext using the projectId and passes its path to the file panel

#### Scenario: Project cannot be resolved
- **WHEN** the projectId from the URL does not match any open project
- **THEN** the file panel toggle button is not displayed
