# Tools Package

Tool system in `com.coderhino.tools`. Defines the `ToolDefinition` interface, a `ToolRegistry` for lookup, a `ToolContext` for execution context, and 40+ built-in tool implementations in the `builtin` subpackage. Tools are the primary way the AI model interacts with the filesystem, shell, web, and external services.

> See [Architecture > Key Packages](../CLAUDE.md) for the broader context.

## Core Interfaces

### ToolDefinition<I, O>

**File:** `ToolDefinition.java`
**Type:** Generic interface. Each tool specifies its `Input` and `Output` types as nested records.

| Method | Default | Purpose |
|--------|---------|---------|
| `name()` | — | Unique tool identifier (e.g. `"bash"`, `"read_file"`) |
| `description()` | — | Human-readable description exposed to the model |
| `inputSchema()` | — | JSON schema for input validation |
| `isEnabled()` | `true` | Feature-flag gate |
| `isReadOnly()` | `false` | Marks tools that don't modify state |
| `validate(I, ToolContext)` | `allow()` | Input validation + permission check |
| `execute(I, ToolContext)` | — | Core tool logic |

### ToolContext

**File:** `ToolContext.java`
**Type:** Java record providing execution context to every tool.

| Field | Type | Purpose |
|-------|------|---------|
| `bootstrapState` | `BootstrapState` | Current app state (CWD, model, messages, etc.) |
| `permissionMode` | `PermissionMode` | BYPASS, DEFAULT, PLAN, AUTO, DONT_ASK, ACCEPT_EDITS |
| `services` | `ServiceRegistry` | Access to all services (MCP, LSP, tasks, etc.) |
| `subAgentContext` | `SubAgentContext` | Depth guard for recursive sub-agent calls |

Also provides a `ThreadLocal`-based progress reporting mechanism: `reportProgress()` accumulates messages, `drainProgressMessages()` consumes them.

### ToolRegistry

**File:** `ToolRegistry.java`
**Type:** `LinkedHashMap<String, ToolDefinition<?, ?>>` wrapper with lookup by name.

- `createDefault()` — instantiates all 42 builtin tools in a fixed order. `SleepTool`, `SyntheticOutputTool`, and `ToolSearchTool` are added in a second pass (the latter depends on the registry itself).
- `find(name)` — returns `Optional<ToolDefinition<?, ?>>`
- `all()` — returns all registered tools

---

## How Tools Are Called

The `ToolLoopOrchestrator` drives the tool-use loop:

1. Model returns a `ToolRequest` with tool name + JSON arguments
2. Orchestrator looks up the tool via `ToolRegistry.find(name)`
3. Orchestrator uses reflection to find the tool's nested `Input` record class
4. Arguments are deserialized via Jackson `objectMapper.convertValue(jsonArgs, inputClass)`
5. `validate(input, context)` runs — returns `allow`, `deny`, or `ask`
6. If allowed: `execute(input, context)` runs and the result is sent back to the model

---

## Built-in Tools (42 total)

### File Operations

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `FileReadTool` | `read_file` | Yes | Read a UTF-8 file with numbered lines. Supports offset/limit pagination. 100KB max. |
| `FileWriteTool` | `write_file` | No | Write UTF-8 content to a file (creates parent dirs). |
| `FileEditTool` | `edit_file` | No | Replace exact text in a file. Fails if `oldText` matches zero or multiple times. |
| `NotebookEditTool` | `notebook_edit` | No | Edit Jupyter notebook (.ipynb) cells — replace, insert, or delete by cell ID. |
| `GlobTool` | `glob` | Yes | Match files by glob pattern. Excludes `.git`, `node_modules`, `target`, etc. 500 result max. |
| `GrepTool` | `grep` | Yes | Regex search across files. Three output modes: `files_with_matches`, `count`, `content`. Supports glob filtering and context lines. |

### Shell & Execution

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `BashTool` | `bash` | No | Execute a shell command via `/bin/zsh -lc`. 30s default timeout, 100KB output cap. Permission-aware. |
| `REPLTool` | `repl` | Yes | Lists the set of primitive tools available in the REPL context. |

### Agent & Session Management

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `AgentTool` | `agent` | No | Spawn a sub-agent (sync or async). Supports subagent types: explore, librarian, oracle, build. Recursion depth limited to 5. |
| `SendMessageTool` | `send_message` | No | Send a message to another agent or session by recipient name. Uses in-memory `MESSAGE_QUEUE`. |
| `BriefTool` | `SendUserMessage` | Yes | Send a message to the user with optional attachments and proactive status flag. |
| `AskUserQuestionTool` | `ask_user_question` | Yes | Present a structured question to the user with optional predefined choices. |

### Planning & Workspace

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `EnterPlanModeTool` | `enter_plan_mode` | Yes | Switch session to plan mode (read-only, no tool execution). |
| `ExitPlanModeTool` | `exit_plan_mode` | Yes | Exit plan mode, optionally targeting a specific permission mode. |
| `EnterWorktreeTool` | `enter_worktree` | No | Activate a git worktree for session isolation. |
| `ExitWorktreeTool` | `exit_worktree` | No | Deactivate worktree and return to default working directory. |

