## Purpose
Support the frontend as a dedicated Maven module while preserving the standard root build and backend packaging workflow.

## Requirements

### Requirement: Root Maven build defines a multi-module reactor
The project SHALL provide a root Maven parent that defines the repository as a multi-module build and SHALL include both the application module and the frontend module in the default reactor.

#### Scenario: Building from the repository root
- **WHEN** a developer runs `mvn package` from the repository root
- **THEN** Maven SHALL execute a reactor build that includes the frontend module and the application module

#### Scenario: Shared build configuration remains centralized
- **WHEN** child modules are built through the root reactor
- **THEN** common coordinates, versions, and shared build properties SHALL be inherited from the root parent POM

### Requirement: Frontend source is owned by a dedicated Maven module
The frontend codebase SHALL live in its own Maven module rather than under the backend source tree, and that module SHALL own the Node/npm/Vite build lifecycle for the web application assets.

#### Scenario: Frontend module builds web assets
- **WHEN** the frontend module participates in a Maven build
- **THEN** it SHALL install the configured Node toolchain, run npm installation, and build the frontend assets from the module's own source directory

#### Scenario: Frontend module preserves existing npm workflow
- **WHEN** a developer works inside the frontend module
- **THEN** the existing npm scripts for development, test, lint, and production build SHALL remain available from the relocated frontend workspace

### Requirement: Application packaging consumes frontend module output
The application module SHALL package the built frontend assets produced by the frontend module into the final runtime artifact so the web server continues to serve the frontend from the classpath.

#### Scenario: Package build includes static assets
- **WHEN** the application module is packaged after the frontend module has built its assets
- **THEN** the final application artifact SHALL contain the frontend build output under the same static classpath location used by the web server today

#### Scenario: Standard root build remains sufficient
- **WHEN** a developer runs the standard root Maven build without separately invoking frontend commands
- **THEN** the build SHALL still produce an application artifact that includes current frontend assets
