## Purpose

TBD

## ADDED Requirements

### Requirement: Web module independence from backend

The system SHALL keep `coderhino-web` independent from `coderhino-backend` at compile time. Web runtime code MUST depend directly on reusable library modules it uses instead of depending on backend as an application-module aggregator.

#### Scenario: Web dependency graph excludes backend
- **WHEN** the Maven dependency graph for `coderhino-web` is inspected in the reactor
- **THEN** `coderhino-backend` is not present in the compile dependency tree

#### Scenario: Web compiles with direct dependencies
- **WHEN** `coderhino-web` is compiled with its required upstream modules
- **THEN** compilation succeeds without relying on transitive dependencies from `coderhino-backend`

### Requirement: Application modules consume runtime libraries as siblings

CLI/backend application code and web application code SHALL consume `coderhino-agent-runtime` and supporting library modules as sibling application surfaces. Neither app module MUST require the other app module to access query execution, service registry, tool registry, command registry, state, or shared runtime contracts.

#### Scenario: CLI and web use runtime directly
- **WHEN** module dependencies are reviewed
- **THEN** both CLI/backend and web paths reference `coderhino-agent-runtime` directly for query execution concerns

#### Scenario: Shared runtime libraries remain application-free
- **WHEN** publishable library modules are inspected
- **THEN** `coderhino-runtime-api`, `coderhino-services`, `coderhino-tools`, and `coderhino-agent-runtime` do not depend on `coderhino-backend` or `coderhino-web`

### Requirement: Web launch behavior is preserved

The system SHALL preserve the existing command-line ability to start the web application through the backend CLI server path unless a replacement executable module is introduced in the same change.

#### Scenario: Backend serve mode starts web application
- **WHEN** the backend CLI is launched with `--serve --port <port>`
- **THEN** the Spring Boot web application starts on the requested local port

#### Scenario: Web application remains directly runnable
- **WHEN** the web module's Spring Boot application entry point is launched directly
- **THEN** the web runtime starts with its REST, SSE, websocket, and static frontend behavior available

### Requirement: Dependency declarations reflect direct production usage

Application module POMs SHALL declare direct production dependencies for internal modules they import and SHOULD remove internal app-module dependencies that are only used as transitive aggregators. Dependency cleanup MUST NOT remove dependencies required for packaging, Spring Boot startup, static frontend resource handoff, or reflective launch behavior without replacing that behavior.

#### Scenario: Web declares modules it imports
- **WHEN** web source imports command, tool, service, runtime, or state types
- **THEN** `coderhino-web/pom.xml` declares the corresponding internal module dependency directly

#### Scenario: Redundant app dependency is removed
- **WHEN** web no longer imports backend-owned classes and no packaging behavior requires backend
- **THEN** `coderhino-web/pom.xml` does not declare `coderhino-backend`

### Requirement: Runtime publication boundary remains unchanged

The module-boundary cleanup SHALL NOT expand the first-release public library surface or introduce CLI/web application dependencies into the embeddable runtime path.

#### Scenario: Embeddable runtime remains Spring-free
- **WHEN** `coderhino-agent-runtime` production sources and dependencies are inspected
- **THEN** they do not require Spring Boot, Spring MVC, websocket, actuator, security, picocli, backend, or web application modules

#### Scenario: Public facade remains source-compatible
- **WHEN** existing code uses the `CoderhinoAgent` facade and query contracts
- **THEN** no source changes are required because of the application module-boundary cleanup
