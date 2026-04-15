# plugins Package

Plugin loading, scanning, validation, installation, and MCP/LSP server wiring.

## Core Abstractions

### `PluginService` (interface)

Top-level service contract for managing plugin lifecycle:

| Method | Description |
|--------|-------------|
| `load(PluginDescriptor)` | Register a plugin |
| `unload(String id)` | Remove a plugin by ID |
| `list()` | Return all loaded plugins |
| `findById(String id)` | Look up a single plugin |
| `serviceName()` | Returns `"plugin-service"` |

Two implementations exist:

- **`FileSystemPluginService`** — production implementation backed by JSON files on disk
- **`NoOpPluginService`** — safe no-op default used when plugins are disabled

## Data Models

### `PluginDescriptor` (record)

Lightweight summary of a loaded plugin:

```
PluginDescriptor(String id, String name, String version, String description)
```

Used as the primary handle in `PluginService`.

### `PluginManifest`

Full manifest parsed from a plugin's `plugin.json`. Built via a Builder pattern. Fields:

| Field | Type | Description |
|-------|------|-------------|
| `id` | `String` | Unique plugin identifier |
| `name` | `String` | Human-readable name |
| `version` | `String` | Semantic version |
| `description` | `String` | Short summary |
| `path` | `Path` | Directory containing `plugin.json` |
| `enabled` | `boolean` | Whether the plugin is active (mutable) |
| `source` | `PluginSource` | Origin of the plugin |
| `commands` | `List<String>` | Declared slash commands |
| `agents` | `List<String>` | Declared agent definitions |
| `skills` | `List<String>` | Relative paths to skill files |
| `hooks` | `Map<String, List<String>>` | Lifecycle hooks |
| `mcpServers` | `List<McpServerDefinition>` | MCP servers to register |
| `lspServers` | `List<LspServerDefinition>` | LSP servers to register |
| `sha` | `String` | Integrity hash |

### `PluginSource` (enum)

Origin of a plugin: `USER`, `PROJECT`, `LOCAL`, `BUILTIN`.

## Plugin Lifecycle

```
Directory with plugin.json
  → PluginManifestValidator.validate()     → ValidationResult (manifest + errors + warnings)
  → FileSystemPluginService.loadFromDirectory()   → stores PluginDescriptor + PluginManifest in memory
  → PluginComponentLoader.loadComponents()         → registers skills, logs command declarations
  → PluginServerWirer.wireServers()                → registers MCP/LSP servers with prefixed names
```

### `PluginManifestValidator`

Parses and validates `plugin.json` from a plugin directory. Returns a `ValidationResult` record containing the built `PluginManifest`, a list of errors, and a list of warnings.

Validation rules:
- `id` is required and must match `[a-zA-Z0-9._@-]+`
- `name` is required
- `version` is optional but warned if not semver-like
- `mcpServers` entries require `name` and `command`
- `lspServers` entries require `language` and `command`
- `commands`, `agents`, `skills` must be non-blank strings if present
- `hooks` maps event names to lists of command strings
- Unknown `source` values default to `USER` with a warning

### `PluginScanningService`

Scans a directory tree for plugin subdirectories (each containing `plugin.json`). Default scan location: `~/.claudecode-plugins/`.

### `PluginInstaller`

Orchestrates a full install from a local directory path:

1. Validates the source directory has `plugin.json`
2. Runs `PluginManifestValidator`
3. Loads via `FileSystemPluginService`
4. Loads components via `PluginComponentLoader`
5. Tracks an analytics event

Returns `InstallResult` (success/failure record with manifest and errors).

### `PluginAutoUpdater`

Background daemon thread that polls `plugin.json` files for mtime changes every 60 seconds. Logs a message when a plugin has been updated on disk. Does not hot-reload — user must restart.

## Server Wiring

### `PluginServerWirer`

Registers MCP and LSP servers declared in a plugin manifest with their respective connection managers. Names are prefixed as `plugin:<pluginId>:<originalName>` to avoid collisions. Provides `unwireServers(pluginId)` to unregister all servers for a plugin.

`CLAUDE_PLUGIN_ROOT` environment variable is injected into MCP server definitions pointing to the plugin directory.

## Component Loading

### `PluginComponentLoader`

Loads skill files declared in a plugin manifest into the `SkillService`. Skill IDs are namespaced as `<pluginId>:<skillName>`. Registration is idempotent (skips if already present). Unloading removes all skills matching the plugin's prefix.

Command and agent declarations are currently logged but not yet registered (deferred implementation).

## Persistence (`FileSystemPluginService`)

- Plugin descriptors persisted as individual JSON files in `~/.coderhino/plugins/<sanitized-id>.json`
- Each file contains `{ id, name, version, description }`
- Full manifests (including MCP/LSP definitions) are held in memory only — not re-persisted
- Thread-safe via `ConcurrentHashMap`

## Marketplace Subpackage

| Class | Description |
|-------|-------------|
| `MarketplaceDefinition` | Record: `(name, MarketplaceType, location)` — describes a plugin source registry |
| `MarketplaceRegistry` | File-backed registry of marketplace definitions, persisted to `~/.coderhino/marketplaces.json` |
| `MarketplaceType` | Enum: currently only `LOCAL_FILE` (`GITHUB`, `NPM` reserved for future) |

## plugin.json Schema

```json
{
  "id": "my-plugin",
  "name": "My Plugin",
  "version": "1.0.0",
  "description": "Description of the plugin",
  "source": "USER",
  "commands": ["path/to/command1"],
  "agents": ["path/to/agent1"],
  "skills": ["skills/my-skill.md"],
  "hooks": {
    "postQuery": ["echo done"]
  },
  "mcpServers": [
    {
      "name": "my-mcp",
      "command": "node",
      "arguments": ["server.js"],
      "environment": { "KEY": "VALUE" },
      "enabled": true
    }
  ],
  "lspServers": [
    {
      "language": "python",
      "command": "pylsp",
      "arguments": [],
      "enabled": true
    }
  ],
  "sha": "abc123"
}
```

Only `id` and `name` are required. All other fields are optional.
