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
`ConversationHistory.build()` SHALL NOT call `ContextCollector` or inject `SystemMessage` entries. It SHALL only read existing messages from `BootstrapState` and append the new user message if not already present.

#### Scenario: Build returns only conversation messages
- **WHEN** `ConversationHistory.build(bootstrapState, "hello")` is called with an existing message list
- **THEN** the result SHALL contain only `UserMessage`, `AssistantMessage`, `AssistantToolUseMessage`, and `ToolResultMessage` instances — no `SystemMessage`

#### Scenario: User message appended when not already present
- **WHEN** the latest message in bootstrap state is not a UserMessage matching the input
- **THEN** a new `UserMessage` SHALL be appended to the returned list

#### Scenario: User message not duplicated when already present
- **WHEN** the latest message in bootstrap state is a UserMessage with content matching the input
- **THEN** no additional UserMessage SHALL be appended

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
In a multi-turn tool loop, the system prompt SHALL be assembled once and reused across iterations. The conversation message list SHALL NOT accumulate prompt content as synthetic messages.

#### Scenario: Three-turn tool loop
- **WHEN** the tool loop executes three iterations with tool results between each
- **THEN** each iteration SHALL use the same system prompt from the original QueryRequest, and the message list SHALL grow by exactly one assistant and one tool-result message per turn
