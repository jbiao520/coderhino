## Purpose

TBD

## Requirements

### Requirement: Tool definitions are serialized into the Agent API tools array
`AgentModelClient.buildPayload()` SHALL include a `tools` key in the JSON payload when the `QueryRequest` contains non-empty tool definitions. Each tool SHALL be serialized as `{ "name": <string>, "description": <string>, "input_schema": <object> }`.

#### Scenario: Request with tools generates tools array in payload
- **WHEN** `buildPayload()` is called with a QueryRequest containing 3 tool definitions with names ["bash", "read_file", "write_file"]
- **THEN** the resulting payload SHALL contain a `tools` key mapped to a list of 3 entries, each with `name`, `description`, and `input_schema` keys

#### Scenario: Request with empty tools omits tools key from payload
- **WHEN** `buildPayload()` is called with a QueryRequest containing an empty tools list
- **THEN** the resulting payload SHALL NOT contain a `tools` key

#### Scenario: Request with null tools omits tools key from payload
- **WHEN** `buildPayload()` is called with a QueryRequest where tools is null
- **THEN** the resulting payload SHALL NOT contain a `tools` key

### Requirement: ToolSchema record represents a serializable tool definition
The system SHALL provide a `ToolSchema` record in `com.coderhino.query` with fields `name` (String), `description` (String), and `inputSchema` (Map<String, Object>). This record SHALL be immutable.

#### Scenario: ToolSchema construction from ToolDefinition
- **WHEN** a ToolSchema is created from a ToolDefinition's name, description, and inputSchema
- **THEN** the ToolSchema SHALL expose those values and be usable by AgentModelClient for payload serialization

### Requirement: ToolRegistry provides tool schemas for API requests
`ToolRegistry` SHALL provide a method that converts all enabled tool definitions into a list of `ToolSchema` records suitable for inclusion in an API request.

#### Scenario: All enabled tools are converted to schemas
- **WHEN** the conversion method is called on a ToolRegistry with 5 tools where 4 are enabled and 1 is disabled
- **THEN** the result SHALL contain exactly 4 ToolSchema entries

#### Scenario: Tool schemas preserve definition order
- **WHEN** the conversion method is called
- **THEN** the resulting list SHALL preserve the registration order of tools in the registry

### Requirement: Tool definitions are preserved across orchestrator loop iterations
`ToolLoopOrchestrator.withMessages()` SHALL copy the tools field from the previous request into each new QueryRequest created during the tool loop.

#### Scenario: Tools available in second iteration after tool result
- **WHEN** the first iteration produces a ToolRequest, the tool is executed, and a second QueryRequest is built for the next iteration
- **THEN** the second QueryRequest SHALL contain the same tools list as the first QueryRequest

### Requirement: QueryEngine flows tool definitions into QueryRequest
`QueryEngine.execute()` SHALL extract tool schemas from its ToolRegistry and include them in the QueryRequest passed to the orchestrator.

#### Scenario: QueryRequest includes registry tools
- **WHEN** `QueryEngine.execute()` is called with a BootstrapState and user input
- **THEN** the resulting QueryRequest passed to `ToolLoopOrchestrator.run()` SHALL contain tool schemas derived from the QueryEngine's ToolRegistry

### Requirement: Embedded runtime publishes schemas for caller-selected tools
The embeddable runtime SHALL derive model tool schemas from the caller-selected tool registry, including enabled built-in tools, dynamic MCP tools when configured, and custom host tools.

#### Scenario: Filtered registry limits schema publication
- **WHEN** an embedded runtime is configured with a filtered tool registry
- **THEN** model requests SHALL include schemas only for enabled tools in that filtered registry plus explicitly configured dynamic tools

#### Scenario: Custom host tool schema is included
- **WHEN** an embedded runtime is configured with a custom host tool definition
- **THEN** the model tool schema list SHALL include that custom tool's name, description, and input schema

### Requirement: Embedded runtime executes only configured tools
The embeddable runtime SHALL reject or report model tool calls that are not present in the configured tool registry or explicitly configured dynamic tool providers.

#### Scenario: Model requests unconfigured tool
- **WHEN** the model requests a tool that the embedded runtime did not configure
- **THEN** the runtime SHALL return an unknown-tool error result through the existing tool-result/error semantics instead of executing an unintended tool

#### Scenario: Model requests configured tool
- **WHEN** the model requests a tool present in the configured registry and permissions allow execution
- **THEN** the runtime SHALL execute that tool using the configured tool context and return its result to the model loop
