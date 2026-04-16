# web-session-terminals Specification

## Purpose
TBD - created by archiving change fix-web-terminal-connection-failure. Update Purpose after archive.
## Requirements
### Requirement: Web UI shall create and list session-scoped terminals
The system SHALL allow the web UI to create a terminal for an existing project-scoped session, list terminals already associated with that session, and return enough metadata for the browser to render terminal tabs and lifecycle state.

#### Scenario: Browser creates a terminal for an active session
- **WHEN** the browser sends `POST /api/sessions/{sessionId}/terminals` for a valid project-scoped session
- **THEN** the server SHALL create one terminal session bound to the resolved session worktree
- **THEN** the response SHALL include the terminal identifier, label, cwd, worktree identifier when available, creation timestamp, and current lifecycle state

#### Scenario: Browser lists existing terminals for a session
- **WHEN** the browser sends `GET /api/sessions/{sessionId}/terminals`
- **THEN** the server SHALL return every terminal currently associated with that session in creation order
- **THEN** each returned item SHALL include enough metadata for the browser to render a terminal tab without making an additional lookup

#### Scenario: Terminal creation rejects an invalid session or worktree
- **WHEN** the browser requests terminal creation for an unknown session or a worktree outside the active project workspace
- **THEN** the server SHALL reject the request with an error payload suitable for the browser to display

### Requirement: Web terminal WebSocket attachment shall bind only to the owning session terminal
The system SHALL expose a WebSocket endpoint for terminal I/O that attaches only when the requested terminal exists and belongs to the provided web session. On successful attachment, the endpoint SHALL stream terminal output, accept browser input and resize messages, and acknowledge readiness so the browser can distinguish a successful attach from an immediate transport failure.

#### Scenario: Browser attaches to a running session terminal
- **WHEN** the browser opens the terminal WebSocket for a running terminal using the owning `sessionId`
- **THEN** the server SHALL accept the connection, register the browser as a listener for that terminal, and emit a readiness event before or together with terminal output replay

#### Scenario: Browser attachment is rejected for unknown or foreign terminal
- **WHEN** the browser opens the terminal WebSocket for a terminal that does not exist or does not belong to the provided session
- **THEN** the server SHALL reject the attachment instead of binding the browser to a different terminal

#### Scenario: Browser sends input and resize after attach
- **WHEN** the browser sends terminal input or resize messages over an attached terminal WebSocket
- **THEN** the server SHALL forward that input and resize to the bound terminal process

### Requirement: Browser terminal state shall reflect attach, exit, and error outcomes
The system SHALL keep the browser-visible terminal tab state synchronized with terminal attach and process lifecycle events so the UI can distinguish a running terminal from an exited terminal, a rejected attachment, or a runtime error. Error states SHALL preserve a diagnostic message when one is available from the backend or connection flow.

#### Scenario: Attached terminal exits after streaming output
- **WHEN** an attached terminal process exits
- **THEN** the browser-visible terminal state SHALL transition from `RUNNING` to `EXITED`
- **THEN** the terminal state SHALL preserve the reported exit code

#### Scenario: Attach attempt fails before terminal becomes ready
- **WHEN** the browser cannot complete terminal attachment for a created terminal
- **THEN** the browser-visible terminal state SHALL transition to `ERROR`
- **THEN** the UI SHALL show an attach failure message that is more specific than a generic fallback when a failure reason is available

#### Scenario: Runtime terminal error is reported after attach
- **WHEN** the terminal backend reports an error for an attached terminal
- **THEN** the browser-visible terminal state SHALL transition to `ERROR`
- **THEN** the UI SHALL retain the backend-provided diagnostic message for that terminal tab

### Requirement: Closing a terminal shall remove it from the owning session
The system SHALL allow the web UI to close a session terminal and remove it from subsequent session-scoped terminal listings.

#### Scenario: Browser closes a running session terminal
- **WHEN** the browser sends `DELETE /api/sessions/{sessionId}/terminals/{terminalId}` for a running terminal owned by that session
- **THEN** the server SHALL stop the terminal process and remove that terminal from the owning session registry

#### Scenario: Browser closes an unknown session terminal
- **WHEN** the browser requests closure for a terminal that is not associated with the provided session
- **THEN** the server SHALL respond without claiming that a terminal was removed

