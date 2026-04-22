# component-style-migration Specification

## Purpose
TBD - updated by archiving change macos-style-web-ui. Refine purpose as needed.

## Requirements
### Requirement: All components use centralized tokens
All frontend components SHALL import their color and font values from shared theme tokens rather than defining component-local theme constants or hard-coded themeable color values.

#### Scenario: Component imports from centralized tokens
- **WHEN** any component uses a color or font value
- **THEN** it imports those values from the shared theme token layer

#### Scenario: No duplicate token definitions
- **WHEN** scanning theme-aware frontend components
- **THEN** no file contains a duplicate local token object for shared theme colors or fonts

### Requirement: Hard borders replaced with shadows or background differentiation
Components SHALL use layered surfaces, subtle hairline tokens, and elevation to separate major content areas. Components SHALL NOT rely on heavy hard-coded borders for primary hierarchy, and any visible panel edge SHALL use shared low-contrast border or hairline tokens instead of arbitrary gray lines.

#### Scenario: Sidebar uses layered separation
- **WHEN** the sidebar renders beside the main workspace
- **THEN** the separation is created with surface contrast, shadow, or shared hairline styling rather than a heavy hard border

#### Scenario: Panels and cards use soft hierarchy
- **WHEN** panels, cards, or popup surfaces render
- **THEN** they use shared surface, shadow, and hairline primitives instead of standalone hard border styling

### Requirement: Sans-serif for all UI text
All UI labels, titles, buttons, navigation items, form fields, status badges, and descriptive text SHALL use the sans-serif font stack (`--font-sans`). Monospace font (`--font-mono`) SHALL only be used for code content, identifiers, file paths, and other machine-readable values.

#### Scenario: Navigation labels use sans-serif
- **WHEN** sidebar navigation items render
- **THEN** they use `var(--font-sans)`

#### Scenario: Monospace reserved for identifiers
- **WHEN** a session ID, file path, or code snippet is displayed
- **THEN** it uses `var(--font-mono)`

### Requirement: Generous Notion-style spacing
Components SHALL use spacing that balances desktop density with breathing room. Toolbar controls SHALL remain compact enough for workspace-heavy screens, while major panels, cards, lists, and message areas SHALL preserve clear padding and gap values that prevent a cramped layout.

#### Scenario: Toolbar controls fit desktop workspace density
- **WHEN** toolbar buttons, segmented controls, and search fields render in the app chrome
- **THEN** they use compact desktop-sized heights and spacing that keep primary actions visible without feeling crowded

#### Scenario: Content sections preserve breathing room
- **WHEN** panels, lists, chat areas, and settings sections render
- **THEN** each section uses shared padding and gap values that visibly separate groups of content

### Requirement: Colorful status badges
Status badges SHALL use themed color treatments that keep state visible and readable across active, pending, success, error, and idle states.

#### Scenario: Active session badge
- **WHEN** a session has an active or success state
- **THEN** the badge uses the theme's success treatment with readable text contrast

#### Scenario: Error status badge
- **WHEN** a run or approval has an error or denied state
- **THEN** the badge uses the theme's error treatment with readable text contrast

### Requirement: Rounded corners
Interactive elements and surfaces SHALL use rounded corners that match a desktop-window aesthetic. Outer shells and prominent panels SHALL use the largest radius tokens, while buttons, inputs, badges, and segmented controls SHALL use smaller shared radius tokens for a consistent control family.

#### Scenario: Window shell and panels use stronger radii
- **WHEN** the app shell or a major workspace panel renders
- **THEN** it uses a shared medium-to-large radius token that is visually consistent with the macOS-style shell

#### Scenario: Controls use consistent rounded geometry
- **WHEN** a button, input, badge, or segmented control renders
- **THEN** it uses a shared small-to-medium radius token rather than an arbitrary component-local radius

### Requirement: Shared controls follow desktop-app interaction styling
Shared buttons, segmented controls, search fields, tab strips, and popup triggers SHALL use one desktop-inspired interaction language across hover, focus, selected, and pressed states.

#### Scenario: Interactive controls share visual states
- **WHEN** the user hovers or focuses a shared control
- **THEN** the control shows a consistent combination of surface change, subtle shadow, or focus ring defined by shared styles

#### Scenario: Selected controls use accent treatment consistently
- **WHEN** a tab, toolbar toggle, or segmented option is selected
- **THEN** it uses the same shared accent and surface treatment used elsewhere in the workspace
