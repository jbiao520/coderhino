# skills Package

Provides skill discovery, persistence, and execution. Skills are reusable, named workflows with ordered steps — persisted as JSON files on disk.

## Interface

**`SkillService`** — the service contract:

| Method | Description |
|--------|-------------|
| `executeSkill(id, input)` | Run a skill by ID. Returns a human-readable summary of steps executed. |
| `list()` | Return all available (non-removed) `SkillDescriptor` instances. |
| `findById(id)` | Look up a single skill by ID. Returns `Optional.empty()` if missing or removed. |
| `remove(name)` | Soft-delete: hides the skill from `list()` and `findById()` for the rest of this session. |
| `serviceName()` | Returns `"skill-service"`. |

## Implementations

### `FileSystemSkillService`

Production implementation. Skills are stored as individual JSON files in `~/.coderhino/skills/` (one file per skill, named `<sanitized-id>.json`).

- **Persistence:** Each file contains `{ id, name, description, filePath, steps }`. Uses Jackson `ObjectMapper` for serialization.
- **Soft delete:** `remove()` adds the ID to an in-memory `ConcurrentHashMap`-backed `Set`. Removed skills are filtered out of `list()` and `findById()` results, but the JSON file is not deleted.
- **Hard delete:** `deleteSkill(id)` physically removes the JSON file.
- **Save:** `saveSkill(descriptor)` creates/overwrites the JSON file, creating the skills directory if needed.
- **ID sanitization:** Non-alphanumeric characters (except `.`, `_`, `-`) are replaced with `_` to produce safe filenames.

### `NoOpSkillService`

Null-object implementation. All queries return empty results; `executeSkill()` returns an empty string. Used as a default when skills are disabled.

## Data Model

**`SkillDescriptor`** — a Java record:

```
SkillDescriptor(String id, String name, String description, String filePath, List<String> steps, String pluginId)
```

| Field | Description |
|-------|-------------|
| `id` | Unique identifier, used for lookup and filename. |
| `name` | Human-readable display name. |
| `description` | Brief description of what the skill does. |
| `filePath` | Path to the skill definition source file (if applicable). |
| `steps` | Ordered list of step descriptions to execute. |
| `pluginId` | Plugin that provided this skill, or `null` for user-defined skills. |

Convenience constructors omit `steps`/`pluginId` (defaults: empty list, null).

## File Format

Example skill file (`~/.coderhino/skills/my-skill.json`):

```json
{
  "id": "my-skill",
  "name": "My Skill",
  "description": "Does something useful",
  "filePath": "/path/to/definition",
  "steps": ["Step 1: do A", "Step 2: do B"]
}
```

## Integration

- Registered in `ServiceRegistry.createDefault()` alongside other services.
- Consumed by the slash-command system when a user invokes a custom skill command.
- Plugin system can contribute skills by calling `FileSystemSkillService.saveSkill()` with a `pluginId`.
