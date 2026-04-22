# composer-toolbar Specification

## Purpose
TBD - created by archiving change add-composer-toolbar and updated by archiving change macos-style-web-ui.

## Requirements
### Requirement: Toolbar is shown with composer
The web chat composer SHALL render a toolbar integrated with the surrounding workspace chrome so the run configuration controls feel like part of the desktop-style shell rather than a disconnected web form.

#### Scenario: Toolbar is shown with composer
- **WHEN** a user opens a chat session in the web UI
- **THEN** the composer displays a desktop-style toolbar in the input area
- **THEN** the toolbar includes a model selector
- **THEN** the toolbar includes controls for build mode and plan mode

### Requirement: Session toolbar actions use accurate affordances
The web chat composer SHALL present session-related toolbar actions with icons, labels, grouping, and selected states that match the macOS-inspired control language while remaining understandable without guesswork.

#### Scenario: New session button exposes tooltip
- **WHEN** a user hovers the new session button in the workspace toolbar
- **THEN** the UI shows a tooltip with the text `new session`
- **THEN** the button styling matches the shared desktop-style toolbar controls

#### Scenario: Panel fold action uses a matching icon
- **WHEN** a user views the main toolbar action that folds the left panel
- **THEN** the action uses an icon that communicates panel folding rather than a folder or file-system action
- **THEN** the action uses the same toolbar button treatment as adjacent session controls

### Requirement: Composer toolbar initializes from session-backed state
The system SHALL provide the chat composer with the current session configuration needed to render the toolbar state consistently across refreshes and resumed sessions.

#### Scenario: Existing session configuration is displayed
- **WHEN** the chat page loads a session with saved composer configuration
- **THEN** the toolbar shows the current model selection from the session data
- **THEN** the toolbar shows the current build and plan mode state from the session data

### Requirement: Message submission applies composer toolbar selections
The system SHALL apply the current composer toolbar selections to the next submitted run so that execution matches the user-visible configuration.

#### Scenario: Submit uses selected model and modes
- **WHEN** a user changes toolbar selections and submits a message
- **THEN** the message submission request includes the selected model
- **THEN** the message submission request includes the selected build and plan mode values
- **THEN** the resulting run uses those submitted values

### Requirement: Model mode is conditional
The system SHALL show the model mode control only when model mode is supported for the active session context or selected model.

#### Scenario: Model mode is available
- **WHEN** the session payload indicates model mode is supported
- **THEN** the composer toolbar displays a model mode control
- **THEN** the user can choose among the supported model mode values

#### Scenario: Model mode is unavailable
- **WHEN** the session payload indicates model mode is not supported
- **THEN** the composer toolbar does not render the model mode control

### Requirement: Unsupported model mode selections are cleared before execution
The system SHALL prevent sending a stale or unsupported model mode when the active configuration no longer supports it.

#### Scenario: Switching configuration removes model mode support
- **WHEN** a user changes to a model or session configuration where model mode is not supported
- **THEN** any previously selected model mode is cleared or ignored
- **THEN** the next submission does not include an unsupported model mode value

### Requirement: Toolbar remains usable during normal chat interaction
The system SHALL preserve existing composer behavior while presenting the toolbar as part of the macOS-style workspace chrome, and toolbar affordances SHALL remain clear during normal chat interaction on both desktop and smaller screens.

#### Scenario: Existing send behavior is preserved
- **WHEN** a user types a message and presses Enter without Shift
- **THEN** the composer submits the message using the current toolbar selections
- **THEN** the send button and running-state behavior continue to work as before

#### Scenario: Toolbar actions remain understandable
- **WHEN** a user interacts with session-related actions in the toolbar during normal chat use
- **THEN** hover text, grouping, and selected states make each action's purpose clear without changing submission behavior
