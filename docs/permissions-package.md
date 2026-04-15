# permissions Package

Detailed documentation for `com.coderhino.permissions` — the permission checking and enforcement layer.

## Overview

The permissions package controls which tool invocations are allowed, require user confirmation, or are blocked. It supports multiple permission modes and uses a layered approach: command classification, rule-based matching, path safety validation, and denial tracking with circuit-breaker escalation.

## Core Components

| Class | Responsibility |
|-------|---------------|
| `PermissionChecker` | Core mode-based permission resolution |
| `EnhancedPermissionChecker` | Extended checker with bash classification, circuit-breaker, and audit logging |
| `BashCommandClassifier` | Classifies bash commands into SAFE / CONFIRM / DENY categories |
| `RuleMatcher` | Matches tool invocations against configurable permission rules |
| `PathSafetyValidator` | Validates file paths against dangerous directories and config files |
| `DenialTracker` | Tracks cumulative denials per tool with soft/hard threshold escalation |
| `PermissionAuditLog` | Thread-safe audit log recording all permission decisions |

## Permission Modes

The system supports six modes (defined in `com.coderhino.types.PermissionMode`):

| Mode | Behavior |
|------|----------|
| **BYPASS** | All operations allowed unconditionally. Used for trusted/internal execution. |
| **DEFAULT** | Rule-based evaluation; unmatched operations prompt the user (`PermissionReason.Ask`). |
| **PLAN** | All operations denied. Used when only planning (no execution) is intended. |
| **AUTO** | Read-only tools auto-approved; bash commands classified via `BashCommandClassifier`; circuit-breaker demotes to DEFAULT after N risky commands. |
| **DONT_ASK** | Silent mode — no user prompts. Allowed if clearly safe, denied otherwise. Destructive commands always blocked. |
| **ACCEPT_EDITS** | File edit tools (`edit_file`, `write_file`) auto-approved; destructive operations still require confirmation. |

## PermissionChecker

The base checker (`PermissionChecker.java`) provides two entry points:

### `resolve(PermissionMode, PermissionResult)` — Simple Resolution

Translates a requested `PermissionResult` through the lens of the current mode:
- **BYPASS** → always `allow()`
- **Deny** → pass through
- **Ask** → pass through (mode-specific handling is in `EnhancedPermissionChecker`)
- Everything else → `allow()`

### `evaluate(PermissionContext, List<PermissionRule>)` — Rule-Based Evaluation

Evaluates a tool invocation against a list of `PermissionRule` objects, with mode-specific behavior:

```
BYPASS        → immediately allowed
DONT_ASK      → rules evaluated; no match → denied (silent)
ACCEPT_EDITS  → edit_file/write_file auto-allowed; others → rules; no match → ask
PLAN          → always denied
AUTO/DEFAULT  → rules evaluated; no match → ask (prompts user)
```

### `toPermissionResult(PermissionDecision)` — Decision Conversion

Converts a `PermissionDecision` (allowed + reason + mode) back to a `PermissionResult` (allow/ask/deny).

## EnhancedPermissionChecker

The extended checker (`EnhancedPermissionChecker.java`) layers additional intelligence on top of the base resolution:

### Circuit Breaker

Tracks consecutive risky operations via `AtomicInteger`. When the count reaches the threshold (default: 3), AUTO mode is demoted to DEFAULT — forcing user confirmation for all subsequent operations. Reset via `resetCircuitBreaker()`.

```
consecutiveRiskyCount >= threshold → effectiveAutoMode switches AUTO → DEFAULT
```

### Mode-Specific Logic

**AUTO mode** (`evaluateForAutoMode`):
1. Read-only tools → auto-allow
2. Bash commands → classified via `BashCommandClassifier`:
   - DENY → block + increment risky counter + check circuit breaker
   - SAFE → allow + reset risky counter
   - CONFIRM → escalate to user + increment risky counter
