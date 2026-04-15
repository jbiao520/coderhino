# Frontend Context — `src/main/client/src/context/`

React Context providers that share global state across the frontend component tree.

## MultiProjectContext

Manages the multi-project workspace — which projects are open, their sessions, and the active session per project.

### Provider

`<MultiProjectProvider>` wraps the app tree and exposes state/actions via `useMultiProject()`.

### State Shape

| Field | Type | Description |
|-------|------|-------------|
| `openProjectIds` | `string[]` | Ordered list of currently open project IDs |
| `projects` | `Record<string, ProjectDto>` | Project metadata keyed by ID |
| `sessionsByProject` | `Record<string, SessionDto[]>` | Sessions loaded per project |
| `activeSessionByProject` | `Record<string, string>` | Currently selected session ID per project |
| `recentSessionOrder` | `string[]` | MRU-ordered session IDs (max 20), used to determine the "active" project |
| `loading` | `boolean` | True while restoring persisted projects on mount |

### Actions

| Action | Signature | Behavior |
|--------|-----------|----------|
| `openProject` | `(project: ProjectDto) => void` | Adds a project to the open set and fetches its sessions |
| `closeProject` | `(projectId: string) => void` | Removes project, clears its active session, prunes recent order |
| `setActiveSession` | `(projectId, sessionId) => void` | Sets the active session for a project; moves session to front of MRU list |
| `refreshSessions` | `(projectId) => Promise<void>` | Re-fetches sessions for a single project from the API |
| `getActiveProject` | `() => ProjectDto \| null` | Returns the project owning the most-recently-used session |
| `getActiveProjectForSession` | `(sessionId) => ProjectDto \| null` | Reverse lookup: given a session ID, find its owning project |

### Persistence

State is persisted to `localStorage` under key `coderhino-multi-project`. The persisted shape is:

```typescript
interface PersistedState {
  openProjectIds: string[];
  lastActiveSessionByProject: Record<string, string>;
  recentSessionOrder: string[];
}
```

On mount, the provider restores persisted project IDs, re-fetches project metadata and sessions from the API, and silently drops any projects that fail to load.

### Session Ownership Filter

`keepSessionsOwnedByProject(projectId, sessions)` filters the session list returned by the API to only include sessions whose `session.projectId` matches. This prevents sessions from appearing under the wrong project if the API returns a broader set.

### Hook

```typescript
function useMultiProject(): MultiProjectContextType
```

Throws if called outside `<MultiProjectProvider>`.

### Test Coverage

`MultiProjectContext.test.tsx` covers:
- Hook guard (throws outside provider)
- Initial empty state
- `openProject` — adds project, loads sessions, no duplicates, persists to localStorage
- `closeProject` — removes project and active session, persists
- `setActiveSession` — sets active session, maintains MRU order, persists
- `getActiveProject` — returns project of most recent session, or null
- `getActiveProjectForSession` — resolves session to project, or null
- `refreshSessions` — reloads sessions for a project
- localStorage persistence — restores from storage, handles corrupted JSON gracefully
