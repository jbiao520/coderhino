# State Package

Immutable state management in `com.coderhino.state`. This package owns the application's runtime state, session lifecycle, and JSONL-based persistence. All state is held in memory — there is no database.

> See [Architecture > Key Packages](../CLAUDE.md) for the broader context.

## Class Overview

| Class | Role |
|-------|------|
| `AppState` | Immutable record holding all runtime state (model, messages, token usage, session) |
| `BootstrapState` | Thread-safe wrapper around `AppState` with atomic updates and change listeners |
| `SessionRuntime` | Immutable record representing an active session's transcript and metadata |
| `SessionRecord` | Flat JSON-serializable record for one line in a JSONL transcript file |
| `SessionSummary` | Lightweight summary of a session for listing (no transcript loaded) |
| `SessionStore` | JSONL file I/O — records messages, loads sessions, lists/deletes sessions |
| `LifecycleManager` | Startup/shutdown hook execution with JVM shutdown hook registration |

---

## AppState

**File:** `AppState.java`
**Type:** Java record (immutable). All mutations return a **new** `AppState` — the original is never modified.

### Fields

| Field | Type | Purpose |
|-------|------|---------|
| `verbose` | `boolean` | Verbose logging toggle |
| `model` | `String` | Active model identifier |
| `cwd` | `String` | Current working directory |
| `interactive` | `boolean` | Whether running in interactive (REPL) mode |
| `running` | `boolean` | Whether a query is currently executing |
| `permissionMode` | `PermissionMode` | Current permission mode (BYPASS, DEFAULT, PLAN, AUTO, etc.) |
| `totalCostUsd` | `double` | Accumulated API cost |
| `totalInputTokens` | `long` | Accumulated input tokens |
| `totalOutputTokens` | `long` | Accumulated output tokens |
| `totalCacheReadTokens` | `long` | Accumulated cache read tokens |
| `totalCacheWriteTokens` | `long` | Accumulated cache write tokens |
| `totalToolUses` | `int` | Total tool invocations |
| `sessionRuntime` | `SessionRuntime` | Active session (transcript + metadata) |
| `messages` | `List<Message>` | In-memory message list (defensive copy on construction) |

### Mutation methods

All return a new `AppState`:

| Method | Effect |
|--------|--------|
| `addMessage(Message)` | Appends a message |
| `clearMessages()` | Empties the message list |
| `stop()` | Sets `running` to `false` |
| `withModel(String)` | Changes the active model |
| `withPermissionMode(PermissionMode)` | Changes the permission mode |
| `withSessionRuntime(SessionRuntime)` | Replaces the session runtime |
| `withCwd(String)` | Changes the working directory |
| `addUsage(long, long)` | Adds input/output token counts |
| `addUsage(long, long, long, long, double)` | Full usage with cache tokens and cost |
| `incrementToolUses()` | Increments the tool use counter |

---

## BootstrapState

**File:** `BootstrapState.java`
**Thread safety:** `AtomicReference<AppState>` + `CopyOnWriteArrayList<Consumer<AppState>>` — safe for concurrent reads and writes.

### How it works

1. Wraps a single `AppState` in an `AtomicReference`.
2. `update(UnaryOperator<AppState>)` atomically swaps the state and notifies listeners if the reference changed.
3. Convenience methods (`addMessage`, `clearMessages`, `stop`) delegate to `update()`.
4. `onChange(Consumer<AppState>)` registers a listener and returns a `Runnable` to unregister it.

### Listener contract

- Listeners are called after each state change where the reference actually changed (identity check, not equality).
- Listener registration/deregistration is thread-safe via `CopyOnWriteArrayList`.
- `listeners()` returns a defensive copy.

---

## SessionRuntime

**File:** `SessionRuntime.java`
**Type:** Immutable record.

| Field | Type | Purpose |
|-------|------|---------|
| `sessionId` | `UUID` | Unique session identifier |
| `lastMessageId` | `UUID` | UUID of the last appended envelope |
| `customTitle` | `String` | User-set session title (nullable) |
| `transcript` | `List<Message.Envelope>` | Defensive copy — full message history |

### Factory and mutation

- `SessionRuntime.create()` — creates a new session with a random UUID and empty transcript.
- `append(Envelope)` — returns a new runtime with the envelope added and `lastMessageId` updated.
- `replaceTranscript(List<Envelope>)` — replaces the full transcript (used when loading from disk).
- `withCustomTitle(String)` — returns a new runtime with the title changed.

---

## SessionRecord

**File:** `SessionRecord.java`
**Type:** Flat record for JSONL serialization. One `SessionRecord` = one line in a `.jsonl` transcript file.

| Field | Type | Purpose |
|-------|------|---------|
| `entryType` | `String` | `"message"` or `"custom-title"` |
| `sessionId` | `UUID` | Owning session |
| `uuid` | `UUID` | Envelope UUID |
| `parentUuid` | `UUID` | Parent envelope UUID (nullable) |
| `timestamp` | `Instant` | When the record was created |
| `messageType` | `String` | `"user"`, `"assistant"`, `"assistant_tool_use"`, `"system"`, `"tool_result"` |
| `content` | `String` | Message text content |
| `toolName` | `String` | Tool name (only for tool messages) |
| `toolUseId` | `String` | Tool use ID (only for tool messages) |
| `sourceAssistantMessageId` | `String` | Link to parent assistant message (only for tool messages) |
| `customTitle` | `String` | Title (only for `"custom-title"` entries) |
| `cwd` | `String` | Working directory at time of recording |

