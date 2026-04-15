# Query Package

The `com.coderhino.query` package is the AI interaction engine — the core pipeline that transforms user input into model requests, manages the tool-use loop, and returns structured results. It is used by both CLI (`ReplShell`) and web (`RunService`) paths.

> See [Architecture > Core Data Flow](../CLAUDE.md) for the broader request lifecycle.

## Class Overview

| Class | Visibility | Purpose                                                                                          |
|-------|-----------|--------------------------------------------------------------------------------------------------|
| `QueryEngine` | public | Top-level entry point — assembles prompt, delegates to orchestrator                              |
| `ToolLoopOrchestrator` | package | Iterative model-call → tool-execute loop with budget/iteration limits                            |
| `ModelClient` | public | Interface for model API calls — returns `ModelResponse`                                          |
| `AgentModelClient` | public | Production implementation — calls Agent Messages API with SSE streaming                          |
| `LocalEchoModelClient` | public | Test/dev stub — parses `tool <name> <args>` from user input                                      |
| `ModelClientFactory` | public | Factory — returns `AgentModelClient` if API key present, else `LocalEchoModelClient`             |
| `AgentConfigResolver` | public | Resolves the default provider's API key, base URL, and model from persisted credentials/settings |
| `PromptAssembler` | package | Merges context snapshot + custom/append system prompts into final prompt                         |
| `ConversationHistory` | package | Builds message list from `BootstrapState` + current user input                                   |
| `QueryRequest` | public | Record — messages, system prompt, custom/append prompts                                          |
| `QueryResult` | public | Record — final text, stop reason, iterations used, token usage                                   |
| `ModelResponse` | public | Sealed interface — `AssistantReply` (text) or `ToolRequest` (tool call with arguments)           |
| `QueryEventSink` | public | Interface for streaming events (text chunks, tool calls, usage, completion)                      |
| `NoOpQueryEventSink` | package | No-op sink used by CLI path; web path uses SSE implementation                                    |
| `UsageAccumulator` | package | Accumulates token counts across iterations, applies deltas to `BootstrapState`                   |
| `BudgetEnforcer` | package | Checks if estimated USD cost exceeds configured budget                                           |
| `StopReasonResolver` | package | Creates `QueryResult` with correct `StopReason` enum (END_TURN, TOOL_LIMIT, ERROR)               |
| `ResponsePersistence` | package | Appends assistant messages back to `BootstrapState`                                              |
| `SubAgentContext` | public | Record — depth-guard for recursive sub-agent sessions (max depth 5)                              |
| `PromptAssemblyResult` | public | Record — default system prompt, user/system context, final assembled prompt                      |
| `ThinkingModeConfig` | package | Record — thinking mode toggle, budget tokens, extended thinking flag                             |
| `RetryHandler` | package | Exponential backoff retry (default 3 attempts, 500ms–8s) for transient failures                  |

---

## Pipeline Flow

```
QueryEngine.execute(bootstrapState, userInput, sink)
  │
  ├─ ensureLatestUserMessage()     → appends UserMessage to BootstrapState if not already present
  ├─ buildHistory()                → ConversationHistory builds List<Message> from state
  ├─ contextCollector.collect()    → ContextSnapshot (system + user context)
  ├─ promptAssembler.assemble()    → merges into final system prompt
  ├─ new QueryRequest(history, systemPrompt, ...)
  │
  └─ ToolLoopOrchestrator.run()    → iterative loop (max 20 iterations by default)
       │
       ├─ modelClient.complete(request)           → ModelResponse
       │    ├─ AssistantReply  → persist, emit onTextChunk, return END_TURN
       │    └─ ToolRequest     → execute tool, append result to history, continue loop
       │
       ├─ Budget check after each iteration
       └─ Return QueryResult when done
```

---

## Key Design Decisions

### Sealed Model Response

`ModelResponse` is a sealed interface with two variants:

- `AssistantReply(text, usage)` — model returned text (end of turn)
- `ToolRequest(toolName, arguments, toolUseId, usage)` — model requested a tool invocation

This enables exhaustive pattern matching in `ToolLoopOrchestrator.run()`.

### Tool Input Materialization

Each `ToolDefinition` has a nested `Input` record class. The orchestrator uses **reflection** to discover it and Jackson `objectMapper.convertValue()` to deserialize the `Map<String, Object>` arguments into the typed input record. This avoids manual parsing per tool.

