# web-chat-session-continuity Specification

## Purpose
TBD - created by archiving change fix-web-chat-session-rehydration. Update Purpose after archive.
## Requirements
### Requirement: Slash command submissions are immediately visible in web chat
The web chat system SHALL show the full slash command invocation, including all user-provided arguments after normalization, in the active session conversation immediately after the user submits the command from the chat composer.

#### Scenario: Command with arguments is echoed immediately
- **WHEN** a user submits a slash command such as `/opsx-propose fix session refresh` from the web chat composer
- **THEN** the chat timeline shows `/opsx-propose fix session refresh` as the newly submitted user-side conversation entry without waiting for a page reload

#### Scenario: Immediate echo matches the persisted command text
- **WHEN** the slash command execution completes and the session is refreshed from backend state
- **THEN** the command entry shown in the chat timeline matches the same normalized command text that was shown immediately after submission

### Requirement: Refreshed web sessions restore the persisted conversation faithfully
The web chat system SHALL reconstruct the conversation from persisted session data after a full page refresh so that visible user and assistant turns remain equivalent to the pre-refresh session transcript. This restored conversation SHALL include persisted assistant activity timelines, inline tool activity history, and related file summary metadata for completed turns whenever those artifacts were visible before refresh.

#### Scenario: Refresh restores command and assistant turns
- **WHEN** a session contains a slash command entry and its assistant response and the user refreshes the chat page
- **THEN** the restored chat timeline includes both the command entry and the assistant response in their original order

#### Scenario: Refresh restores sessions without active runs
- **WHEN** the user opens an existing session that has no active run after a browser refresh
- **THEN** the chat page loads the session from `/api/sessions/{id}` and renders the persisted messages without depending on prior in-memory streaming state

#### Scenario: Refresh restores persisted assistant activity timeline
- **WHEN** a persisted assistant message includes completed-turn activity timeline items such as thinking or tool execution history and the user refreshes the chat page
- **THEN** the restored chat timeline SHALL render those activity items with the associated assistant turn in the same relative order as before refresh

#### Scenario: Refresh restores persisted file summary metadata
- **WHEN** a persisted assistant message includes file summary metadata and the user refreshes the chat page
- **THEN** the restored chat timeline SHALL render that file summary with the same assistant turn after reload

### Requirement: Persisted assistant content renders consistently after refresh
The web chat system SHALL render assistant messages loaded from persisted session data with the same structured-message behavior used for equivalent live assistant content. Persisted assistant messages that also carry completed-turn activity timeline or file summary metadata SHALL preserve that metadata when rendered after refresh.

#### Scenario: Structured assistant message survives refresh
- **WHEN** a persisted assistant message contains structured markdown content that is eligible for the structured renderer
- **THEN** the refreshed chat page renders the structured summary and detail sections instead of degrading to an inconsistent plain-text-only presentation

#### Scenario: Plain assistant message survives refresh
- **WHEN** a persisted assistant message contains plain text content
- **THEN** the refreshed chat page renders the same plain text content without loss or duplication

#### Scenario: Structured or plain assistant message retains completed-turn metadata
- **WHEN** a persisted assistant message includes activity timeline or file summary metadata and the browser refreshes the page
- **THEN** the refreshed chat page SHALL render the assistant content together with that metadata instead of dropping the auxiliary transcript history

