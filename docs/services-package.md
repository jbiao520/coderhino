# Services Package (`com.coderhino.services`)

Central service layer providing all non-core infrastructure: dependency injection, authentication, scheduling, protocol clients, cost tracking, and more. Services use manual DI via `ServiceRegistry` — not Spring-managed in CLI mode.

## Service Registry

### ServiceRegistry
Central dependency injection container holding all service singletons. Factory method `createDefault(Path)` wires up the full application stack with feature flag-driven behavior.

- Getter methods for all services: `mcp()`, `lsp()`, `tasks()`, `costTracker()`, `compact()`, `analytics()`, `featureFlags()`, `serverService()`, `pluginService()`, `skillService()`, `coordinatorService()`, `proactiveService()`, `cronScheduler()`, `remoteTriggerService()`, `voiceService()`, `settingsSyncService()`, `teamMemoryService()`
- Multiple telescoping constructors for progressive service addition
- NoOp defaults for optional services

### ConfiguredServiceRegistry
Fluent builder for assembling `ServiceRegistry` instances with overridable service slots.

- `static Builder builder()` with `withXxx()` methods for each service
- `build()` null-coalesces unset services to NoOp implementations

## Cost Tracking

### CostTracker
Tracks API usage costs across Anthropic models (Haiku, Sonnet, Opus) with per-model pricing tiers and optional JSON persistence.

- `addUsage(model, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens)` — returns cost in USD
- `addLinesChanged(linesAdded, linesRemoved)` — tracks code change metrics
- `save()` / `restore()` / `reset()` — persistence to `~/.coderhino/cost-state.json`
- `totalCostUsd()`, `getModelUsage(model)`, `formatSummary()`
- Nested records: `ModelUsage`, `UsageSnapshot`, `TurnRecord`, `StoredCostState`

## Analytics

### AnalyticsService (interface)
Abstraction for tracking CLI usage events.

- `trackEvent(eventName, payload)`, `flush()`, `shutdown()`, `isEnabled()`

### DefaultAnalyticsService
File-based implementation buffering events to `~/.coderhino/analytics-events.jsonl`. Uses `CopyOnWriteArrayList` for thread-safe buffering.

### NoOpAnalyticsService
Null Object implementation. `isEnabled()` returns `false`.

### AnalyticsEvent (record)
Immutable event record: `eventName`, `properties` (Map), `timestamp` (Instant). Static factories `of(name)` and `of(name, properties)`.

## Feature Flags

### FeatureFlagService (interface)
Feature gate evaluation mirroring TypeScript GrowthBook pattern.

- `isEnabled(flagName)`, `isEnabled(flagName, defaultValue)`, `getString(flagName, defaultValue)`, `flagNames()`, `snapshot()`

### EnvFeatureFlagService
Backed by environment variables (`CLAUDECODE_FLAG_*`) and JSON config file (`~/.claude/feature-flags.json`). Resolution priority: env vars > config file > defaults.

### NoOpFeatureFlagService
Returns `false` for all flags, empty for collections.

### FeatureFlag
String constants for all known feature flags: `PROACTIVE`, `KAIROS`, `DAEMON`, `VOICE_MODE`, `AGENT_TRACERS`, `COORDINATOR_MODE`, `HISTORY_SNIP`. Non-instantiable utility class.

## Authentication

### AuthService
Manages API key and OAuth token storage with validation and auth code exchange.

- `storeApiKey(key)`, `loadApiKey()`, `clearApiKey()`, `isAuthenticated()`
- `isValidApiKey(key)` (static) — regex validation
- Files stored in `~/.claude/`

### UserSettings
Simple POJO for persisting user login state (`loggedIn`, `username`, `accountTier`) to JSON file.

## Configuration Management

### ConfigVersion
Enum: `V1`, `V2`, `V3`. `current()` returns latest. `fromNumber(int)` for parsing.

### ConfigMigration (interface)
Strategy for version-to-version config schema transforms: `fromVersion()`, `toVersion()`, `migrate(Map)`.

### ConfigMigrationRunner
Orchestrates sequential migrations with auto-sorting. `migrate()` applies all needed migrations. Static `withDefaults()` factory includes V1→V2 and V2→V3.

### MigrationV1ToV2
Renames `apiKey` to `anthropicApiKey`, updates model defaults.

### MigrationV2ToV3
Renames `maxTokens` to `maxOutputTokens`, adds new fields with defaults.

### ConfigValidator
Validates configuration schema with type checking and error collection. Returns `ValidationResult` record.

### McpConfigLoader
Loads MCP server definitions from `.mcp.json` files. Jackson-based with graceful degradation.

### McpConfigWriter
Writes MCP configurations to `.mcp.json`. `addServer()`, `setServerEnabled()`.