3. Dangerous operations → block
4. Safe bash commands → auto-allow
5. Safe paths → auto-allow
6. Unknown → escalate to user

**DONT_ASK mode** (`evaluateForDontAskMode`):
1. Read-only tools → allow
2. Bash DENY → block
3. Dangerous operations → block
4. Read-only bash commands → allow
5. Safe paths (non-destructive) → allow
6. Everything else → deny (silent, no prompt)

**ACCEPT_EDITS mode** (`evaluateForAcceptEditsMode`):
1. Read-only tools → allow
2. File edit tools (non-destructive) → allow
3. Dangerous operations → ask user
4. Everything else → ask user

### Tool Input Extraction

Uses duck-typing via reflection to extract commands and paths from tool inputs:
- Checks for known record types (`BashToolInput`, `FileToolInput`, `EditToolInput`)
- Falls back to reflection: looks for `command()` or `path()` methods on the input object

## BashCommandClassifier

Classifies raw bash command strings into three categories using regex pattern matching:

### Classification Priority (evaluated in order)

1. **DENY patterns** (checked first, always blocked):
   - Fork bombs (`:(){ :|:& };:`)
   - `rm -rf /`, `sudo rm`
   - Disk operations (`dd if=`, `mkfs`, `fdisk`)
   - `chmod 777 /`, `chown -R`
   - System operations (`sudo su`, `init 6`, `reboot`, `shutdown`)
   - Pipe to shell (`| sh`, `| bash`, `| python`, `| ruby`)
   - Overwrite system files (`> /etc/passwd`, `> /etc/shadow`)
   - Remote code execution (`curl | sh`, `wget | sh`)
   - Base64 decode pipe to shell

2. **CONFIRM patterns** (require approval):
   - Write redirects (`>>`, `>`)
   - Network tools (`curl`, `wget`, `scp`, `rsync`)
   - Git write operations (`commit`, `push`, `rebase`, `reset`, etc.)
   - Package management (`npm install`, `pip install`, etc.)
   - Script execution (`./script`, `sh`, `bash`, `python *.py`)
   - `rm`, `mv`, `cp`, `chmod`, `chown`
   - Process/service management (`kill`, `systemctl`, `docker run`)
   - `sudo`, `make`

3. **SAFE command patterns** (specific safe sub-commands):
   - `git status/log/diff/show/branch/...`
   - `mvn test/compile/verify/...`
   - `npm ls/list/info/...`
   - `sed` (without `-i`), `awk`
   - `java --version`, `python -V`, `node -v`

4. **SAFE first tokens** (known-safe command names):
   - File inspection: `cat`, `head`, `tail`, `grep`, `find`, `ls`, etc.
   - Text processing: `sort`, `uniq`, `diff`, `cut`, `paste`
   - System info: `uname`, `whoami`, `df`, `du`, `ps`, etc.
   - Build tools: `mvn`, `gradle`, `java`, `node`, `python`

5. **Default**: Unknown commands → CONFIRM

### API

```java
classifier.classify(command)    // → Classification enum (SAFE, CONFIRM, DENY)
classifier.isSafe(command)      // → boolean
classifier.isDeny(command)      // → boolean
classifier.isConfirm(command)   // → boolean
classifier.explain(command)     // → human-readable explanation string
```

## RuleMatcher

Stateless, thread-safe rule matching engine.

### Rule Format

Rules are specified as `Rule` records with three optional fields:
```java
record Rule(String toolPattern, String prefixPattern, String contentPattern)
```

Parsed from string format `"tool:prefix:content"` where `*` means "any".

### Match Results

| Result | Meaning |
|--------|---------|
| `NO_MATCH` | Rule does not match the invocation |
| `TOOL_MATCH` | Only tool name matched |
| `TOOL_PREFIX_MATCH` | Tool + prefix or tool + content matched |
| `FULL_MATCH` | All specified criteria matched |

Special: `shell` pattern also matches `bash` tool name (alias handling).

