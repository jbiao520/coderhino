# session-context-raw-ai-history Specification

## Purpose
TBD - created by archiving change show-raw-ai-history-in-context. Update Purpose after archive.
## Requirements
### Requirement: Session context exposes raw AI request and response history
The session context API SHALL expose a raw AI history collection derived from `AgentModelClient` exchanges. Each outbound model request SHALL produce exactly one raw history entry, and each inbound model response SHALL produce exactly one raw history entry, in the order they occurred for the session.

#### Scenario: Request entry is recorded from Agent payload
- **WHEN** `AgentModelClient` sends a model request for a session
- **THEN** the session context raw AI history SHALL include one request entry containing the exact serialized payload sent to `/v1/messages`

#### Scenario: Response entry is recorded from model reply
- **WHEN** `AgentModelClient` receives a response for a session request
- **THEN** the session context raw AI history SHALL include one response entry corresponding to that request in the same chronological sequence

#### Scenario: Multiple model turns preserve alternating order
- **WHEN** a session performs multiple model calls in one run or across multiple runs
- **THEN** the raw AI history SHALL preserve the full chronological order of request and response entries for that session

### Requirement: Raw AI history reflects only model-client boundary messages
The session context raw AI history SHALL contain only the raw request and response messages exchanged through `AgentModelClient`. It SHALL NOT include derived assistant transcript messages, tool-call transcript messages, or tool-result transcript messages as separate AI history entries.

#### Scenario: Tool activity is excluded from raw history
- **WHEN** a model response triggers tool use and tool results are appended to the transcript
- **THEN** the raw AI history SHALL still contain only the request and response entries produced by the model client exchanges

#### Scenario: Transcript-only messages do not create raw entries
- **WHEN** the session transcript contains assistant or tool messages that were not captured as direct model-client request or response payloads
- **THEN** those transcript messages SHALL NOT appear as standalone raw AI history entries in the context API

### Requirement: Context panel renders raw AI history folded by default
The web context panel SHALL render raw AI history entries in a collapsed state by default and SHALL allow the user to expand an individual entry to inspect its full raw content.

#### Scenario: Raw history starts collapsed
- **WHEN** the user opens the context panel for a session with raw AI history entries
- **THEN** each entry SHALL render in a folded state without showing the full raw payload by default

#### Scenario: User expands one raw entry
- **WHEN** the user activates the expand control for a raw AI history entry
- **THEN** the panel SHALL reveal that entry's full raw content without automatically expanding other entries

#### Scenario: Empty raw history shows explicit state
- **WHEN** a session has no recorded raw AI request or response entries
- **THEN** the context panel SHALL show an explicit empty-state message instead of rendering placeholder history items
