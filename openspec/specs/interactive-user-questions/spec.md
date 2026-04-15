# interactive-user-questions Specification

## Purpose
TBD - created by archiving change support-ask-user-question-in-web-chat. Update Purpose after archive.
## Requirements
### Requirement: Web chat SHALL surface pending ask-user-question prompts
When an active run reaches the `ask_user_question` tool, the system SHALL expose a structured pending question to the web client that includes the tool-use identifier, the question text, and any predefined choices.

#### Scenario: Open-ended question becomes pending input
- **WHEN** the model invokes `ask_user_question` with a question and no choices during a web run
- **THEN** the active run state includes a pending question payload for that run
- **THEN** the web chat renders an input UI for answering the question
- **THEN** the run remains paused until the user submits an answer or cancels the run

#### Scenario: Choice-based question becomes pending input
- **WHEN** the model invokes `ask_user_question` with a question and one or more choices during a web run
- **THEN** the active run state includes the question text and the provided choices
- **THEN** the web chat renders the pending question as selectable choices instead of only raw tool transcript JSON

### Requirement: Choice-based questions SHALL allow a custom answer
For a pending `ask_user_question` prompt that includes predefined choices, the web client SHALL offer an additional custom-answer path that lets the user submit a free-form response.

#### Scenario: User selects a predefined choice
- **WHEN** the pending question has predefined choices and the user selects one of them
- **THEN** the selected choice is submitted as the answer for the outstanding tool-use request

#### Scenario: User enters a custom response for a choice-based question
- **WHEN** the pending question has predefined choices and the user chooses the custom-answer option
- **THEN** the web chat reveals a free-form input control
- **THEN** the typed value is submitted as the answer for the outstanding tool-use request

### Requirement: Answer submission SHALL resume the paused run
When the user answers a pending `ask_user_question`, the system SHALL associate the answer with the original tool-use ID, append the corresponding tool result to the conversation, and resume execution of the same run.

#### Scenario: Open-ended answer resumes execution
- **WHEN** the user submits a free-form answer for a pending question
- **THEN** the backend records a tool result for the outstanding `ask_user_question` tool-use ID
- **THEN** the same run transitions from waiting-for-question state back to running
- **THEN** subsequent assistant output continues in the existing active run transcript

#### Scenario: Selected choice resumes execution
- **WHEN** the user submits one of the predefined choices for a pending question
- **THEN** the backend records that choice as the tool result content for the outstanding tool-use ID
- **THEN** the run resumes without creating a new independent user message turn

### Requirement: Pending questions SHALL survive session refresh and reconnect
The system SHALL preserve pending-question state in active-run replay data so a browser refresh or SSE reconnect can reconstruct the waiting UI for the same run.

#### Scenario: Refresh during a pending question
- **WHEN** the browser reloads while a run is paused on `ask_user_question`
- **THEN** the session fetch returns the pending question payload for the active run
- **THEN** the web chat renders the same pending question and available answer controls after hydration

#### Scenario: No pending question after answer submission
- **WHEN** a pending question has already been answered and the run has resumed or completed
- **THEN** subsequent session snapshots do not expose that question as still waiting for input

