# toolbar-service-status Specification

## Purpose
Provides a workspace toolbar entry point for viewing MCP server, LSP, and plugin integration status via a tabbed popup interface.

## ADDED Requirements

### Requirement: Workspace toolbar exposes service status entry point
The web workspace toolbar SHALL expose a service status action on the right side after the file explorer action whenever the workspace toolbar is shown.

#### Scenario: Service status action is available in workspace sessions
- **WHEN** a user is viewing a project session route that renders the workspace toolbar
- **THEN** the toolbar shows a dedicated service status icon button after the file explorer button

#### Scenario: Service status action is absent outside workspace toolbar contexts
- **WHEN** a route does not render the workspace toolbar
- **THEN** the service status action is not shown

### Requirement: Service status popup provides tabbed integration views
When the user opens the service status action, the web UI SHALL display a popup with tabs for MCP server status, LSP status, and plugin status, and only the selected tab's content SHALL be visible.

#### Scenario: Popup opens with tab navigation
- **WHEN** the user activates the service status toolbar action
- **THEN** a popup opens containing tabs labeled MCP server status, LSP status, and Plugins status

#### Scenario: Selecting a tab changes the visible content
- **WHEN** the user selects one of the service status tabs
- **THEN** the popup shows the status list for that tab and hides the non-selected tab panels

### Requirement: Service status data is loaded from backend runtime snapshots
The service status popup SHALL load MCP server, LSP, and plugin status data from backend runtime state exposed through a web API, and it SHALL present loading, empty, and error states for each popup load.

#### Scenario: Status data load succeeds
- **WHEN** the popup requests service status data and the backend returns available status information
- **THEN** the UI renders MCP server rows, LSP rows, and plugin rows using the returned payload

#### Scenario: No entries exist for a service type
- **WHEN** the backend returns an empty list for MCP servers, LSP servers, or plugins
- **THEN** the selected tab shows an explicit empty state instead of a blank panel

#### Scenario: Status data load fails
- **WHEN** the popup requests service status data and the API request fails
- **THEN** the popup shows an error state that indicates the service status could not be loaded

### Requirement: Backend exposes aggregated service status payload
The web backend SHALL expose a read-only endpoint that returns aggregated service status payloads for MCP servers, LSP servers, and plugins without triggering new MCP or LSP process startup as a side effect.

#### Scenario: Aggregated status payload reflects manager state
- **WHEN** a client requests the service status endpoint
- **THEN** the response includes MCP server entries derived from registered definitions and current MCP connection state, LSP entries derived from registered definitions and current LSP connection state, and plugin entries derived from loaded plugin descriptors

#### Scenario: Status endpoint is passive
- **WHEN** a client requests the service status endpoint for registered MCP or LSP definitions that are not currently running
- **THEN** the endpoint reports their existing status snapshot without starting or reconnecting those services