### Factory methods

- `forMessage(sessionId, cwd, envelope)` — extracts tool fields from `ToolResultMessage` / `AssistantToolUseMessage`.
- `forCustomTitle(sessionId, cwd, title)` — creates a title-setting record.

---

## SessionSummary

**File:** `SessionSummary.java`
**Type:** Lightweight record for session listings — does not load the full transcript.

| Field | Type | Purpose |
|-------|------|---------|
| `sessionId` | `UUID` | Session identifier |
| `customTitle` | `String` | User-set title (nullable) |
| `firstPrompt` | `String` | First user message content (nullable) |
| `messageCount` | `int` | Total message records in the session |
| `updatedAt` | `Instant` | Timestamp of the most recent record |
| `sessionFile` | `Path` | Absolute path to the `.jsonl` file |

---

## SessionStore

**File:** `SessionStore.java`
**Storage:** JSONL files under `~/.coderhino/projects/{sanitized-cwd}/{sessionId}.jsonl`.

### Directory layout

```
~/.coderhino/projects/
  └── _users_jianguo_projects_myapp/     # sanitized cwd
      ├── 550e8400-e29b-41d4-a716-446655440000.jsonl
      └── 6ba7b810-9dad-11d1-80b4-00c04fd430c8.jsonl
```

Each `.jsonl` file is one session — one `SessionRecord` per line, appended in order.

### Key methods

| Method | Returns | Description |
|--------|---------|-------------|
| `recordMessage(AppState, Message)` | `Message.Envelope` | Creates an envelope, appends a `SessionRecord` to disk, returns the envelope |
| `saveCustomTitle(AppState, String)` | `void` | Appends a `"custom-title"` record |
| `loadSession(UUID, String)` | `SessionRuntime` | Reads all records, materializes messages, sorts by timestamp |
| `sessionExists(UUID, String)` | `boolean` | Checks if the `.jsonl` file exists |
| `transcriptSize(UUID, String)` | `int` | Counts message records without loading full objects |
| `deleteSession(UUID, String)` | `void` | Deletes the `.jsonl` file |
| `listSessions(String)` | `List<SessionSummary>` | Lists all sessions for a project, sorted by modification time |

### Message materialization

`materializeMessage(SessionRecord)` reconstructs a `Message` sealed interface instance from the flat record:

| `messageType` | Reconstructed type |
|---------------|-------------------|
| `"user"` | `Message.UserMessage` |
| `"assistant"` | `Message.AssistantMessage` |
| `"assistant_tool_use"` | `Message.AssistantToolUseMessage` |
| `"system"` | `Message.SystemMessage` |
| `"tool_result"` | `Message.ToolResultMessage` |

### Path sanitization

CWD paths are sanitized for use as directory names: `:`, `\`, `/` replaced with `_`, lowercased.

---

## LifecycleManager

**File:** `LifecycleManager.java`

Manages startup and shutdown hooks for the CLI runtime. Ensures cleanup runs even on SIGTERM/SIGINT.

### Behavior

1. **Startup:** `registerStartupHook(Runnable)` adds hooks that execute on `start()`. Must be called before `start()` — throws `IllegalStateException` otherwise.
2. **Shutdown:** `registerShutdownHook(Runnable)` adds hooks that execute on `shutdown()`. Can be registered at any time.
3. **JVM hook:** On `start()`, a JVM shutdown hook is registered via `Runtime.getRuntime().addShutdownHook()` so `shutdown()` fires on SIGTERM/SIGINT.
4. **Idempotency:** `shutdown()` uses `compareAndSet` — safe to call multiple times. The JVM shutdown hook is removed on explicit shutdown to avoid double-execution.
5. **Error handling:** Individual shutdown hook failures are logged to stderr but do not prevent remaining hooks from running.

### Thread safety

`started` and `shutDown` are `AtomicBoolean` — `start()` and `shutdown()` are safe to call from multiple threads. The hook lists (`ArrayList`) are not thread-safe for concurrent registration — register all hooks from a single thread before calling `start()`.

---

## Data Flow

```
QueryEngine / ReplShell
  │
  ├─ BootstrapState.addMessage(Message)
  │     └─ AtomicReference update → new AppState → notify listeners
  │
  ├─ SessionStore.recordMessage(AppState, Message)
  │     └─ SessionRecord → append to .jsonl file
  │
  └─ SessionRuntime.append(Envelope)
        └─ New immutable SessionRuntime with updated transcript
```

On session restore:
```
SessionStore.loadSession(UUID, cwd)
  └─ Read .jsonl → parse SessionRecords → materialize Messages → sort by timestamp
  └─ Return SessionRuntime(sessionId, lastMessageId, customTitle, transcript)
```
