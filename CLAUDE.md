# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run Commands

```bash
# Full build (includes frontend React app via frontend-maven-plugin)
mvn clean package

# Build skipping tests
mvn clean package -DskipTests

# Run tests
mvn test

# Run a single test class
mvn test -Dtest=QueryEngineTest

# Run a single test method
mvn test -Dtest=QueryEngineTest#testToolExecution

# Run the CLI (REPL mode)
mvn spring-boot:run
# or after building:
java -jar target/coderhino-1.0.0-SNAPSHOT.jar

# Run as web server
java -jar target/coderhino-1.0.0-SNAPSHOT.jar --serve --port 8080

# Print bootstrap state (debug)
java -jar target/coderhino-1.0.0-SNAPSHOT.jar --print-state

# Frontend only (from src/main/client/)
npm run dev          # Vite dev server
npm run build        # Production build
npm run test         # Vitest
npm run lint         # ESLint
```

**Excluded tests:** `PluginSkillCoordinatorTest` and `ServerModeLifecycleTest` are excluded in surefire config due to pre-existing failures.

## Architecture

This is a Java 17 rewrite of Claude Code CLI. It runs in two modes: a terminal REPL and a Spring Boot web application with a React frontend.

### Entry Points

- **CLI:** `com.coderhino.cli.Main` (picocli) — starts REPL via `ReplShell` or web server via `ServerService`
- **Web:** `com.coderhino.web.CodeRhinoWebApplication` (Spring Boot) — serves REST API + static frontend

### Core Data Flow

```
User Input → ReplShell / WebController → QueryEngine → ToolLoopOrchestrator → ModelClient (Anthropic API)
                                                                ↕
                                                         ToolRegistry → ToolDefinition.execute()
```

### Key Packages (com.coderhino.*)

| Package | Purpose |
|---------|---------|
| `cli` | REPL shell, terminal rendering, main entry point |
| `commands` | 50+ slash commands (CommandDefinition interface, CommandRegistry) |
| `query` | AI interaction engine — QueryEngine, ToolLoopOrchestrator, PromptAssembler, AgentModelClient — [detailed docs](docs/query-package.md) |
| `tools` | 40+ tool implementations (ToolDefinition interface, ToolRegistry) — [detailed docs](docs/tools-package.md) |
| `state` | Immutable state management — AppState (record), BootstrapState (thread-safe wrapper with listeners) — [detailed docs](docs/state-package.md) |
| `services` | ServiceRegistry with 20+ services (MCP, LSP, auth, analytics, cron, tasks, cost tracking, etc.) — [detailed docs](docs/services-package.md) |
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
- **Service Registry:** Manual dependency injection via `ServiceRegistry.createDefault()` — not Spring-managed in CLI mode. Services use NoOp defaults and are configured by feature flags (`EnvFeatureFlagService`).
- **Tool/Command Registries:** Both use `LinkedHashMap<String, ?>` for lookup by name. New tools implement `ToolDefinition<I,O>`, new commands implement `CommandDefinition`.

### Web Layer (Spring Boot)

The web layer uses Spring MVC with SSE streaming:
- `WebSession` — per-browser isolated state with execution locks (`ReentrantLock`)
- `RunService` — async query execution with approval workflow (PENDING_APPROVAL → APPROVED → RUNNING → COMPLETED)
- `SseQueryEventSink` — streams `QueryEvent` to browser via Server-Sent Events
- `SessionEventBus` — in-memory event bus connecting query execution to SSE subscribers
- No database — all persistence is file-based (JSONL sessions, JSON config)

### Frontend (src/main/client/)

React 18 + TypeScript + Vite. Built during `mvn package` via frontend-maven-plugin, output copied to `target/classes/static/` for Spring Boot to serve. Pages: ChatPage, SessionListPage, ApprovalsPage, SettingsPage.

- **Context providers:** `MultiProjectContext` manages the multi-project workspace — [detailed docs](docs/context-package.md)
- **Custom hooks:** 8 hooks for API communication, SSE streaming, and browser interactions — [detailed docs](docs/hooks-package.md)

### Session Persistence

Sessions stored as JSONL files in `~/.coderhino/projects/` (one line per `Message.Envelope`). `SessionStore` handles recording and loading.

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
