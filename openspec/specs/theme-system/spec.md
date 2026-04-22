# theme-system Specification

## Purpose
TBD - updated by archiving change macos-style-web-ui. Refine purpose as needed.

## Requirements
### Requirement: CSS custom property theme palettes
The system SHALL define two color palettes via CSS custom properties on `:root` (light) and `[data-theme="dark"]` (dark). Both palettes SHALL support a macOS-inspired visual language with neutral window tones, cool translucent chrome surfaces, desktop-style blue accents, subtle hairlines, and layered elevation for application shells and panels.

Tokens SHALL include: `--bg`, `--surface`, `--surface-hover`, `--surface-accent`, `--sidebar`, `--panel`, `--panel-muted`, `--window-bg`, `--window-chrome`, `--glass-bg`, `--glass-border`, `--border`, `--border-strong`, `--hairline`, `--text`, `--text-muted`, `--text-on-accent`, `--accent`, `--accent-hover`, `--accent-soft`, `--green`, `--red`, `--purple`, `--orange`, `--pink`, `--shadow-sm`, `--shadow-md`, `--shadow-lg`, `--shadow-xl`, `--radius-sm`, `--radius-md`, `--radius-lg`, `--radius-xl`, `--font-sans`, `--font-mono`.

#### Scenario: Light palette renders macOS-inspired chrome
- **WHEN** the `<html>` element has no `data-theme` attribute or `data-theme="light"`
- **THEN** CSS custom properties resolve to a bright neutral workspace palette with distinct window chrome, layered panel surfaces, and a saturated desktop-blue accent

#### Scenario: Dark palette renders macOS-inspired dark chrome
- **WHEN** the `<html>` element has `data-theme="dark"`
- **THEN** CSS custom properties resolve to a dark graphite workspace palette with readable translucent shell surfaces, layered panel contrast, and the same accent semantics as light mode

### Requirement: Theme tokens support shell translucency and fallbacks
The system SHALL expose theme tokens that allow shell chrome and popup surfaces to render with translucency when supported while preserving opaque fallback values that remain legible in all supported browsers.

#### Scenario: Shell uses tokenized translucent surfaces
- **WHEN** a shell or popup surface needs glass-like styling
- **THEN** the surface uses shared theme tokens such as `--glass-bg` and `--glass-border` rather than component-local color values

#### Scenario: Fallback values remain part of the theme contract
- **WHEN** translucency effects are unavailable or disabled
- **THEN** the same tokenized surfaces still render with solid theme values that preserve contrast and separation

### Requirement: Centralized token bridge
`src/styles/tokens.ts` SHALL export a `T` object where every color value is a CSS variable reference string. All components SHALL import tokens from that shared token source.

#### Scenario: Component references token
- **WHEN** a component uses a shared token in inline styles or stylesheets
- **THEN** the rendered UI resolves those references from the active CSS custom properties

### Requirement: useTheme hook
The system SHALL provide a `useTheme` hook in `src/hooks/useTheme.ts` that resolves the effective theme from backend settings and system preference, sets `data-theme` on `<html>`, and persists the resolved theme to localStorage for instant-load on next visit.

#### Scenario: System theme follows OS preference
- **WHEN** user selects `system` in settings and OS is in dark mode
- **THEN** `document.documentElement.dataset.theme` is set to `dark`

#### Scenario: Explicit theme overrides system
- **WHEN** user selects `dark` in settings regardless of OS preference
- **THEN** `document.documentElement.dataset.theme` is set to `dark`

#### Scenario: OS preference change updates theme in real time
- **WHEN** user has `system` theme selected and changes OS theme
- **THEN** the UI switches themes without a page reload

### Requirement: Theme toggle control
The UI SHALL expose a theme control that allows the user to switch among light, dark, and system modes, and the selection SHALL persist via the existing settings flow.

#### Scenario: Toggle changes theme mode
- **WHEN** the user changes the current theme mode from the UI
- **THEN** the selected theme becomes active and is persisted

### Requirement: No flash of wrong theme on initial load
The system SHALL set `data-theme` on `<html>` before React renders by reading from localStorage in `index.html` or a synchronous script block.

#### Scenario: Returning visit with saved preference
- **WHEN** user previously selected dark mode and returns
- **THEN** dark mode renders immediately without a light-mode flash