## Cron Scheduling

### CronScheduler (interface)
Job scheduling lifecycle: `schedule()`, `cancel()`, `getJob()`, `listJobs()`, `shutdown()`.

### DefaultCronScheduler
`ScheduledExecutorService`-based with jitter support. `scheduleWithJitter()` randomizes fire times. Feature-gated via `FeatureFlagService`.

### NoOpCronScheduler
Null Object for disabled scheduling.

### CronJobInfo (record)
Job metadata: `jobId`, `expression`, `description`, `registeredAt`, `nextRun`, `active`.

### CronJitterConfig (record)
Jitter parameters: `windowMs`, `jitterFraction`.

## Context Compaction

### CompactService
Compacts message history by grouping messages and replacing older groups with summaries when token limits are exceeded. Preserves the 10 most recent messages by default.

- `compact(messages)` / `compact(messages, boundary)` — returns `CompactResult`
- `shouldCompact(messages)` — checks if compaction needed
- `estimateTokens(messages)` — rough token count
- `compactWithSimulatedSummary(...)` — injects summary simulator for testing

`CompactResult` record: `originalMessages`, `compactedMessages`, `originalTokens`, `compactTokens`, `status` (`ALREADY_WITHIN_LIMITS`, `COMPACTED`, `NO_OP`).

### CompactPromptBuilder
Static utility for building compaction prompts and formatting messages. `buildSummaryPrompt()`, `formatMessage()`, `formatGroupSummary()`, `extractBrief()`.

### MessageGrouping
Groups messages into token-based chunks for compaction. `groupMessages()`, `groupMessagesForCompaction()`, `estimateTokens()`. Nested `MessageGroup` record.

## History

### HistoryManager
Command-line history persistence in JSONL format. `add()`, `list()`, `search()`, `removeLast()`, `clear()`. Append-only file storage.

## LSP (Language Server Protocol)

### LspClientManager
Manages LSP server lifecycle and protocol operations. `register()`, `start()`, `workspaceSymbols()`, `definition()`, `hover()`, `references()`, `getDiagnostics()`. Auto-start on demand.

### LspServerDefinition (record)
Server config: `language`, `command`, `arguments`, `enabled`.

### LspConnection (record)
Connection state: `language`, `connected`, `lastStartedAt`, `statusMessage`, `processId`, `commandLine`.

### LspJsonRpcSession
Low-level JSON-RPC 2.0 protocol handler over stdio. Synchronized methods for thread safety. `initializeIfNeeded()`, `workspaceSymbols()`, `documentSymbols()`, `definition()`, `hover()`, `getDiagnostics()`. Content-Length header framing.

### Supporting types
- `LspDiagnosticDescriptor` (record) — diagnostic info
- `LspLocationDescriptor` (record) — source location
- `LspSymbolDescriptor` (record) — workspace symbol

## MCP (Model Context Protocol)

### McpConnectionManager
Central lifecycle manager with reconnection logic. `register()`, `connect()`, `disconnect()`, `reconnect()`, `listTools()`, `listResources()`, `readResource()`, `callTool()`, `ping()`. Exponential backoff reconnection. LinkedHashMap for insertion order.

### McpServerDefinition (record)
Server config: `name`, `command`, `arguments`, `environment`, `enabled`.

### McpConnection (record)
Connection state: `serverName`, `connected`, `lastStartedAt`, `statusMessage`, `processId`, `commandLine`.

### McpJsonRpcSession
Low-level JSON-RPC 2.0 handler over stdio with request-response correlation via IDs. `initializeIfNeeded()`, `listTools()`, `listResources()`, `readResource()`, `callTool()`, `ping()`, `subscribeResource()`, `unsubscribeResource()`.

### Supporting types
- `McpToolDescriptor` (record) — `name`, `description`
- `McpResourceDescriptor` (record) — `uri`, `name`, `mimeType`, `description`

## Memory

### MemoryService
Persistent per-session fact extraction and storage. Extracts `[MEMORY:...]` markers from messages, falls back to last-exchange summary. `extract()`, `recall()`, `append()`, `clear()`. JSON file per session.

### TeamMemoryService (interface)
Cross-session/team memory sharing: `share()`, `recall()`, `sync()`.

### LocalTeamMemoryService
File-based implementation with fact deduplication. Persists to `~/.coderhino/team-memory/`.

### NoOpTeamMemoryService
Null Object for disabled team memory.

## OAuth

### ApiKeyManager
API key persistence with validation (`^[a-zA-Z0-9-_]+$`). `saveApiKey()`, `loadApiKey()`, `clearApiKey()`, `hasApiKey()`.

