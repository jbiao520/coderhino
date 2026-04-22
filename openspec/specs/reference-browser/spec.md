# reference-browser Specification

## Purpose
TBD - created by archiving change enhance-reference-browser. Update Purpose after archive.

## Requirements
### Requirement: Composer reference browser popup
The system SHALL open a dedicated reference browser popup when the user activates the composer reference control. The popup SHALL present available bundled references in a list that supports pagination when the number of references exceeds the configured page size.

#### Scenario: Open the browser with the available references
- **WHEN** the user activates the composer reference control and references are available
- **THEN** the system opens a popup containing the reference list and shows the first page of results

#### Scenario: Show an empty state when no references exist
- **WHEN** the user activates the composer reference control and no references are available
- **THEN** the system opens the popup and shows a clear empty state instead of list rows

### Requirement: Frontend-only title search
The system SHALL filter references in the browser on the client using the already-loaded reference list. The filter SHALL match against the reference title only and SHALL be case-insensitive.

#### Scenario: Filter references by title match
- **WHEN** the user enters a search query that matches one or more reference titles
- **THEN** the system updates the browser list to show only references whose titles contain the query text

#### Scenario: Reset pagination for filtered results
- **WHEN** the user changes the search query after navigating away from the first page
- **THEN** the system returns the browser to the first page of the filtered result set

#### Scenario: Show no-match feedback
- **WHEN** the user enters a search query that matches no reference titles
- **THEN** the system shows a no-results state in the browser without issuing a new backend request

### Requirement: Reference preview
The system SHALL provide a preview action for each reference in the browser. Activating preview SHALL open a popup that shows the full reference content rendered as formatted markdown.

#### Scenario: Preview a reference without inserting it
- **WHEN** the user activates the preview action for a reference
- **THEN** the system opens a preview popup showing that reference's full markdown content and does not insert markdown into the composer

#### Scenario: Preserve browser context after closing preview
- **WHEN** the user closes the preview popup
- **THEN** the system returns the user to the reference browser with the prior search query and page still intact

### Requirement: Insert a selected reference from the browser
The system SHALL allow the user to insert a reference's markdown into the composer from the browser. Insertion SHALL preserve the existing behavior for replacing the current selection or inserting at the current cursor position.

#### Scenario: Insert at the current cursor position
- **WHEN** the user inserts a reference while the composer has a caret position and no selected text
- **THEN** the system inserts the reference markdown at the caret position, closes the browser, and restores focus to the composer

#### Scenario: Replace the current selection
- **WHEN** the user inserts a reference while text is selected in the composer
- **THEN** the system replaces the selected text with the reference markdown and restores the updated selection state in the composer
