# theme-system

### Requirement: CSS custom property theme palettes
The system SHALL define two color palettes via CSS custom properties on `:root` (light) and `[data-theme="dark"]` (dark). Both palettes SHALL use warm tones — the light palette SHALL use warm whites/creams and the dark palette SHALL use warm grays (not blue-tinted blacks).

Tokens SHALL include: `--bg`, `--surface`, `--sidebar`, `--border`, `--text`, `--text-muted`, `--accent`, `--accent-hover`, `--green`, `--red`, `--purple`, `--orange`, `--pink`, `--shadow-sm`, `--shadow-md`, `--shadow-lg`, `--radius-sm`, `--radius-md`, `--radius-lg`, `--font-sans`, `--font-mono`.

#### Scenario: Light palette renders warm colors
- **WHEN** the `<html>` element has no `data-theme` attribute or `data-theme="light"`
- **THEN** CSS custom properties resolve to warm light values (e.g., `--bg: #ffffff`, `--text: #37352f`)

#### Scenario: Dark palette renders warm dark colors
- **WHEN** the `<html>` element has `data-theme="dark"`
- **THEN** CSS custom properties resolve to warm dark values (e.g., `--bg: #191919`, `--text: #e2e0dc`)

### Requirement: Centralized token bridge
`src/styles/tokens.ts` SHALL export a `T` object where every color value is a CSS variable reference string (e.g., `'var(--bg)'`). `src/types/theme.ts` SHALL be deleted. All components SHALL import tokens from `src/styles/tokens.ts` only.

#### Scenario: Component references token
- **WHEN** a component uses `T.bg` in an inline style
- **THEN** the rendered element has `background: var(--bg)` which resolves to the current theme's value

### Requirement: useTheme hook
The system SHALL provide a `useTheme` hook in `src/hooks/useTheme.ts` that resolves the effective theme from backend settings and system preference, sets `data-theme` on `<html>`, and persists the resolved theme to localStorage for instant-load on next visit.

#### Scenario: System theme follows OS preference
- **WHEN** user selects "system" in settings and OS is in dark mode
- **THEN** `document.documentElement.dataset.theme` is set to `"dark"`

#### Scenario: System theme follows OS preference (light)
- **WHEN** user selects "system" in settings and OS is in light mode
- **THEN** `document.documentElement.dataset.theme` is set to `"light"`

#### Scenario: Explicit theme overrides system
- **WHEN** user selects "dark" in settings regardless of OS preference
- **THEN** `document.documentElement.dataset.theme` is set to `"dark"`

#### Scenario: Theme persists across reloads
- **WHEN** user sets theme to "light" and reloads the page
- **THEN** the page renders in light mode without a flash of wrong theme

#### Scenario: OS preference change updates theme in real-time
- **WHEN** user has "system" theme selected and changes OS from light to dark
- **THEN** the UI switches to dark theme without page reload

### Requirement: Theme toggle control
The sidebar footer SHALL display a clickable theme icon that cycles through light → dark → system on each click. The icon SHALL display 🌞 for light, 🌙 for dark, and 💻 for system. The selection SHALL be persisted via the existing settings API.

#### Scenario: Toggle cycles themes
- **WHEN** user clicks the theme icon while in light mode
- **THEN** theme switches to dark, icon changes to 🌙

#### Scenario: Toggle wraps around to system
- **WHEN** user clicks the theme icon while in dark mode
- **THEN** theme switches to system, icon changes to 💻

### Requirement: No flash of wrong theme on initial load
The system SHALL set `data-theme` on `<html>` before React renders by reading from localStorage in `index.html` or a synchronous script block.

#### Scenario: First visit with no saved preference
- **WHEN** user visits for the first time with no localStorage entry
- **THEN** the page renders in light mode (default)

#### Scenario: Returning visit with saved preference
- **WHEN** user previously selected dark mode and returns
- **THEN** dark mode renders immediately without a light-mode flash
