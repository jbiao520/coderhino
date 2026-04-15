# Frontend Hooks

Custom React hooks in `src/main/client/src/hooks/`. These hooks encapsulate all API communication, SSE streaming, and browser-level interactions for the frontend. Pages consume these hooks rather than calling the API layer directly.

> See [Architecture > Frontend](../CLAUDE.md) for the broader frontend context.

## Hook Overview

| Hook | Purpose | Used By |
|------|---------|---------|
| `useStreamingSession` | SSE-based real-time chat with a single session | ChatPage |
| `useSession` | Fetch a single session's metadata | Session detail views |
| `useSessions` | List and create sessions for a project | SessionListPage |
| `useFileTree` | Lazy-load directory listings with caching | File browser components |
| `useCredentials` | Read/write API credentials | SettingsPage |
| `useSettings` | Read/write application settings | SettingsPage |
| `useSearchDirectories` | Debounced directory search (300ms) | Project picker / search UI |
| `useKeyboardShortcuts` | Global Cmd/Ctrl+Tab session switching | App root layout |

---

## useStreamingSession

**File:** `useStreamingSession.ts`
**State model:** `useReducer` with a discriminated union `Action` type (11 variants) dispatched into an immutable `StreamingState`.

```typescript
StreamingState {
  session: SessionDto | null;
  messages: StreamingMessage[];        // { role, content } — finalized user/assistant turns
  liveText: string;                    // text being streamed from the current assistant turn
  toolActivity: ToolActivity[];        // tools invoked in the current run
  activeRun: RunDto | null;
  runStatus: 'idle' | 'running' | 'completed' | 'error';
  error: string | null;
  loading: boolean;
  chunkCount: number;                  // increments per SSE text-chunk
  completedRunIds: Set<string>;        // prevents duplicate RUN_STARTED for reconnects
}
```

**Action types:**

| Action | Trigger |
|--------|---------|
| `SESSION_LOADED` | Initial `GET /api/sessions/{id}` succeeds |
| `SESSION_ERROR` | Initial fetch fails |
| `RUN_STARTED` | `POST /api/sessions/{id}/runs` returns a `RunDto` |
| `TEXT_CHUNK` | SSE `text-chunk` event |
| `TOOL_CALL` | SSE `tool-call` event |
| `TOOL_RESULT` | SSE `tool-result` event |
| `RUN_COMPLETED` | SSE `completed` event — finalizes `liveText` into `messages` |
| `RUN_CANCELLED` | SSE `cancelled` or user-initiated cancel |
| `RUN_FAILED` | SSE `failed` event |
| `SSE_ERROR` | SSE connection error |
| `USER_MESSAGE` | User submits a message (optimistic append to `messages`) |

**SSE connection:** Opens an `EventSource` to `/api/sessions/{id}/events`. Subscribes to: `ready`, `text-chunk`, `status`, `tool-call`, `tool-result`, `usage`, `completed`, `failed`, `cancelled`, `server-shutdown`. On error, closes and reconnects after 2 seconds. `server-shutdown` closes without reconnect.

**Returns:** `StreamingState` plus `submitMessage(message)` and `cancelRun()`.

**Cancellation safety:** `cancelRun()` sends `DELETE /api/sessions/{id}/runs/{runId}` and immediately dispatches `RUN_CANCELLED` on success.

---

## useSession

**File:** `useSession.ts`

Fetches a single session by ID. Returns `{ session, loading, error }`. Re-fetches when `sessionId` changes. Uses a `cancelled` flag in the effect cleanup to prevent state updates after unmount.

---

## useSessions

**File:** `useSessions.ts`

Lists sessions for a given `projectId` and provides `createSession(projectId)` which calls `POST /api/sessions`. A `tick` state counter drives re-fetches — `reload()` increments it. If the created session belongs to the current project, the list is automatically reloaded.

Returns `{ sessions, loading, error, createSession, reload }`.

---

## useFileTree

**File:** `useFileTree.ts`

Lazy-loads directory listings via `api.files.tree(projectPath, dirPath)`. Maintains an in-memory `Map<string, DirectoryListing>` keyed by `${projectPath}::${dirPath}`. Exposes `fetchDirectory(path, dir)` (cache-aware) and `clearCache()`.

No loading/error states — callers handle those.

---

## useCredentials

**File:** `useCredentials.ts`

Fetches credentials on mount via `api.credentials.get()`. Provides `saveCredentials(updates)` which calls `api.credentials.update()` and optimistically updates local state.

Returns `{ credentials, loading, error, saving, saveCredentials }`.

---

## useSettings

**File:** `useSettings.ts`

Fetches application settings on mount via `api.settings.get()`. Provides `saveSettings(updates)` which calls `api.settings.update()` and updates local state.

Returns `{ settings, loading, error, saving, saveSettings }`.

---

## useSearchDirectories

**File:** `useSearchDirectories.ts`

Debounced (300ms) search for directories matching a query string. Each new search cancels the previous request via `AbortController`. Empty queries immediately clear results without hitting the API.

Returns `{ results, loading, error, search(query) }`.

---

## useKeyboardShortcuts

**File:** `useKeyboardShortcuts.ts`

Registers a global `keydown` listener for **Cmd/Ctrl+Tab** (and Cmd/Ctrl+Shift+Tab for reverse). Cycles through sessions in `recentSessionOrder` from `MultiProjectContext`, navigating to the appropriate project+session route. Falls back to fetching session metadata if the project mapping isn't in memory.

Cleanup removes the event listener on unmount.

---

## Common Patterns

All hooks follow these conventions:

- **Cancellation safety:** Effects use a `cancelled` boolean or `AbortController` to ignore stale responses after unmount or re-render.
- **API layer indirection:** Most hooks call through `api.*` from `../api/client` rather than raw `fetch`. The exception is `useStreamingSession`, which uses raw `fetch` for the initial session load and `EventSource` for SSE.
- **Consistent return shape:** `{ data, loading, error }` for read hooks; `{ data, loading, error, saving, save }` for read/write hooks.
- **No global state:** Hooks are self-contained. Session-level state is managed by `useStreamingSession`'s reducer, not a context or store.
