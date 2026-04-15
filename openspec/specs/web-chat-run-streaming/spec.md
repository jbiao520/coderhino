## ADDED Requirements

### Requirement: Web chat run submission shall start a streamable run
The system SHALL accept a chat submission for an existing web session, create a run for that session, and expose that run to the web UI as the active run for subsequent streaming events.

#### Scenario: Submit message for execution
- **WHEN** the browser posts a message to the web session run endpoint for an existing session
- **THEN** the system SHALL create a run ID, mark that run as active for the session, and return the run identifier to the browser

#### Scenario: Reject concurrent run for same session
- **WHEN** the browser submits a new message while the session already has an active run
- **THEN** the system SHALL reject the request as a session-busy conflict and SHALL preserve the existing active run

### Requirement: Web chat stream shall deliver assistant text and tool activity using a stable SSE payload contract
The system SHALL publish SSE events for the active web session using a stable payload schema that the browser can consume directly. Text chunk events SHALL include the assistant text fragment, tool-call events SHALL include the tool name and serialized arguments, tool-result events SHALL include the tool name and tool result text, richer model-progress events SHALL include enough typed payload to distinguish thinking content and in-progress tool-input generation, and terminal events SHALL include the run ID and terminal status.

#### Scenario: Assistant text is streamed to browser
- **WHEN** the active run produces assistant text during execution
- **THEN** the SSE stream SHALL emit a `text-chunk` event whose payload includes the run ID and the text fragment in a consistent field

#### Scenario: Tool activity is streamed to browser
- **WHEN** the active run requests a tool and later receives the tool result
- **THEN** the SSE stream SHALL emit `tool-call` and `tool-result` events whose payloads identify the same run and include the tool name plus the emitted argument and result content

#### Scenario: Run fails during execution
- **WHEN** the active run throws an execution error
- **THEN** the SSE stream SHALL emit a terminal failure event containing the run ID and an error message suitable for the browser to display

#### Scenario: Rich model progress is streamed to browser
- **WHEN** the active run receives intermediate model-stream activity such as thinking text or partial tool-input generation before a final tool call or assistant reply
- **THEN** the SSE stream SHALL emit typed progress events whose payload identifies the run and preserves the arrival order of that intermediate content

### Requirement: Web chat UI shall render live assistant output for the active run
The system SHALL append streamed assistant text for the currently active run into the chat view while execution is in progress, and SHALL render streamed tool activity and richer model-progress activity inline within that same conversation flow in the exact chronological order events are processed. The UI SHALL NOT place active-run tool activity or model-progress activity in a separate bottom panel.

#### Scenario: Browser receives streaming text chunk
- **WHEN** the browser receives a `text-chunk` event for the active run
- **THEN** the chat view SHALL append that chunk to the in-progress assistant response displayed to the user

#### Scenario: Browser receives tool activity
- **WHEN** the browser receives `tool-call` and `tool-result` events for the active run
- **THEN** the chat view SHALL show the corresponding tool activity without losing the in-progress assistant response

#### Scenario: Browser receives intermediate model progress
- **WHEN** the browser receives a typed model-progress event for the active run
- **THEN** the chat view SHALL render that progress inline in chronological order without converting it into a finalized assistant message

### Requirement: Inline tool activity blocks shall be compact and collapsible
The system SHALL render each streamed tool call as a visually distinct inline block inside the main chat transcript. Each block SHALL display the tool name, a status icon indicating pending or completed execution, and a toggle control for expansion. Inline tool activity blocks SHALL be collapsed by default and SHALL reveal the tool input and output when expanded.

#### Scenario: Tool activity appears collapsed by default
- **WHEN** the browser receives a `tool-call` event for the active run
- **THEN** the chat view SHALL render a collapsed inline tool block that shows the tool name and status while hiding detailed input and output content until expanded

#### Scenario: User expands an inline tool block
- **WHEN** the user expands a completed inline tool activity block
- **THEN** the chat view SHALL show the full serialized tool input and output for that tool call within the transcript without navigating away from the conversation

### Requirement: Persisted web chat messages shall include hover-action metadata
The system SHALL include timestamp metadata for persisted messages and stable rollback target metadata for persisted user messages in session payloads.

#### Scenario: Completed run with assistant reply
- **WHEN** the active run completes after producing assistant text
- **THEN** the browser SHALL remove the live-output placeholder, append the assistant response to the message list, and clear the session's active run indicator

#### Scenario: Reload after completed run
- **WHEN** the browser reloads a session after a completed run
- **THEN** the session payload returned by the backend SHALL include the completed assistant message in the persisted message list together with its timestamp metadata

#### Scenario: Persisted user message carries rollback target metadata
- **WHEN** the browser loads a session payload that includes persisted user messages
- **THEN** each persisted user message SHALL include enough stable metadata for the browser to request a rewind to the point before that specific message

### Requirement: Web chat UI shall support rollback from a persisted user message
The system SHALL let the web chat UI trigger a rewind of session history from a selected persisted user message and then refresh browser state so the visible timeline and composer reflect the rewound conversation.

#### Scenario: Browser rolls back from hovered user message
- **WHEN** the browser invokes rollback for a persisted user message
- **THEN** the server SHALL rewind that session to the point immediately before the selected message and the browser SHALL refresh the session history shown in the chat view

#### Scenario: Browser restores clicked message text after rollback
- **WHEN** the browser completes a rollback for a persisted user message
- **THEN** the composer SHALL contain the content of the clicked user message instead of remaining empty

