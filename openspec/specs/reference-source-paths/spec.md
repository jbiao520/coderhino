# reference-source-paths Specification

## Purpose
TBD - created by archiving change add-reference-path-settings. Update Purpose after archive.

## Requirements
### Requirement: Users can configure multiple reference source paths
The system SHALL allow users to persist multiple filesystem directory paths as reference sources, and SHALL load readable markdown files from those directories into the reference browser in addition to bundled references.

#### Scenario: Settings save multiple reference source paths
- **WHEN** the user saves settings with more than one reference source path
- **THEN** the settings API persists the ordered list of configured paths as part of web settings

#### Scenario: Reference API includes markdown files from configured paths
- **WHEN** one or more configured reference source paths contain readable `.md` files
- **THEN** the references API returns those markdown files alongside bundled references

### Requirement: Invalid reference source paths do not break reference loading
The system SHALL ignore configured reference source paths that are missing, unreadable, or contain no readable markdown files, and SHALL continue returning any remaining valid references.

#### Scenario: One configured path is invalid
- **WHEN** the user has configured multiple reference source paths and one path no longer exists
- **THEN** the references API still succeeds and returns references from bundled assets and any remaining valid configured paths

#### Scenario: Configured path has no markdown files
- **WHEN** the user configures a path that contains no readable `.md` files
- **THEN** the references API excludes that path from the result set without treating the request as an error

### Requirement: Reference results expose filenames for popup display
The system SHALL expose each reference file's filename in the references response so the client can show the filename directly in the browser list and preview popup.

#### Scenario: Browser shows configured markdown filename
- **WHEN** the references API returns a markdown file from a configured source path
- **THEN** the response includes the original filename with extension for that reference

#### Scenario: Browser shows bundled markdown filename
- **WHEN** the references API returns a bundled markdown asset
- **THEN** the response includes that asset's filename with extension for that reference
