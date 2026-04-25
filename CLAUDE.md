# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Full build (includes frontend React app via the frontend module)
mvn clean package

# Build skipping tests
mvn clean package -DskipTests

# Run backend / CLI integration tests
mvn -pl coderhino-backend -am test

# Run embeddable runtime tests
mvn -pl coderhino-agent-runtime -am test

# Run optional Spring integration tests
mvn -pl coderhino-agent-spring -am test

# Run web tests
mvn -pl coderhino-web -am test

# Run a single runtime test class
mvn -pl coderhino-agent-runtime -am test -Dtest=QueryEngineMessageLifecycleTest

# Run a single runtime test method
mvn -pl coderhino-agent-runtime -am test -Dtest=QueryEngineMessageLifecycleTest#executeInsertsUserMessageWhenNotAlreadyPresent

# Verify publishable library modules (attaches sources and javadocs)
mvn -pl coderhino-runtime-api,coderhino-services,coderhino-tools,coderhino-agent-runtime -am verify

# Run the CLI / web app after building:
java -jar coderhino-backend/target/coderhino-backend-1.0.0-SNAPSHOT.jar

# Run as web server
java -jar coderhino-backend/target/coderhino-backend-1.0.0-SNAPSHOT.jar --serve --port 8080

# Print bootstrap state (debug)
java -jar coderhino-backend/target/coderhino-backend-1.0.0-SNAPSHOT.jar --print-state

# Frontend only (from frontend/)
npm run dev          # Vite dev server
npm run build        # Production build
npm run test         # Vitest
npm run lint         # ESLint
```

**Excluded tests:** `PluginSkillCoordinatorTest` and `ServerModeLifecycleTest` are excluded in surefire config due to pre-existing failures.

## Architecture

This is a Java 17 rewrite of Claude Code CLI. It supports three integration surfaces: a terminal REPL, a Spring Boot web application with a React frontend, and an embeddable Java agent runtime.

### Entry Points

- **CLI:** `com.coderhino.cli.Main` (picocli) — starts REPL via `ReplShell` or web server via `ServerService`
- **Web:** `com.coderhino.web.CodeRhinoWebApplication` (Spring Boot) — serves REST API + static frontend
- **Embeddable runtime:** `com.coderhino.agent.CoderhinoAgent` — plain Java facade in `coderhino-agent-runtime`
- **Optional Spring integration:** `com.coderhino.agent.spring.CoderhinoAgentAutoConfiguration` — lightweight auto-configuration for `coderhino.agent.*`

Production code is split across Maven modules:

| Module | Purpose |
|---------|---------|
| `frontend` | React frontend bundle |
| `coderhino-runtime-api` | Shared runtime contracts, state/config records, permissions, and tool runtime interfaces |
| `coderhino-services` | Service implementations and service-registry default profiles |
| `coderhino-tools` | Built-in tool implementations and `ToolRegistry` |
| `coderhino-agent-runtime` | Embeddable query runtime, `QueryEngine`, `ToolLoopOrchestrator`, `ModelClient`, and `CoderhinoAgent` |
| `coderhino-agent-spring` | Optional Spring Boot auto-configuration for embedding |
| `coderhino-commands` | Slash command definitions and registry |
| `coderhino-backend` | CLI application packaging and integration |
| `coderhino-web` | Spring Boot REST API, SSE, and static asset hosting |

### Core Data Flow

```
User Input / Host App → ReplShell / WebController / CoderhinoAgent → QueryEngine → ToolLoopOrchestrator → ModelClient (Anthropic/OpenAI/custom)
                                                                                         ↕
                                                                                  ToolRegistry → ToolDefinition.execute()
