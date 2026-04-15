# Coordinator Package (`com.coderhino.coordinator`)

The coordinator package provides multi-agent orchestration, allowing a single Code Rhino session to manage multiple worker agents that operate in parallel.

## Overview

The coordinator subsystem controls how Code Rhino dispatches work. It supports three modes:

- **SINGLE** — Default mode. One agent handles all tasks sequentially.
- **MULTI_AGENT** — A coordinator agent spawns and manages multiple worker agents for parallel execution.
- **TEAM** — Extended multi-agent mode with additional team coordination semantics.

The feature is gated behind the `COORDINATOR_MODE` feature flag (`FeatureFlagService`). When the flag is off or the `NoOpCoordinatorService` is used, the system always operates in `SINGLE` mode.

## Class Diagram

```
CoordinatorMode (enum)
    SINGLE | MULTI_AGENT | TEAM

CoordinatorService (interface)
    + currentMode(): CoordinatorMode
    + setMode(CoordinatorMode): void
    + isMultiAgent(): boolean
    + serviceName(): String
    + matchSessionMode(CoordinatorMode): Optional<String>
    + getWorkerToolsContext(): List<String>
    + getCoordinatorSystemPrompt(String): String
    + isCoordinatorModeAvailable(): boolean
          |
          +-- NoOpCoordinatorService      (stub, always SINGLE)
          +-- DefaultCoordinatorService    (thread-safe, feature-flag gated)
```

## Classes

### `CoordinatorMode` (enum)

Three orchestration modes:

| Mode | Description |
|------|-------------|
| `SINGLE` | Default — one agent, no workers |
| `MULTI_AGENT` | Coordinator spawns parallel workers via `Agent` tool |
| `TEAM` | Extended multi-agent with team coordination |

### `CoordinatorService` (interface)

Defines the coordinator contract. Default methods provide no-op returns so implementations only need to override what they support.

Key methods:
- **`currentMode()`** / **`setMode()`** — Get or change the active orchestration mode.
- **`isMultiAgent()`** — Returns `true` when mode is `MULTI_AGENT` or `TEAM`.
- **`matchSessionMode(requestedMode)`** — Compares requested mode to current mode. If different, switches mode and returns an `Optional<String>` warning message describing the transition (e.g. "Session mode changed to MULTI_AGENT. Coordinator mode active."). Returns `Optional.empty()` if modes already match.
- **`getWorkerToolsContext()`** — Returns the list of tool names available to worker agents.
- **`getCoordinatorSystemPrompt(workerContext)`** — Returns the full system prompt injected into the coordinator agent, including the worker context string.
- **`isCoordinatorModeAvailable()`** — Checks whether the feature flag allows coordinator mode.

### `NoOpCoordinatorService`

Null-object implementation. Always reports `SINGLE` mode, ignores `setMode()` calls, and returns `false` for `isMultiAgent()`. Used when multi-agent coordination is disabled.

### `DefaultCoordinatorService`

Thread-safe implementation using `AtomicReference<CoordinatorMode>` for mode storage.

Key behaviors:
- **Thread-safe mode switching** — Uses `AtomicReference` so concurrent reads and writes are safe.
- **Feature flag gating** — `isCoordinatorModeAvailable()` delegates to `FeatureFlagService.isEnabled(FeatureFlag.COORDINATOR_MODE)`.
- **Worker tools list** — `getWorkerToolsContext()` returns the standard set of tools available to workers: `Agent`, `Bash`, `Edit`, `Glob`, `Grep`, `Mcp`, `MultiEdit`, `NotebookEdit`, `Read`, `TodoWrite`, `WebFetch`, `WebSearch`, `Write`.
- **Coordinator system prompt** — `getCoordinatorSystemPrompt(workerContext)` returns a comprehensive prompt that instructs the coordinator how to:
  - Use `Agent` (spawn), `SendMessage` (continue), and `TaskStop` (stop) tools
  - Parse `<task-notification>` XML results from workers
  - Manage parallelism (read-only tasks in parallel, write tasks serialized)
  - Synthesize research findings before delegating implementation
  - Handle worker failures by continuing the same worker via `SendMessage`

## Integration Points

The coordinator service is registered in `ServiceRegistry.createDefault()` alongside other services. It is consumed by:

- **`QueryEngine`** — Checks `isMultiAgent()` to decide whether to inject the coordinator system prompt.
- **`ToolLoopOrchestrator`** — May reference coordinator state when routing tool calls to workers.
- **Web layer** — Mode transitions can be triggered via session configuration.

## Design Notes

- **Null safety:** Both constructors accept nullable parameters and fall back to safe defaults (`SINGLE` mode, `NoOpFeatureFlagService`).
- **NoOp pattern:** Follows the project convention of providing no-op service implementations for disabled features.
- **Immutable tools list:** `getWorkerToolsContext()` returns `List.of(...)` (immutable).