```java
// ToolLoopOrchestrator.materializeInput()
for (Class<?> nestedClass : tool.getClass().getDeclaredClasses()) {
    if (nestedClass.isRecord() && nestedClass.getSimpleName().equals("Input")) {
        return objectMapper.convertValue(arguments, nestedClass);
    }
}
```

### Event Sink Pattern

`QueryEventSink` decouples the query pipeline from its consumers:

- **CLI path:** `NoOpQueryEventSink` — all callbacks are no-ops
- **Web path:** `SseQueryEventSink` — publishes `QueryEvent` to `SessionEventBus` for SSE streaming

This allows the same pipeline to serve both REPL and web modes without branching logic.

### Budget Enforcement

`BudgetEnforcer` estimates cost using per-token rates ($3/M input, $15/M output by default). When `maxBudgetUsd > 0`, it checks accumulated cost after each iteration and stops with `StopReason.ERROR` if exceeded. A value of `0.0` disables enforcement.

### Sub-Agent Recursion Guard

`SubAgentContext` carries depth and is passed through `ToolContext` to nested `AgentTool` invocations. `MAX_DEPTH = 5` prevents runaway recursion — `isTooDeep()` is checked before spawning a new `QueryEngine`.

### Retry Strategy

`RetryHandler` provides exponential backoff (500ms base, 8s max) with retryable error detection based on message content (timeout, 503, 529, rate limit, etc.). `AgentModelClient` has its own internal retry for retryable HTTP status codes (500, 502, 503, 504, 529) with 2 max attempts.

---

## Agent API Integration

`AgentModelClient` calls `POST /v1/messages` with:

- **Streaming (preferred):** SSE `Stream<String>` response parsed via `processStreamLines()`
- **Fallback:** Non-streaming response parsed via `parseResponseBody()`

### SSE Event Handling

The client handles these Agent SSE events:

| Event | Action |
|-------|--------|
| `message_start` | Extract initial `input_tokens` from usage |
| `content_block_start` | Detect `tool_use` (capture name, id) or `thinking` block |
| `content_block_delta` | Accumulate `text_delta`, `input_json_delta`, or `thinking_delta` |
| `message_delta` | Accumulate `output_tokens` |
| `message_stop` | Final usage reconciliation |

### Message Serialization

`toAgenticMessages()` converts the `List<Message>` to Agent API format:

- Consecutive `UserMessage` + `ToolResultMessage` are **merged** into a single `user` message with content blocks
- `AssistantToolUseMessage` is serialized as an `assistant` message with `tool_use` content block
- Tool results use `tool_result` content blocks with `tool_use_id` linkage

### Configuration Resolution

`AgentConfigResolver` resolves the default provider configuration used by `QueryEngine`:

| Config | 1st Priority | 2nd Priority | Default |
|--------|-------------|-------------|---------|
| Model | `defaultProvider.models[0]` | `settings.defaultModel` | `MiniMax-M2.5` |
| Base URL | `defaultProvider.apiBaseUrl` (normalized) | — | `https://api.anthropic.com` |
| API Key | `defaultProvider.apiKey` (required) | — | throws `IllegalStateException` |

If no default provider is configured, `resolve()` fails fast with `IllegalStateException` so the caller does not construct an unusable `AgentModelClient`.

---

## LocalEchoModelClient

A dev/test stub that parses tool invocations from user input using a convention-based protocol:

```
"tool bash <command>"           → ToolRequest("bash", {command, timeoutSeconds})
"tool read_file <path>"         → ToolRequest("read_file", {path, offset, limit})
"tool write_file <path>::<content>"  → ToolRequest("write_file", {path, content})
"tool edit_file <path>::<old>::<new>" → ToolRequest("edit_file", {path, oldText, newText})
"tool glob <pattern>"           → ToolRequest("glob", {pattern})
"tool grep <pattern>"           → ToolRequest("grep", {pattern})
"tool web_fetch <url>"          → ToolRequest("web_fetch", {url, format})
"tool web_search <query>"       → ToolRequest("web_search", {query, limit})
"tool lsp workspaceSymbol <lang>::<query>"  → ToolRequest("lsp", {operation, language, query})
```

For non-tool input, echoes a skeleton response. Activated automatically by `ModelClientFactory` when `ANTHROPIC_API_KEY` is not set.