### API

```java
matcher.matches(rule, toolName, content)        // → MatchResult
matcher.matchesAny(rules, toolName, content)    // → boolean
matcher.findFirstMatch(rules, toolName, content) // → Rule or null
```

## PathSafetyValidator

Validates file paths against known dangerous locations. Constructed with a `repoRoot` to enable within-repo checks.

### Dangerous Directory Patterns

Blocks paths containing: `.git`, `.ssh`, `.aws`, `.kube`, `.docker`, `.gnupg`

### Config File Patterns

Flags files matching: `known_hosts`, `authorized_keys`, `config`, `.env*`, SSH keys (`id_rsa`, `id_ed25519`, etc.)

### API

```java
validator.isPathUnsafe(rawPath)    // → true if in dangerous directory
validator.isConfigFile(rawPath)    // → true if sensitive config/credential file
validator.isWithinRepo(rawPath)    // → true if path is under repoRoot
validator.isSafeForWrite(rawPath)  // → true if not unsafe and not config file
```

Factory: `PathSafetyValidator.forCurrentDirectory()` uses `user.dir` as repo root.

## DenialTracker

Tracks denial frequency per tool and globally, escalating through three states:

| State | Meaning |
|-------|---------|
| `ALLOWED` | No denials recorded |
| `SOFT_DENY` | Denials between soft and hard thresholds |
| `HARD_DENY` | Denials at or above hard threshold — tool effectively locked |

### Default Thresholds

- Soft denial: 3 denials
- Hard denial: 5 denials (or soft + 2 if overridden)

Thread-safe: uses `ConcurrentHashMap<String, AtomicReference<TrackerState>>` for per-tool state and `AtomicReference` for global state.

### API

```java
tracker.recordDenial(toolName)           // increment and return new state
tracker.recordHardDenial(toolName)       // immediately escalate to HARD_DENY
tracker.getState(toolName)               // current per-tool DenialState
tracker.getGlobalState()                 // current global DenialState
tracker.reset(toolName)                  // reset per-tool state to ALLOWED
tracker.resetAll()                       // reset everything
```

## PermissionAuditLog

Thread-safe (`CopyOnWriteArrayList`) log of all permission decisions.

### Entry Record

```java
record Entry(Instant timestamp, String toolName, String command,
             Classification classification, PermissionMode mode,
             Decision decision, String reason)
```

### Decision Types

| Decision | Meaning |
|----------|---------|
| `ALLOWED` | Operation permitted |
| `DENIED` | Operation blocked |
| `ESCALATED` | Operation escalated to higher scrutiny (e.g., AUTO → DEFAULT via circuit breaker) |

### API

```java
auditLog.record(toolName, command, classification, mode, decision, reason)
auditLog.entries()                    // all entries (unmodifiable copy)
auditLog.entriesForTool(toolName)     // filtered by tool
auditLog.countDecision(Decision)      // aggregate count
auditLog.countClassification(Classification)  // aggregate count
auditLog.clear()                      // clear all entries
```

## Data Flow

```
Tool Invocation
     │
     ▼
PermissionChecker.evaluate(context, rules)
     │
     ├── BYPASS ────────────► ALLOW
     ├── PLAN ───────────────► DENY
     │
     ▼ (for AUTO/DEFAULT/DONT_ASK/ACCEPT_EDITS)
EnhancedPermissionChecker.resolveWithContext(mode, result, toolName, input)
     │
     ├── BashCommandClassifier.classify(command)
     │       ├── DENY    ► block + audit + track denial
     │       ├── SAFE    ► allow + audit + reset circuit breaker
     │       └── CONFIRM ► escalate + audit + increment circuit breaker
     │
     ├── PathSafetyValidator.isPathUnsafe / isConfigFile
     │       └── dangerous path ► block
     │
     ├── DenialTracker (cumulative per-tool tracking)
     │
     └── PermissionAuditLog.record (all decisions logged)
```
