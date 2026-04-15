# task-completion-notifications Specification

## Purpose
TBD - created by archiving change add-task-completion-notifications. Update Purpose after archive.
## Requirements
### Requirement: Completed background tasks shall surface project-scoped sidebar notifications
The system SHALL surface completion notifications for background tasks and completed AI chat runs created from the web application. When a background task reaches `DONE` or an AI chat run reaches a completed terminal state and is associated with a project, the web UI SHALL increment an unseen completion indicator for that project unless the user is already viewing the associated project context at the moment the completion is observed.

#### Scenario: Background task completes for inactive session
- **WHEN** a background task associated with project P1 and session S2 reaches `DONE` while the user is viewing a different project or session
- **THEN** the web client SHALL mark that completion as unseen for project P1
- **AND** the left sidebar project icon for P1 SHALL display a red badge indicating at least one unseen completion

#### Scenario: AI run completes for inactive project context
- **WHEN** an AI chat run associated with project P1 and session S2 reaches a completed terminal state while the user is viewing a different project or session
- **THEN** the web client SHALL mark that completion as unseen for project P1
- **AND** the left sidebar project icon for P1 SHALL display a red badge indicating at least one unseen completion

#### Scenario: Completion occurs in active project context
- **WHEN** a background task or AI chat run associated with the currently visible project context reaches completion
- **THEN** the web client SHALL NOT increment an unseen project badge for that completion

#### Scenario: Multiple completions accumulate for the same project
- **WHEN** multiple distinct background tasks or AI chat runs for project P1 complete before the user returns to that project
- **THEN** the sidebar badge for P1 SHALL reflect the accumulated unseen completion count

### Requirement: Task completion events shall be deduplicated in the browser
The system SHALL identify each completion notification with a stable identifier so the browser can ignore duplicate deliveries caused by reconnects, replay, or repeated event fetches. Background task completions SHALL use a stable task identifier, and AI run completions SHALL use the stable run identifier.

#### Scenario: Duplicate task completion delivery after reconnect
- **WHEN** the browser receives the same completed task notification more than once for task T1
- **THEN** the web client SHALL count task T1 only once toward unseen completion state
- **AND** the notification sound SHALL play at most once for task T1

#### Scenario: Duplicate AI run completion delivery after reconnect
- **WHEN** the browser receives the same completed AI run notification more than once for run R1
- **THEN** the web client SHALL count run R1 only once toward unseen completion state
- **AND** the notification sound SHALL play at most once for run R1

### Requirement: The web UI shall play a short audio cue for newly observed task completions
The system SHALL attempt to play a very short notification sound when the browser first observes a new background task or AI run completion event.

#### Scenario: Audio playback succeeds for AI completion
- **WHEN** the browser observes a new completed AI run notification for run R1
- **THEN** the UI SHALL play a short completion sound once for R1

#### Scenario: Audio playback is blocked
- **WHEN** the browser observes a new completed background task or AI run notification but the browser blocks audio playback
- **THEN** the UI SHALL still update unseen completion badge state
- **AND** the failure to play audio SHALL NOT interrupt the application

### Requirement: Viewing the relevant project context shall clear unseen completion state
The system SHALL clear unseen completion indicators when the user returns to the relevant project context so the sidebar reflects only completions the user has not yet visited.

#### Scenario: User opens the notified project
- **WHEN** project P1 has unseen background task or AI run completion notifications and the user navigates to project P1
- **THEN** the UI SHALL clear or reduce P1's unseen completion badge according to the completions now in view

#### Scenario: Completion lacks project metadata
- **WHEN** a background task reaches `DONE` or an AI run reaches a completed terminal state without an associated project ID
- **THEN** the sidebar SHALL NOT show that completion on any project icon

