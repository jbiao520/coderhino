# component-style-migration

### Requirement: All components use centralized tokens
All 16 frontend components SHALL import their color/font values from `src/styles/tokens.ts` exclusively. No component SHALL define a local `T`, `TOKENS`, or equivalent color constant. No component SHALL contain hard-coded hex color values for themeable properties.

#### Scenario: Component imports from centralized tokens
- **WHEN** any component uses a color or font value
- **THEN** it imports `{ T }` from `'../styles/tokens'` (or the correct relative path) and references `T.bg`, `T.accent`, etc.

#### Scenario: No duplicate token definitions
- **WHEN** scanning all `.tsx` files in `src/`
- **THEN** no file contains a local const/object with properties like `bg: '#...'`, `surface: '#...'`, `border: '#...'` etc.

### Requirement: Hard borders replaced with shadows or background differentiation
Components SHALL NOT use `border: 1px solid <hard-color>` for visual separation between content areas. Instead, components SHALL use `box-shadow` (using `--shadow-sm`, `--shadow-md` tokens) or background-color contrast (e.g., `surface` vs `bg`) for visual hierarchy.

#### Scenario: Sidebar uses shadow for separation
- **WHEN** the sidebar renders next to the main content area
- **THEN** the visual separation uses `box-shadow` or no explicit border, not a hard `1px solid` line

#### Scenario: Cards use shadows instead of borders
- **WHEN** session list items, tool activity cards, or approval items render
- **THEN** they use `box-shadow` for elevation, not `border` for outline

### Requirement: Sans-serif for all UI text
All UI labels, titles, buttons, navigation items, form fields, status badges, and descriptive text SHALL use the sans-serif font stack (`--font-sans`). Monospace font (`--font-mono`) SHALL only be used for: code content in FileContentViewer, code blocks in chat messages, session IDs, file paths, and other machine-readable identifiers.

#### Scenario: Navigation labels use sans-serif
- **WHEN** sidebar nav items (Sessions, Approvals, Settings) render
- **THEN** they use `var(--font-sans)`

#### Scenario: Page titles use sans-serif
- **WHEN** any page title (Session, Settings, Approvals) renders
- **THEN** it uses `var(--font-sans)`

#### Scenario: Monospace reserved for identifiers
- **WHEN** a session ID, file path, or code snippet is displayed
- **THEN** it uses `var(--font-mono)`

### Requirement: Generous Notion-style spacing
Components SHALL use generous padding and gap values consistent with Notion's visual density. Minimum padding for interactive elements SHALL be 12px. Minimum gap between major content sections SHALL be 16px. Message areas, form fields, and list items SHALL have at least 16px vertical padding.

#### Scenario: Sidebar nav items have adequate spacing
- **WHEN** sidebar navigation links render
- **THEN** each link has at least 8px vertical padding and 12px horizontal padding

#### Scenario: Session list items have breathing room
- **WHEN** session list items render
- **THEN** each item has at least 14px vertical padding and 20px horizontal padding

### Requirement: Colorful status badges
Status badges (session status, run status, approval status) SHALL use colored backgrounds from the expanded palette (green for active/success, blue for pending/running, red for error/denied, muted gray for idle). Badge text SHALL remain readable against the background.

#### Scenario: Active session badge
- **WHEN** a session has status "ACTIVE"
- **THEN** the badge uses `var(--green)` background at low opacity with green text

#### Scenario: Pending approval badge
- **WHEN** an approval has status "PENDING"
- **THEN** the badge uses `var(--accent)` background at low opacity with accent-colored text

#### Scenario: Error status badge
- **WHEN** a run or approval has an error/denied status
- **THEN** the badge uses `var(--red)` background at low opacity with red text

### Requirement: Rounded corners
Interactive elements (buttons, inputs, cards, badges) SHALL use border-radius values from the design tokens (`--radius-sm` = 6px, `--radius-md` = 10px, `--radius-lg` = 14px). Cards and panels SHALL use at least `--radius-md`. Badges and small pills SHALL use at least `--radius-sm`.

#### Scenario: Session list cards have rounded corners
- **WHEN** session list items render
- **THEN** they have `border-radius` of at least `var(--radius-md)`

#### Scenario: Buttons have rounded corners
- **WHEN** any button renders
- **THEN** it has `border-radius` of at least `var(--radius-sm)`