### Requirement: Web chat run completion shall finalize the assistant response in the session view
The system SHALL finalize a completed web run by persisting the assistant response to the session state and transitioning the web UI from live output to a completed assistant message visible in the chat history. Active-run replay state for that run SHALL retain the ordered in-progress transcript, including richer model-progress items, for reconnect and immediate post-completion refresh behavior until the active run is cleared. The completion event exposed to the browser SHALL include the completed run ID together with the associated session ID and project ID, if present, so the browser can evaluate project-scoped completion notifications without an additional lookup.

#### Scenario: Completed run with assistant reply
- **WHEN** the active run completes after producing assistant text
- **THEN** the browser SHALL remove the live-output placeholder, append the assistant response to the message list, and clear the session's active run indicator

#### Scenario: Reload after completed run
- **WHEN** the browser reloads a session after a completed run
- **THEN** the session payload returned by the backend SHALL include the completed assistant message in the persisted message list together with its timestamp metadata

#### Scenario: Completion event carries project-scoped notification context
- **WHEN** the backend emits a completed terminal event for run R1 associated with session S2 and project P1
- **THEN** the event payload delivered to the browser SHALL include `runId`, `sessionId`, and `projectId`

### Requirement: Web chat run terminal states shall be reflected in browser state
The system SHALL update browser-visible run state for completion, cancellation, and failure so the chat composer and status indicators accurately reflect whether the user can submit another request. Completion handling SHALL also make enough metadata available for the browser to treat off-screen AI run completions as project-scoped unseen notifications.

#### Scenario: Completed run clears active run state
- **WHEN** the browser receives the completion event for the active run
- **THEN** the UI SHALL mark the run as completed and SHALL re-enable message submission

#### Scenario: Completed run outside active project context updates notifications
- **WHEN** the browser receives a completed event for run R1 associated with project P1 while the user is viewing a different project or session
- **THEN** the browser SHALL treat that completion as eligible for project-scoped unseen notification handling

#### Scenario: Cancelled run clears active run state
- **WHEN** the browser cancels the active run or receives a cancellation event for that run
- **THEN** the UI SHALL clear the active run state and SHALL leave any partial live output out of the finalized chat history

#### Scenario: Failed run surfaces error
- **WHEN** the browser receives a failure event for the active run
- **THEN** the UI SHALL clear the active run state, preserve any useful diagnostic context, and display that the run failed instead of appearing idle without explanation

### Requirement: Successful web chat submissions SHALL be available to composer history navigation
The system SHALL add each successfully submitted web chat composer prompt to the browser-side composer history for the active page so that later keyboard history navigation can reuse that submitted prompt.

#### Scenario: Submitted prompt enters composer history
- **WHEN** the user submits a non-empty web chat prompt from the composer and the browser accepts that submission for execution
- **THEN** the submitted prompt SHALL be stored as the newest entry in composer history for that page

#### Scenario: Unsubmitted draft does not enter composer history
- **WHEN** the user types text in the composer but does not submit it
- **THEN** that text SHALL NOT be added to composer history as a submitted prompt

### Requirement: Prompt-backed web chat slash commands shall execute as streamable session runs
The system SHALL submit prompt-backed, web-compatible slash commands from ChatPage through the same session run pipeline used for normal chat prompts so live SSE output, run lifecycle updates, and persisted session refresh all behave consistently for command-driven interactions.

#### Scenario: Prompt-backed slash command starts a streamable run
- **WHEN** the user submits a recognized prompt-backed, web-compatible slash command from the web chat composer
- **THEN** the browser SHALL create a session run through the web session run endpoint instead of the standalone command execution endpoint

#### Scenario: Prompt-backed slash command streams live output in chat
- **WHEN** the submitted prompt-backed slash command produces assistant text or tool activity during execution
- **THEN** the chat view SHALL render the output from the normal SSE run stream using the same live transcript behavior as a non-command prompt

#### Scenario: Unsupported slash command is blocked before run submission
- **WHEN** the user submits an unrecognized slash command or one marked as not web-compatible
- **THEN** the browser SHALL show an inline validation error and SHALL NOT create a session run

#### Scenario: Non-prompt-backed slash command stays on direct command execution
- **WHEN** the user submits a recognized web-compatible slash command that is not prompt-backed
- **THEN** the browser SHALL continue using the standalone command execution endpoint instead of the session run endpoint

### Requirement: Web chat slash commands shall persist a visible command prompt
The system SHALL preserve a display-ready user prompt for slash command runs so the chat timeline shows both the invoked command and the user-provided parameter text in a stable, human-readable form even when the internal prompt sent to the model is expanded from a prompt-backed command definition.

#### Scenario: Raw slash command is shown for direct command prompts
- **WHEN** a slash command does not define a separate display prompt
- **THEN** the persisted user message shown in the web chat timeline SHALL match the normalized slash command form `/<command> <arguments>`

#### Scenario: Expanded display prompt is shown for prompt-backed slash commands
- **WHEN** a prompt-backed slash command provides a display-ready prompt prefix derived from the command definition and user arguments
- **THEN** the persisted user message shown in the web chat timeline SHALL use that display prompt together with the submitted parameter text

#### Scenario: Refreshed session does not duplicate visible slash command messages
- **WHEN** the browser refreshes session state after a slash command run completes
- **THEN** the chat timeline SHALL reconcile the optimistic user message with the persisted visible command prompt without showing duplicates
