# session-context-ai-history Specification

## Purpose
TBD - created by archiving change compact-context-ai-history. Update Purpose after archive.
## Requirements
### Requirement: Compact paired AI history entries in session context
The system SHALL present raw AI history in the session context panel as compact turn-oriented entries. When a raw `request` entry is immediately followed by a raw `response` entry, the UI SHALL render them inside a single history entry rather than as two separate rows.

#### Scenario: Pair adjacent request and response entries
- **WHEN** the session context panel receives raw AI history with a `request` entry followed immediately by a `response` entry
- **THEN** the panel renders one AI history row for that pair
- **THEN** expanding that row reveals both the request payload and the response payload in chronological order

#### Scenario: Preserve unmatched raw history entries
- **WHEN** the session context panel receives a raw AI history entry that does not have an immediately adjacent counterpart of the opposite direction
- **THEN** the panel renders that payload in its own AI history row
- **THEN** the payload remains expandable without requiring a synthetic paired entry

### Requirement: Icon-based disclosure for AI history entries
The system SHALL use a compact expand/collapse icon control at the right side of each AI history entry instead of a text button, and the AI history rows SHALL remain collapsed by default without rendering helper copy about their folded state.

#### Scenario: Render compact collapsed entry by default
- **WHEN** the session context panel first renders AI history with one or more entries
- **THEN** each entry appears collapsed by default
- **THEN** the row does not display the text `Show raw message`, `Hide raw message`, or `Folded by default`

#### Scenario: Toggle entry expansion with the icon control
- **WHEN** the user activates the disclosure icon for an AI history entry
- **THEN** the entry expands in place and reveals its raw payload content
- **THEN** activating the same icon again collapses the entry back to its compact state

