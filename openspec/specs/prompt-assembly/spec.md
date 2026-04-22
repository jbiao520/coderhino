## Purpose

TBD

## ADDED Requirements

### Requirement: PromptAssemblyResult preserves prompt pieces
The system SHALL provide a `PromptAssemblyResult` record in `com.coderhino.query` with fields `defaultSystemPrompt`, `userContext`, `systemContext`, and `systemPrompt`. The `systemPrompt` field SHALL contain the final assembled prompt after applying override rules.

#### Scenario: Default assembly produces systemPrompt from collected context
- **WHEN** `PromptAssembler` assembles a prompt with no custom or append overrides
- **THEN** `systemPrompt` SHALL equal the concatenation of systemContext and userContext sections, and `defaultSystemPrompt` SHALL be non-blank

#### Scenario: Custom override replaces default prompt
- **WHEN** `customSystemPrompt` is provided and non-blank
- **THEN** `systemPrompt` SHALL equal `customSystemPrompt`, and both `defaultSystemPrompt` and `systemContext` SHALL be skipped in the final prompt

#### Scenario: Append suffix added last
- **WHEN** `appendSystemPrompt` is provided and non-blank
- **THEN** `systemPrompt` SHALL end with `appendSystemPrompt` appended after the default or custom prompt content

#### Scenario: Both custom and append provided
- **WHEN** both `customSystemPrompt` and `appendSystemPrompt` are provided
- **THEN** `systemPrompt` SHALL equal `customSystemPrompt` followed by `appendSystemPrompt`, with no default prompt or systemContext included

### Requirement: PromptAssembler owns prompt construction
A `PromptAssembler` class in `com.coderhino.query` SHALL accept a `ContextSnapshot`, optional `customSystemPrompt`, and optional `appendSystemPrompt`, and return a `PromptAssemblyResult`. It SHALL apply the following precedence rules:
1. If `customSystemPrompt` is present, skip the default prompt
2. If `customSystemPrompt` is present, skip the default systemContext
3. Always append `appendSystemPrompt` last if present

#### Scenario: Assembler with no overrides
- **WHEN** `PromptAssembler.assemble(snapshot, null, null)` is called with a snapshot containing systemContext="env" and userContext="claude-md"
- **THEN** the result's `systemPrompt` SHALL contain both "env" and "claude-md" sections

#### Scenario: Assembler skips default when custom is present
- **WHEN** `PromptAssembler.assemble(snapshot, "custom-prompt", null)` is called
- **THEN** the result's `systemPrompt` SHALL be exactly "custom-prompt" with no systemContext or userContext content

#### Scenario: Assembler appends suffix
- **WHEN** `PromptAssembler.assemble(snapshot, null, "append-me")` is called
- **THEN** the result's `systemPrompt` SHALL end with "append-me" following the default content

### Requirement: ConversationHistory no longer injects context
`ConversationHistory.build()` SHALL NOT call `ContextCollector` or inject `SystemMessage` entries. It SHALL build conversation history from persisted transcript messages plus explicit current-turn input supplied by the query execution path, and it SHALL NOT infer that the latest persisted `UserMessage` is always identical to the model-facing input for the active turn.

#### Scenario: Build returns only conversation messages
- **WHEN** conversation history is built for a turn with an existing message list
- **THEN** the result SHALL contain only `UserMessage`, `AssistantMessage`, `AssistantToolUseMessage`, and `ToolResultMessage` instances and SHALL contain no `SystemMessage`

#### Scenario: Current-turn user message appended when not already present
- **WHEN** the latest persisted message is not the visible user message for the current turn
- **THEN** the query execution path SHALL persist the visible user turn before history is built
- **THEN** the resulting model-facing history SHALL still include exactly one current-turn user message

#### Scenario: Visible persisted current turn is replaced by raw model input for request history
- **WHEN** the latest persisted message is a visible `UserMessage` for the current turn and the model-facing raw input differs
- **THEN** the resulting model-facing history SHALL use the raw input for that current turn instead of duplicating both the visible and raw forms

#### Scenario: Matching visible and raw input does not duplicate the current turn
- **WHEN** the visible persisted input and raw model input are identical for the current turn
- **THEN** the resulting model-facing history SHALL contain no additional duplicate `UserMessage`

### Requirement: ContextCollector output consumed by assembler
`PromptAssembler` SHALL consume `ContextSnapshot` from `ContextCollector.collect()`. The `ContextCollector` class and `ContextSnapshot` record SHALL remain unchanged.

#### Scenario: Assembler reads systemContext from snapshot
- **WHEN** the ContextSnapshot contains a non-blank systemContext
- **THEN** the PromptAssemblyResult's systemContext field SHALL equal the snapshot's systemContext

#### Scenario: Assembler reads userContext from snapshot
- **WHEN** the ContextSnapshot contains a non-blank userContext
- **THEN** the PromptAssemblyResult's userContext field SHALL equal the snapshot's userContext

### Requirement: Custom prompt suppresses default systemContext
When `customSystemPrompt` is provided, the assembled prompt SHALL NOT include the `systemContext` content collected from environment/git info.

#### Scenario: Custom prompt present with non-blank systemContext
- **WHEN** customSystemPrompt is "my-prompt" and ContextSnapshot.systemContext is "environment info"
- **THEN** the final systemPrompt SHALL be "my-prompt" without "environment info"

### Requirement: Multi-turn execution does not duplicate prompt content
In a multi-turn tool loop, the system prompt SHALL be assembled once and reused across iterations. The conversation message list SHALL NOT accumulate prompt content as synthetic messages, and the current-turn raw model input SHALL NOT cause a second visible user turn to appear in request history.

#### Scenario: Three-turn tool loop with visible/raw divergence on the latest turn
- **WHEN** a conversation already contains prior visible user and assistant turns and the latest turn uses distinct visible and raw input before the tool loop begins
- **THEN** each model iteration SHALL use the same assembled system prompt
- **THEN** the initial message list for that run SHALL contain exactly one representation of the latest user turn in model-facing form
