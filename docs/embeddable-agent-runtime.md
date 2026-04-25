# Embeddable Agent Runtime

Coderhino's first public library set is intended for host applications that want to embed the agent runtime without depending on the runnable CLI or web applications.

## First-Release Artifacts

The first external publication scope is limited to these library jars:

- `com.coderhino:coderhino-runtime-api` - shared runtime contracts, state, config records, permissions, and tool service contracts.
- `com.coderhino:coderhino-services` - embeddable service implementations and safe/no-op service defaults.
- `com.coderhino:coderhino-tools` - built-in tool contracts and tool registry support.
- `com.coderhino:coderhino-agent-runtime` - query loop, model client contracts, orchestration, and the `CoderhinoAgent` facade.

`coderhino-commands`, `coderhino-agent-spring`, `coderhino-backend`, `coderhino-web`, and `frontend` are out of first publication scope. They may be useful inside the repository, but embedders should not need them for the plain Java runtime path.

## Plain Java Usage

```java
CoderhinoAgent agent = CoderhinoAgent.builder()
    .modelClient(modelClient)
    .cwd(projectPath)
    .enabledBuiltInTools(List.of("read_file", "grep"))
    .build();

CoderhinoAgent.AgentResult result = agent.run("Summarize this project");
```

By default, `CoderhinoAgent` uses embedded-safe services and read-only built-in tools. Hosts must explicitly provide a broader `ToolRegistry` or service registry to enable mutating tools, local integrations, plugins, daemons, voice, or other side-effectful behavior.

## Optional Spring Integration

`coderhino-agent-spring` provides lightweight Spring configuration and property binding for host applications that want an injectable `CoderhinoAgent` bean. It is intentionally separate from `coderhino-agent-runtime` so the plain Java runtime remains Spring-free.

Example properties:

```properties
coderhino.agent.model=sonnet
coderhino.agent.cwd=/path/to/project
coderhino.agent.permission-mode=DEFAULT
coderhino.agent.enabled-tools=read_file,grep
coderhino.agent.max-tool-iterations=50
coderhino.agent.max-budget-usd=1.00
```

Host applications can override `ModelClient`, `ToolRegistry`, `ServiceRegistry`, `PermissionChecker`, or `QueryEventSink` by declaring their own beans.

## Publication Checks

The first-release library modules attach source and javadoc jars during `verify` and `install`:

```bash
mvn -pl coderhino-runtime-api,coderhino-services,coderhino-tools,coderhino-agent-runtime -am verify
```

Local consumption can be verified by installing the runtime module and compiling a minimal external Maven project that depends only on `coderhino-agent-runtime`.
