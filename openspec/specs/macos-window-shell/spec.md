# macos-window-shell Specification

## Purpose
TBD - created by archiving change macos-style-web-ui. Update Purpose after archive.

## Requirements
### Requirement: Application shell renders macOS-style window chrome
The web UI SHALL render its primary desktop layout inside a macOS-inspired window shell with a distinct titlebar, layered content region, rounded outer frame, and elevated shadow so the application reads as a desktop workspace rather than a full-bleed browser page.

#### Scenario: Desktop shell is visible on large screens
- **WHEN** the user opens the web UI on a desktop-sized viewport
- **THEN** the application renders a visually distinct outer shell around the workspace
- **THEN** the shell includes a titlebar or chrome region above the main content
- **THEN** the shell uses rounded corners and elevated shadow styling

### Requirement: Shell includes traffic-light chrome without hiding required actions
The macOS-style shell SHALL include a red, yellow, and green traffic-light cluster in the chrome region, and that cluster SHALL NOT be the only way users can access required navigation or application actions.

#### Scenario: Traffic-light cluster renders in desktop chrome
- **WHEN** the desktop shell is shown
- **THEN** the titlebar displays a red, yellow, and green traffic-light cluster aligned with the shell chrome
- **THEN** the cluster remains visually consistent in both light and dark themes

#### Scenario: Required actions remain available outside traffic lights
- **WHEN** a user needs to navigate, open settings, or control workspace panels
- **THEN** the UI exposes those actions through standard buttons, menus, or keyboard interactions outside the traffic-light cluster

### Requirement: Shell surfaces support translucent layering with fallback
The shell SHALL support translucent layered surfaces for titlebars, sidebars, and popups when the browser supports them, and SHALL fall back to opaque surfaces that preserve contrast and separation when translucency effects are unavailable.

#### Scenario: Blur-capable browser renders translucent chrome
- **WHEN** the browser supports `backdrop-filter`
- **THEN** shell chrome and overlay surfaces use translucent layered styling with blur

#### Scenario: Non-supporting browser preserves legibility
- **WHEN** the browser does not support `backdrop-filter`
- **THEN** the same shell surfaces render with opaque fallback backgrounds and visible separation

### Requirement: Shell degrades cleanly on smaller screens
The application SHALL preserve usability on smaller screens by simplifying the outer window treatment while keeping navigation, toolbar actions, and content areas accessible.

#### Scenario: Mobile viewport uses compact shell treatment
- **WHEN** the user opens the web UI on a narrow viewport
- **THEN** the app may reduce outer margins, corner radius, or titlebar ornamentation
- **THEN** the workspace remains fully usable without horizontal overflow caused by shell chrome
