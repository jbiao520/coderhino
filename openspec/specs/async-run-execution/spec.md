## ADDED Requirements

### Requirement: Async run execution
The system SHALL execute web run queries on a Spring-managed async thread, returning the HTTP POST response immediately with a `RUNNING` status before any model API calls are made.

#### Scenario: Submit message returns immediately
- **WHEN** a client submits a message via `POST /api/sessions/{id}/runs`
- **THEN** the response SHALL return HTTP 202 with a `runId` and status `RUNNING` before the query engine begins execution

#### Scenario: Query execution runs on background thread
- **WHEN** a run is submitted with `BYPASS` permission mode
- **THEN** the `QueryEngine.execute()` call SHALL run on a thread that is not the HTTP request thread

### Requirement: Terminal events for all orchestrator exit paths
The `ToolLoopOrchestrator` SHALL emit a terminal sink event (`onCompleted` or `onError`) for every exit path including budget exceeded and tool iteration limit reached. The shared query execution pipeline SHALL also persist one assistant-visible terminal transcript message for every returned terminal `QueryResult`, so callers that rely on persisted session state observe the same final outcome that was emitted live.

#### Scenario: Budget exceeded emits completed and persists terminal text
- **WHEN** the accumulated usage exceeds the configured budget
- **THEN** the sink SHALL receive `onCompleted` with a message indicating budget was exceeded
- **AND** the orchestrator SHALL return a `QueryResult`
- **AND** the query execution state SHALL persist one assistant message containing the terminal result text for that turn

#### Scenario: Tool iteration limit emits completed and persists terminal text
- **WHEN** the tool loop reaches the maximum iteration count (default 20)
- **THEN** the sink SHALL receive `onCompleted` with a message indicating the iteration limit was reached
- **AND** the orchestrator SHALL return a `QueryResult`

#### Scenario: Model completion failure persists terminal error text
- **WHEN** model completion throws an exception during an iteration
- **THEN** the sink SHALL receive a terminal error event or terminal completion event for that failed turn
- **AND** the orchestrator SHALL return a terminal `QueryResult` describing the error
- **AND** the query execution state SHALL persist one assistant message containing that terminal error text for the turn

### Requirement: Frontend handles out-of-order RUN_STARTED
The frontend streaming reducer SHALL ignore a `RUN_STARTED` action for a run that has already received a terminal event (`RUN_COMPLETED` or `RUN_FAILED`).

#### Scenario: RUN_STARTED arrives after RUN_COMPLETED
- **WHEN** a `RUN_COMPLETED` event is processed followed by a `RUN_STARTED` for the same `runId`
- **THEN** the `RUN_STARTED` SHALL be ignored
- **AND** the state SHALL remain `completed` with the final assistant message visible

#### Scenario: Fresh submission clears completed tracking
- **WHEN** a user submits a new message
- **THEN** any tracked completed runIds SHALL be cleared
- **AND** the new run's `RUN_STARTED` SHALL be processed normally
