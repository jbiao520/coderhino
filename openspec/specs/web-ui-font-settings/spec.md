# web-ui-font-settings Specification

## Purpose
TBD - created by archiving change add-web-ui-font-settings. Update Purpose after archive.
## Requirements
### Requirement: General settings expose web UI font controls
The system SHALL provide font controls in the General settings section for the project sidebar and chat page. The controls MUST allow the user to configure each surface independently and MUST submit through the existing web settings save flow.

#### Scenario: General settings show separate sidebar and chat font controls
- **WHEN** the user navigates to the General settings section
- **THEN** the UI displays project sidebar font controls and chat page font controls as distinct settings groups, adhering to the new modern design guidelines

#### Scenario: Font settings save through web settings API
- **WHEN** the user changes a sidebar or chat font setting and saves the General settings form
- **THEN** the client sends the updated font settings in the existing `/api/settings` request payload

### Requirement: Web UI font settings are persisted and restored
The system SHALL persist sidebar and chat page font settings as part of web settings so the saved values are returned on subsequent `GET /api/settings` requests and restored after reload.

#### Scenario: Saved font settings are returned from settings API
- **WHEN** font settings were previously saved
- **THEN** a later `GET /api/settings` response includes the saved sidebar and chat page font values

#### Scenario: Missing font settings fall back to defaults
- **WHEN** a user has no previously saved font settings
- **THEN** the system returns default sidebar and chat page font values that preserve the current UI appearance

### Requirement: Sidebar font settings affect the project sidebar in the active UI
The system SHALL apply saved project sidebar font settings to the project sidebar UI in the current browser session after a successful settings save.

#### Scenario: Sidebar typography updates after save
- **WHEN** the user saves a new project sidebar font setting
- **THEN** the visible project sidebar reflects the updated font configuration without requiring a page reload

### Requirement: Chat page font settings affect the chat page in the active UI
The system SHALL apply saved chat page font settings to the chat page UI in the current browser session after a successful settings save.

#### Scenario: Chat typography updates after save
- **WHEN** the user saves a new chat page font setting while the chat UI is open
- **THEN** the visible chat page reflects the updated font configuration without requiring a page reload

