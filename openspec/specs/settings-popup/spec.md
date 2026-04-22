# settings-popup Specification

## Purpose
TBD - created by archiving change optimize-settings-popup. Update Purpose after archive.

## Requirements
### Requirement: Settings popup provides modernized navigation layout
The system SHALL display the settings UI in a macOS-inspired modal or sheet surface with a distinct header, layered sidebar-based navigation on desktop screens, and a responsive compact layout on smaller screens. The layout MUST preserve clear category switching while matching the surrounding shell styling.

#### Scenario: User opens settings on a desktop screen
- **WHEN** the user triggers the action to open settings on a screen wide enough for desktop layout
- **THEN** the settings surface opens with desktop-style chrome and layered panel styling
- **THEN** the layout displays a sidebar with categories on the left and the selected category content on the right

#### Scenario: User opens settings on a mobile screen
- **WHEN** the user triggers the action to open settings on a smaller screen
- **THEN** the settings surface switches to a compact responsive layout that maximizes usable space
- **THEN** category navigation remains available without requiring the desktop sidebar layout

### Requirement: Settings UI provides immediate interactive feedback
The system SHALL ensure that all interactive elements within the settings surface provide immediate hover, focus, active, and selected feedback consistent with the macOS-style control system.

#### Scenario: User hovers over a settings control
- **WHEN** the user hovers a settings button, toggle, navigation item, or selectable option
- **THEN** the element visually changes to indicate interactivity using the shared desktop-style control treatment

#### Scenario: User interacts via keyboard
- **WHEN** the user navigates the settings surface using the keyboard
- **THEN** a clear focus indicator appears around the currently focused interactive element without breaking the modal's visual hierarchy

### Requirement: Settings popup manages reference source paths
The settings popup SHALL let the user view, add, edit, and remove multiple reference source paths as part of persisted workspace settings.

#### Scenario: User adds multiple reference source paths
- **WHEN** the user enters multiple filesystem paths in settings and saves
- **THEN** the settings UI submits the complete ordered list of reference source paths to the settings API

#### Scenario: User removes a reference source path
- **WHEN** the user deletes a previously configured reference source path and saves settings
- **THEN** the removed path is no longer present in persisted settings

### Requirement: Settings popup restores saved reference source paths
The settings popup SHALL display previously saved reference source paths when settings are loaded so the user can review and edit them.

#### Scenario: Saved reference source paths are shown on load
- **WHEN** the settings popup loads and settings contain saved reference source paths
- **THEN** the UI renders each saved path in the reference source path controls