### Task & Todo Management

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `TaskCreateTool` | `TaskCreate` | No | Create a local task record. |
| `TaskGetTool` | `TaskGet` | Yes | Retrieve a task by ID. |
| `TaskListTool` | `TaskList` | Yes | List all tasks. |
| `TaskOutputTool` | `task_output` | Yes | Get output from a background task. Supports blocking with timeout (30s default, 600s max). |
| `TaskStopTool` | `TaskStop` | No | Stop a running task by ID. |
| `TaskUpdateTool` | `TaskUpdate` | No | Update task status (pending/in_progress/completed/deleted). |
| `TodoCreateTool` | `todo_create` | No | Create a todo entry with title and description. |
| `TodoWriteTool` | `todo_write` | No | Replace the entire todo list. |

### Scheduling & Triggers

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `CronCreateTool` | `cron_create` | No | Schedule a recurring cron job. Checks `FeatureFlag.PROACTIVE`. |
| `CronDeleteTool` | `cron_delete` | No | Cancel a cron job by ID. |
| `CronListTool` | `cron_list` | Yes | List all scheduled cron jobs. |
| `RemoteTriggerTool` | `remote_trigger` | No | Dispatch a webhook-style trigger event to registered handlers. |

### Web & Search

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `WebFetchTool` | `web_fetch` | Yes | Fetch a URL and return content (text or HTML-to-markdown). 15-minute LRU cache, domain blocklist, auto HTTPS upgrade. |
| `WebSearchTool` | `web_search` | Yes | Search the web and return top results. Default limit 5, max 20. |

### External Integrations

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `MCPTool` | `mcp` | Yes | Execute a tool provided by a connected MCP server. |
| `ListMcpResourcesTool` | `ListMcpResourcesTool` | Yes | List available resources from MCP servers. |
| `ReadMcpResourceTool` | `ReadMcpResourceTool` | Yes | Read a specific resource from an MCP server. |
| `LspTool` | `lsp` | Yes | Run LSP code intelligence (workspaceSymbol, documentSymbol, goToDefinition, hover). |

### Config & Utilities

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `ConfigTool` | `config` | No | Read or write a config key-value pair. In-memory `ConcurrentHashMap` store. |
| `SkillTool` | `skill` | Yes | Execute a skill or slash command. Normalizes names, supports forked skills. |
| `ToolSearchTool` | `tool_search` | Yes | List/search available tools by query. Depends on `ToolRegistry` instance. |
| `SleepTool` | `sleep` | Yes | Wait for N milliseconds (capped at 60s). |
| `SyntheticOutputTool` | `synthetic_output` | Yes | Wrap content in structured JSON output. Escapes special characters. |

### Team & Multi-Agent

| Tool | Name | ReadOnly | Description |
|------|------|----------|-------------|
| `TeamCreateTool` | `team_create` | No | Create a named team/session group with optional member agent IDs. |
| `TeamDeleteTool` | `team_delete` | No | Delete a named team/session group. |

---

## Design Patterns

### Input Materialization

Every tool declares a nested `Input` record. The `ToolLoopOrchestrator` discovers it via reflection (`toolClass.getDeclaredClasses()` filtered for "Input") and deserializes JSON arguments with Jackson:

```java
var inputClass = findInputClass(tool);
var input = objectMapper.convertValue(arguments, inputClass);
```

String inputs in `Input` records are typically stripped of leading/trailing whitespace in compact constructors.

### Permission Validation

Tools declare their permission requirements via `validate()`:

- **Read-only tools** (glob, grep, read_file) — always `allow()`
- **Destructive tools** (bash, edit_file, write_file) — use `EnhancedPermissionChecker` to resolve based on `PermissionMode`:
  - `BYPASS` — auto-allow
  - `PLAN` — auto-deny
  - `DEFAULT/AUTO/DONT_ASK/ACCEPT_EDITS` — ask user for confirmation

### Path Resolution

File tools resolve relative paths against `context.bootstrapState().get().cwd()`:

```java
private Path resolve(ToolContext context, String rawPath) {
    var path = Path.of(rawPath);
    if (path.isAbsolute()) return path.normalize();
    return Path.of(context.bootstrapState().get().cwd()).resolve(path).normalize();
}
```

### Static In-Memory State

Several tools maintain static concurrent collections (no persistence across restarts):

| Tool | State |
|------|-------|
| `ConfigTool` | `ConcurrentHashMap<String, String>` |
| `EnterWorktreeTool` / `ExitWorktreeTool` | `ConcurrentHashMap<UUID, String>` |
| `SendMessageTool` | `CopyOnWriteArrayList<SentMessage>` |
| `TeamCreateTool` / `TeamDeleteTool` | `ConcurrentHashMap<String, List<String>>` |
| `TodoCreateTool` | `AtomicLong` counter |
| `TodoWriteTool` | `CopyOnWriteArrayList<String>` |

### Adding a New Tool

1. Create a class in `builtin/` implementing `ToolDefinition<I, O>`
2. Define nested `Input` and `Output` records
3. Implement `name()`, `description()`, `inputSchema()`, `execute()`
4. Override `isReadOnly()` if the tool is read-only
5. Override `validate()` for permission logic
6. Register in `ToolRegistry.createDefault()` — add to the `tools` list

---

## Output Size Limits

| Tool | Limit |
|------|-------|
| `BashTool` | 100KB stdout/stderr each |
| `FileReadTool` | 100KB raw content |
| `GlobTool` | 500 results |
| `GrepTool` | 1000 results, 500 chars per line |
| `WebSearchTool` | 20 results max |
