# query-stream-events Specification

## Purpose
TBD - created by archiving change surface-model-stream-events-in-web-chat. Update Purpose after archive.

## ADDED Requirements

### Requirement: Query pipeline shall expose ordered model stream events before terminal completion
The query pipeline SHALL allow a streaming model client to emit ordered intermediate model events while a turn is still in progress, instead of waiting until the model response collapses to one terminal assistant reply or tool request. These events SHALL preserve source order for a single model call and SHALL be forwarded to downstream query event sinks before terminal completion is reported.

#### Scenario: Streaming Agent text delta is forwarded before completion
- **WHEN** `AgentModelClient` receives one or more streaming text deltas before `message_stop`
- **THEN** the query pipeline SHALL forward corresponding model stream events to the active query sink in the same order before the final completion event for that model call

#### Scenario: Streaming thinking delta is forwarded before tool or reply completion
- **WHEN** `AgentModelClient` receives `thinking_delta` content during a streaming response
- **THEN** the query pipeline SHALL forward that thinking content as an intermediate model stream event instead of discarding it

### Requirement: Streaming tool-input progress shall remain distinct from finalized tool calls
The query pipeline SHALL distinguish between partial tool-input progress emitted during model streaming and the finalized tool-call event that triggers tool execution. Partial tool-input progress SHALL NOT execute tools by itself and SHALL only represent in-progress model output until the finalized tool-use block is complete.

#### Scenario: Partial input JSON does not trigger tool execution
- **WHEN** the model emits one or more `input_json_delta` fragments for a tool-use content block
- **THEN** the system SHALL expose those fragments as in-progress model stream events and SHALL NOT execute the tool until the finalized tool request is available

#### Scenario: Finalized tool request still produces one executable tool-call event
- **WHEN** the model finishes a tool-use block and the query loop resolves a `ToolRequest`
- **THEN** the system SHALL emit exactly one executable tool-call event for that tool use in addition to any earlier progress events

### Requirement: Rich model stream events shall be optional for model clients
The shared query execution contract SHALL support model clients that emit no intermediate model stream events. Clients without streaming support SHALL continue to function using only terminal assistant reply or tool request results.

#### Scenario: Non-streaming model client remains compatible
- **WHEN** a model client returns only a terminal `AssistantReply` or `ToolRequest` and emits no intermediate events
- **THEN** the query pipeline SHALL complete the turn successfully without requiring synthetic model stream events

#### Scenario: Streaming parse failure falls back to terminal behavior
- **WHEN** the streaming Agent parse path fails and the client falls back to the existing non-streaming request path
- **THEN** the query pipeline SHALL still produce the same terminal assistant reply or tool request behavior even if intermediate model stream events are incomplete or absent

### Requirement: Query stream event sink is part of embeddable runtime API
The streaming query event sink contract SHALL be available from the embeddable agent runtime module so external applications can observe text deltas, thinking deltas, tool progress, usage, errors, and completion events without depending on backend or web modules.

#### Scenario: External app observes runtime events
- **WHEN** an external application provides a query event sink to the embeddable runtime facade
- **THEN** the runtime SHALL forward ordered query events to that sink using the same event semantics as the existing query pipeline

#### Scenario: App without streaming remains compatible
- **WHEN** an external application invokes the runtime without providing a custom event sink
- **THEN** execution SHALL still complete and return the final result without requiring event callbacks

### Requirement: Interactive user questions remain host-mediated
The embeddable runtime SHALL allow host applications to mediate interactive user-question events through the runtime event sink or equivalent callback.

#### Scenario: Host answers interactive question
- **WHEN** the model requests an interactive user question tool during embedded execution and the host callback returns an answer
- **THEN** the runtime SHALL send that answer back as the tool result and continue the agent loop

#### Scenario: Host does not answer interactive question
- **WHEN** the model requests an interactive user question tool during embedded execution and no host answer is available
- **THEN** the runtime SHALL handle the absence according to existing query event sink semantics without requiring backend or web approval infrastructure
