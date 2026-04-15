# Server Package (`com.coderhino.server`)

Server-mode lifecycle management — starts, stops, and queries the server subsystem across headless, daemon, and API modes. This package is the bridge between the CLI entry point (`Main`) and the Spring Boot web layer (`CodeRhinoWebApplication`).

## Classes

### `ServerMode` (enum)

Operating modes for the server subsystem. Maps to the TypeScript server feature flags.

| Value | Description |
|-------|-------------|
| `HEADLESS` | No UI — headless batch execution |
| `DAEMON` | Long-running background daemon |
| `API` | HTTP API server for external integrations (launches Spring Boot) |

### `ServerService` (interface)

Contract for server lifecycle management.

| Method | Returns | Description |
|--------|---------|-------------|
| `start(ServerMode, int port)` | `String` | Start the server in the given mode on the given port. Returns a handle identifier (e.g. `"api:8080"`). |
| `stop()` | `void` | Request graceful shutdown. |
| `isRunning()` | `boolean` | Whether the server is currently running. |
| `currentMode()` | `Optional<ServerMode>` | Current operating mode, empty if not running. |
| `serviceName()` | `String` | Service name for diagnostics. |

### `LocalServerService`

Production implementation. Thread-safe via `AtomicBoolean`, `AtomicReference`, and `CountDownLatch`.

**API mode** (`ServerMode.API`): Launches `CodeRhinoWebApplication` (Spring Boot) on a daemon thread. Uses a `CountDownLatch` to block `start()` until Spring fires `ApplicationReadyEvent` (30s timeout). Handles `PortInUseException` with a user-friendly error message.

**Non-API modes** (`HEADLESS`, `DAEMON`): Runs a stub loop on a daemon thread — a simple `while(running) sleep(50ms)` pattern that keeps the JVM alive.

**Shutdown (`stop()`)**: Sets `running = false`, closes the Spring `ConfigurableApplicationContext` if present, then shuts down the executor with a 500ms graceful window before `shutdownNow()`.

### `NoOpServerService`

Null-object implementation used for default wiring when no server is needed (e.g. CLI REPL mode). All queries return empty/false/no-op values.

## Lifecycle Flow

```
Main (CLI entry point)
  ├── --serve flag → LocalServerService.start(API, port)
  │                    └── SpringApplication.run(CodeRhinoWebApplication) on daemon thread
  │                        └── CountDownLatch.await() blocks until ApplicationReadyEvent
  │
  └── no --serve  → NoOpServerService (default wiring, REPL mode)
```

## Thread Safety

All mutable state in `LocalServerService` uses atomic references (`AtomicBoolean`, `AtomicReference<ServerMode>`, etc.). The server runs on a dedicated daemon thread so the lifecycle calls (`start`/`stop`) can be made from any thread.
