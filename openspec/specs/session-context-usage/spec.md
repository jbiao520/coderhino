# session-context-usage Specification

## Purpose
TBD - created by archiving change fix-context-usage-per-session. Update Purpose after archive.
## Requirements
### Requirement: Session context current usage shall be scoped to the requested session
The system SHALL expose `Current Usage` in the session context payload using only the usage snapshot that belongs to the requested session and its active run state. The system SHALL NOT reuse or mirror another session's current usage snapshot when the requested session has different usage data or no active usage snapshot.

#### Scenario: Different sessions expose different current usage values
- **WHEN** session A and session B have different current usage snapshots
- **THEN** `GET /api/sessions/A/context` and `GET /api/sessions/B/context` SHALL return different `summary.currentUsage` values matching their respective sessions

#### Scenario: Idle session has no current usage snapshot
- **WHEN** a session has no active usage snapshot for its current state
- **THEN** the session context response SHALL return `summary.currentUsage` as null instead of reusing values from another session

### Requirement: Context panel shall render live usage only for the selected session
The system SHALL allow the context panel to show live `Current Usage` updates for the session currently open in the chat view, and SHALL keep other sessions bound to their own context payload instead of inheriting streamed usage from a different session.

#### Scenario: Viewing an active session shows live usage
- **WHEN** the user is viewing session A and session A receives live usage updates during a run
- **THEN** the context panel SHALL render session A's latest live usage values

#### Scenario: Switching sessions clears cross-session live usage carryover
- **WHEN** session A has live usage values and the user switches to session B
- **THEN** the context panel for session B SHALL render session B's own current usage snapshot or its empty state, not session A's live usage values

