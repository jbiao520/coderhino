## Purpose

TBD

## ADDED Requirements

### Requirement: QueryRequest carries conversation, system prompt, and override fields
The system SHALL provide a `QueryRequest` record in `com.coderhino.query` containing `messages` (conversation history), `systemPrompt` (assembled prompt), `customSystemPrompt` (nullable override), `appendSystemPrompt` (nullable suffix), and `tools` (nullable list of `ToolSchema` for API tool definitions). All fields SHALL be immutable after construction.

#### Scenario: Default construction with no overrides and no tools
- **WHEN** a `QueryRequest` is created with messages and a systemPrompt but null customSystemPrompt, null appendSystemPrompt, and null tools
- **THEN** the request SHALL carry only the messages and the assembled systemPrompt

#### Scenario: Construction with custom override
- **WHEN** a `QueryRequest` is created with a non-null customSystemPrompt
- **THEN** the customSystemPrompt value SHALL be accessible and the systemPrompt SHALL reflect the custom override logic

#### Scenario: Construction with append suffix
- **WHEN** a `QueryRequest` is created with a non-null appendSystemPrompt
- **THEN** the appendSystemPrompt value SHALL be accessible and the systemPrompt SHALL include the suffix appended after the default or custom prompt

#### Scenario: Construction with tool schemas
- **WHEN** a `QueryRequest` is created with a non-null, non-empty tools list
- **THEN** the tools list SHALL be accessible and contain the provided tool schemas in order

#### Scenario: Construction with null tools is backward compatible
- **WHEN** a `QueryRequest` is created with null tools
- **THEN** the request SHALL behave identically to the pre-change QueryRequest (no tools sent to API)

### Requirement: ModelClient accepts QueryRequest
The `ModelClient` interface SHALL define `complete(QueryRequest request)` instead of `complete(List<Message> history)`. All implementations SHALL extract both the system prompt and messages from the request.

#### Scenario: AgentModelClient reads system prompt from request
- **WHEN** `AgentModelClient.complete(request)` is called with a request where systemPrompt is non-blank
- **THEN** the resulting API payload SHALL contain `system` set to `request.systemPrompt()` and `messages` derived only from `request.messages()`

#### Scenario: No system prompt in request
- **WHEN** `AgentModelClient.complete(request)` is called with a request where systemPrompt is blank
- **THEN** the resulting API payload SHALL NOT contain the `system` field

### Requirement: Payload excludes SystemMessage scanning
`AgentModelClient.buildPayload()` SHALL NOT scan the message list for `SystemMessage` instances. The `system` field in the Agent API payload SHALL come exclusively from `QueryRequest.systemPrompt()`.

#### Scenario: Messages contain no SystemMessage entries
- **WHEN** a QueryRequest's message list contains UserMessage and AssistantMessage only
- **THEN** the payload's `messages` array SHALL contain only user and assistant role entries, and `system` SHALL come from the request's systemPrompt

#### Scenario: Legacy SystemMessage in messages is ignored for system field
- **WHEN** a QueryRequest's message list somehow contains a SystemMessage
- **THEN** the payload's `system` field SHALL still come only from QueryRequest.systemPrompt(), not from scanning messages

### Requirement: ToolLoopOrchestrator passes QueryRequest to ModelClient
`ToolLoopOrchestrator.run()` SHALL construct an updated `QueryRequest` for each iteration and pass it to `ModelClient.complete()`, preserving the original system prompt AND tools across tool-call turns.

#### Scenario: First iteration uses assembled request
- **WHEN** the tool loop starts with a QueryRequest containing a system prompt, initial messages, and tools
- **THEN** `modelClient.complete()` SHALL receive that QueryRequest with tools included

#### Scenario: Subsequent iterations preserve system prompt and tools
- **WHEN** a tool result is appended to the conversation and the loop continues
- **THEN** the next `modelClient.complete()` call SHALL use a QueryRequest with updated messages but the same system prompt and same tools as the first iteration

### Requirement: Public QueryEngine API unchanged
`QueryEngine.execute(BootstrapState, String)` and `QueryEngine.execute(BootstrapState, String, QueryEventSink)` SHALL retain their current signatures. Prompt options SHALL be threaded internally via package-private plumbing only.

#### Scenario: Existing caller invokes execute unchanged
- **WHEN** code calls `queryEngine.execute(bootstrapState, userInput)`
- **THEN** the method SHALL return a `QueryResult` as before, using the new internal prompt assembly path

#### Scenario: Package-private prompt options
- **WHEN** a QueryEngine is constructed with package-private prompt configuration
- **THEN** the assembled QueryRequest SHALL include the configured custom/append prompts

#### Scenario: Direct execute caller has not pre-added the latest user message
- **WHEN** code calls `queryEngine.execute(bootstrapState, userInput)` and the latest message in `bootstrapState` is not a matching `UserMessage`
- **THEN** the execution path SHALL append exactly one `UserMessage` with that content to `bootstrapState` before completing the run

#### Scenario: Direct execute caller already stored the latest user message
- **WHEN** code calls `queryEngine.execute(bootstrapState, userInput)` and the latest message in `bootstrapState` is already a matching `UserMessage`
- **THEN** the execution path SHALL NOT append a duplicate `UserMessage` to `bootstrapState`

### Requirement: Query execution shall persist one assistant turn per completed exchange
The shared query execution path SHALL persist the final assistant reply to `BootstrapState` exactly once for each completed exchange, and callers SHALL NOT need to append a second assistant turn to maintain conversation history.

#### Scenario: Completed run from CLI-style caller
- **WHEN** a caller pre-populates the current `UserMessage`, invokes query execution, and renders the returned assistant text
- **THEN** `BootstrapState` SHALL contain exactly one assistant message for that exchange after completion

#### Scenario: Completed run from direct execute caller
- **WHEN** a caller invokes `execute(...)` without pre-populating the current `UserMessage`
- **THEN** `BootstrapState` SHALL contain the triggering user message followed by exactly one persisted assistant message for the completed exchange

#### Scenario: Multi-turn execution keeps complete conversation history
- **WHEN** a later query is executed after one or more prior exchanges
- **THEN** the request history sent to the model SHALL reflect the same user and assistant turns already present in `BootstrapState`, without missing user turns or duplicate assistant turns
