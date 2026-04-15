# composer-input-history Specification

## Purpose
Enables keyboard navigation through previously submitted web chat composer inputs using ArrowUp/ArrowDown, with unsent draft preservation during history traversal.
## Requirements
### Requirement: Web chat composer SHALL support keyboard navigation through submitted input history
The system SHALL let the user move through previously submitted web chat composer inputs with `ArrowUp` and `ArrowDown` when the main composer textarea has focus and no higher-priority keyboard overlay is handling those keys.

#### Scenario: Navigate to an older submitted prompt
- **WHEN** the user focuses the web chat composer, has at least one previously submitted prompt in the current browser session, and presses `ArrowUp`
- **THEN** the composer SHALL replace the current textarea value with the most recent submitted prompt

#### Scenario: Navigate back toward newer submitted prompts
- **WHEN** the user is viewing an older submitted prompt from history and presses `ArrowDown`
- **THEN** the composer SHALL replace the textarea value with the next newer submitted prompt

### Requirement: Web chat composer SHALL preserve the current draft during history navigation
The system SHALL preserve the unsent draft that exists before history navigation begins and SHALL restore that draft when the user navigates back past the newest submitted history entry.

#### Scenario: Restore unsent draft after leaving history mode
- **WHEN** the user has typed an unsent draft, presses `ArrowUp` to enter submitted-input history, and then presses `ArrowDown` until they move past the newest history entry
- **THEN** the composer SHALL restore the exact unsent draft that existed before history navigation started

### Requirement: History navigation SHALL not override existing keyboard ownership rules
The system SHALL defer composer history navigation whenever another composer interaction already owns the relevant arrow-key input.

#### Scenario: Mention autocomplete keeps arrow-key control
- **WHEN** the file mention autocomplete menu is visible in the composer and the user presses `ArrowUp` or `ArrowDown`
- **THEN** the composer SHALL keep autocomplete navigation behavior and SHALL NOT replace the textarea value with a submitted history entry

#### Scenario: Command palette keeps arrow-key control
- **WHEN** the slash-command palette is visible in the composer and the user presses `ArrowUp` or `ArrowDown`
- **THEN** the composer SHALL keep command palette navigation behavior and SHALL NOT replace the textarea value with a submitted history entry