```

### Key Packages (com.coderhino.*)

| Package | Purpose |
|---------|---------|
| `cli` | REPL shell, terminal rendering, main entry point |
| `agent` | Public embeddable runtime facade — `CoderhinoAgent`, `AgentConfig`, `AgentRequest`, `AgentResult` |
| `commands` | 50+ slash commands (CommandDefinition interface, CommandRegistry) |
| `config` | Shared settings and credentials persistence extracted from `com.coderhino.web.*` into `com.coderhino.config.*` |
| `query` | AI interaction engine in `coderhino-agent-runtime` — QueryEngine, ToolLoopOrchestrator, PromptAssembler, AgentModelClient — [detailed docs](docs/query-package.md) |
| `tools` | 40+ tool implementations (ToolDefinition interface, ToolRegistry) — [detailed docs](docs/tools-package.md) |
| `state` | Immutable state management — AppState (record), BootstrapState (thread-safe wrapper with listeners) — [detailed docs](docs/state-package.md) |
| `services` | ServiceRegistry with 20+ services (MCP, LSP, auth, analytics, cron, tasks, cost tracking, etc.) plus embedded/app default profiles — [detailed docs](docs/services-package.md) |
| `permissions` | Permission checking — modes: BYPASS, DEFAULT, PLAN, AUTO, DONT_ASK, ACCEPT_EDITS — [detailed docs](docs/permissions-package.md) |
| `server` | Server-mode lifecycle (HEADLESS/DAEMON/API) — [detailed docs](docs/server-package.md) |
| `web` | Spring Boot REST controllers, SSE streaming, WebSession management, approval workflow |
| `plugins` | Plugin loading/scanning with MCP/LSP server wiring — [detailed docs](docs/plugins-package.md) |
| `skills` | Skill discovery, persistence, and execution — [detailed docs](docs/skills-package.md) |
| `hooks` | Lifecycle hook execution |
| `coordinator` | Multi-agent coordination (SINGLE / MULTI_AGENT / TEAM modes) — [detailed docs](docs/coordinator-package.md) |
| `context` | Git status, environment info, project context collection |
| `types` | Shared types — Message (sealed interface with 5 variants), PermissionMode, ToolInputSchema — [detailed docs](docs/types-package.md) |

### Important Design Patterns

- **Immutable state:** `AppState` is a Java record. `BootstrapState` wraps it in an `AtomicReference<AppState>` with `CopyOnWriteArrayList` listeners for thread-safe state changes.
- **Message types:** `Message` is a sealed interface permitting `UserMessage`, `AssistantMessage`, `AssistantToolUseMessage`, `SystemMessage`, `ToolResultMessage`. Messages are wrapped in `Message.Envelope` (UUID + parent UUID + timestamp) for persistence.
- **Tool input materialization:** Each tool has a nested `Input` record class. The `ToolLoopOrchestrator` uses reflection to find it and Jackson `objectMapper.convertValue()` to deserialize arguments.
- **Embeddable facade:** `CoderhinoAgent` wraps `QueryEngine` wiring and supports caller-provided `ModelClient`, `ToolRegistry`, `ServiceRegistry`, `PermissionChecker`, `BootstrapState`, prompts, budgets, and `QueryEventSink`.
- **Service Registry:** Manual dependency injection via `ServiceRegistry`. Use `createEmbeddedDefault(...)` for safe embedders and `createAppDefault(Path cwd, ServerService server)` for CLI/web behavior. `createDefault()` currently aliases the embedded-safe profile.
- **Tool/Command Registries:** Both use `LinkedHashMap<String, ?>` for lookup by name. `ToolRegistry.createDefault()` exposes the full built-in tool set, `createReadOnlyDefault()` exposes the embedded-safe read-only set, and `filtered(...)` narrows a registry for host-selected tools.

### Web Layer (Spring Boot)

The web layer uses Spring MVC with SSE streaming:
- `WebSession` — per-browser isolated state with execution locks (`ReentrantLock`)
- `RunService` — async query execution with approval workflow (PENDING_APPROVAL → APPROVED → RUNNING → COMPLETED)
- `SseQueryEventSink` — streams `QueryEvent` to browser via Server-Sent Events
- `SessionEventBus` — in-memory event bus connecting query execution to SSE subscribers
- No database — all persistence is file-based (JSONL sessions, JSON config)

### Frontend (frontend/)

React 18 + TypeScript + Vite. Built during the root `mvn package` via the dedicated `frontend` Maven module, with output handed off through `frontend/target/frontend-dist/` and copied into `coderhino-backend/target/classes/static/` for Spring Boot to serve. Pages: ChatPage, SessionListPage, ApprovalsPage, SettingsPage.

- **Context providers:** `MultiProjectContext` manages the multi-project workspace — [detailed docs](docs/context-package.md)
- **Custom hooks:** 8 hooks for API communication, SSE streaming, and browser interactions — [detailed docs](docs/hooks-package.md)

### Session Persistence

Sessions stored as JSONL files in `~/.coderhino/projects/` (one line per `Message.Envelope`). `SessionStore` handles recording and loading.

## Embeddable Runtime

- `coderhino-agent-runtime` is the plain Java embeddable runtime module and must remain Spring-free.
- `CoderhinoAgent` is the supported public embedding entry point for external Java callers.
- Embedded-safe defaults use `ToolRegistry.createReadOnlyDefault()` and `ServiceRegistry.createEmbeddedDefault(...)` so embedders do not start servers, daemons, plugin updaters, voice, or broad local integrations by default.
- CLI and web paths should use `ServiceRegistry.createAppDefault(...)` when they need the existing local MCP/LSP/plugin/analytics behavior.
- `coderhino-agent-spring` is optional. It binds `coderhino.agent.*` properties and backs off when host apps provide custom `ModelClient`, `ToolRegistry`, `ServiceRegistry`, `PermissionChecker`, or `QueryEventSink` beans.
- First external publication scope is `coderhino-runtime-api`, `coderhino-services`, `coderhino-tools`, and `coderhino-agent-runtime`. Commands, Spring integration, backend, and web remain outside the first public library set.
- See `docs/embeddable-agent-runtime.md` for the current embedding and publication notes.

## Custom Commands

Custom slash commands are defined in `~/.claude/commands/`. See [CUSTOM_COMMANDS.md](CUSTOM_COMMANDS.md) for full documentation.

| Command | Description |
|---------|-------------|
| `/cpjava` | Compare TypeScript/JavaScript vs Java implementations side by side |

## Tech Stack

- **Java 17**, Maven, Spring Boot 3.2.5 (Tomcat, Spring Security, Actuator)
- **picocli** for CLI argument parsing
- **Jackson** for JSON serialization
- **JUnit 5**, Mockito, WireMock, Spring Boot Test for testing
- **React 18**, TypeScript, Vite, Vitest for frontend