### OAuthCrypto
PKCE cryptographic utilities (all static): `generateCodeVerifier()`, `generateCodeChallenge()`, `generateState()`. SHA-256, SecureRandom, Base64-URL encoding.

### SecureTokenStorage
JSON-based token persistence with account metadata. `saveTokens()`, `loadTokens()`, `clearTokens()`, `hasTokens()`.

### OAuthException
Runtime exception for OAuth flow errors.

## Proactive Service

### ProactiveService (interface)
Autonomous scheduled work execution (KAIROS/brief-style flows). `enable()`, `disable()`, `isEnabled()`, `scheduleWork()`, `invokeBrief()`, `shutdown()`.

### DefaultProactiveService
Thread-pool-based scheduler with attribution context. `ATTRIBUTION_CONTEXT` ThreadLocal for injecting `X-Attribution: WORKLOAD_CRON` header. Orphan job cleanup.

### NoOpProactiveService
Null Object for disabled proactive mode.

## Settings Sync

### SettingsSyncService (interface)
Bidirectional settings sync: `sync()`, `getRemoteSetting()`, `setLocalSetting()`, `isSynced()`.

### LocalSettingsSyncService
File-based implementation with `ConcurrentHashMap` cache. Persists to `~/.coderhino/settings-sync.json`.

### NoOpSettingsSyncService
Null Object for disabled settings sync.

## Tasks

### TaskService
Background task execution with persistence and progress tracking. Lifecycle: PENDING → RUNNING → DONE/FAILED.

- `create()`, `submit()`, `list()`, `get()`, `getOutput()`, `getOutputAwait()`, `stop()`, `cancel()`, `update()`, `delete()`
- `reportProgress()`, `getProgressMessages()` — synchronized progress tracking
- `shutdown()` — graceful executor shutdown

### TaskRecord (record)
Immutable task state: `id` (UUID), `status`, `description`, `output`, `createdAt`, `startedAt`, `completedAt`.

### TaskStatus (enum)
`PENDING`, `RUNNING`, `DONE`, `CANCELLED`, `FAILED`. `fromString()` for case-insensitive parsing.

## Token Estimation

### TokenEstimation
Static utility for rough token counts. Text /4, JSON /2. `roughEstimate()`, `roughEstimateForJson()`, `estimateForMessages()`, `estimateForMessage()`.

## Remote Triggers

### RemoteTriggerService (interface)
Event dispatch for remote agent triggers. `registerHandler()`, `dispatch()`, `isRegistered()`, `unregisterHandler()`.

### DefaultRemoteTriggerService
In-memory event bus with `ConcurrentHashMap<eventType, handler>`. Null-safe dispatch, silent exception handling.

### NoOpRemoteTriggerService
Null Object for disabled triggers.

## Voice

### VoiceService (interface)
Voice input and transcription. `enable()`, `disable()`, `isEnabled()`, `processAudio()`, `processText()`, `shutdown()`, `currentMode()`.

### DefaultVoiceService
Feature-flagged voice service with transcription queueing and `AnthropicVoiceStreamClient` integration. `setMode()`, `pollTranscription()`, `createStreamClient()`, `checkRecordingAvailability()`.

### NoOpVoiceService
Null Object for disabled voice.

### VoiceRecorder (interface)
Platform-agnostic audio capture returning 16-bit signed little-endian 16 kHz mono PCM. `startRecording()`, `stopRecording()`, `isAvailable()`.

### VoiceRecorderFactory
OS-specific recorder selection: Sox (macOS), native (Windows), Arecord (Linux). WSL detection via `/proc/version`. Fallback chain.

### VoiceMode (enum)
`DISABLED`, `PUSH_TO_TALK`, `CONTINUOUS`.

### Supporting types
- `VoiceTranscription` (record) — transcription result
- `VoiceStreamCallbacks` — streaming callbacks
- `VoiceKeyterms` — keyword detection utilities
- `AnthropicVoiceStreamClient` — streaming transcription client
- `NativeVoiceRecorder`, `SoxVoiceRecorder`, `ArecordVoiceRecorder` — platform recorders

## Design Patterns

| Pattern | Usage |
|---------|-------|
| **Null Object** | Every service has a `NoOp*` implementation for disabled/testing scenarios |
| **Interface + Default/NoOp** | Services define interfaces with `NoOp*` defaults wired by feature flags |
| **Builder** | `ConfiguredServiceRegistry.Builder` for flexible service assembly |
| **Feature Flags** | `EnvFeatureFlagService` gates service behavior (voice, proactive, coordinator, etc.) |
| **Immutable Records** | Config, connection state, task records, events are all Java records |
| **JSON-RPC over stdio** | Both MCP and LSP use `JsonRpcSession` pattern for protocol communication |
