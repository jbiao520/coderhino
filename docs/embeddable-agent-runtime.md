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

By default, `CoderhinoAgent` uses embedded-safe services and hardened built-in tools limited to `read_file`, `glob`, and `grep`. These default filesystem tools are confined to the configured `cwd`: relative paths are resolved under that workspace, and absolute paths or parent traversal outside the workspace are rejected.

Hosts must explicitly provide a broader `ToolRegistry`, call `enabledBuiltInTools(...)`, or provide a custom service registry to enable network tools, mutating tools, local integrations, plugins, daemons, voice, or other side-effectful behavior. Passing `enabledBuiltInTools(List.of())` is an explicit empty selection and publishes no built-in tools.

## Provider And Credentials

The first SDK release supports the Anthropic-compatible message request format used by `ProviderApiType.CLAUDE_CODE`. `ProviderApiType.OPENAI` is rejected with a clear error because OpenAI-compatible request payloads are not implemented in this release.

Default production model-client creation requires credentials. Set `ANTHROPIC_API_KEY`, pass credentials through the builder, or inject a host-owned `ModelClient`. Local echo or fake model behavior is only available by explicitly injecting a `ModelClient`; it is not used silently when credentials are missing.

`contextWindow` and `maxOutputTokens` are separate settings. Context window is retained as model metadata for validation/accounting, while `maxOutputTokens` controls the provider output-token field such as Anthropic `max_tokens`. The default `maxOutputTokens` is `128000`.

```java
CoderhinoAgent agent = CoderhinoAgent.builder()
    .apiKey(System.getenv("ANTHROPIC_API_KEY"))
    .apiBaseUrl("https://api.anthropic.com")
    .contextWindow(128000)
    .maxOutputTokens(4096)
    .build();
```

## Runtime Results And Events

`CoderhinoAgent.AgentResult` exposes `stopReason`, `isSuccess()`, and `isError()` so hosts can distinguish successful assistant output from runtime/provider failure. Model/provider failures are returned with `ERROR` stop reason and are not persisted as normal assistant turns.

`QueryEventSink` callbacks are terminal-outcome specific. Successful runs call `onCompleted` once for the final assistant text. Model/provider failures call `onError` with diagnostic text and do not call a misleading successful `onCompleted`.

## State Isolation

The agent's managed `BootstrapState` is suitable for simple single-session usage. Multi-session or concurrent hosts should pass a request-specific `BootstrapState` in `AgentRequest`, or create one `CoderhinoAgent` per session. Request-specific state is updated for that run and does not mutate the agent's managed default state.

## Custom Tools

Host tools implement `ToolDefinition<I, O>`. For typed JSON input materialization, define a nested record named `Input` on the tool class and use matching JSON argument names:

```java
final class EchoTool implements ToolDefinition<EchoTool.Input, String> {
    record Input(String value) {}
    public String name() { return "host_echo"; }
    public String description() { return "Echo host input"; }
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of("value", Map.of("type", "string")));
    }
    public String execute(Input input, ToolContext context) { return input.value(); }
}
```

Unsupported or mismatched input shapes fail as clear invalid-tool-input errors instead of ambiguous reflection failures.

## Optional Spring Integration

`coderhino-agent-spring` provides lightweight Spring configuration and property binding for host applications that want an injectable `CoderhinoAgent` bean. It is intentionally separate from `coderhino-agent-runtime` so the plain Java runtime remains Spring-free.

Example properties:

```properties
coderhino.agent.model=sonnet
coderhino.agent.cwd=/path/to/project
coderhino.agent.permission-mode=DEFAULT
coderhino.agent.enabled-tools=read_file,grep
coderhino.agent.max-tool-iterations=50
coderhino.agent.max-output-tokens=4096
coderhino.agent.max-budget-usd=1.00
```

Host applications can override `ModelClient`, `ToolRegistry`, `ServiceRegistry`, `PermissionChecker`, or `QueryEventSink` by declaring their own beans.

The Spring module is optional and is not part of the first plain Java runtime publication scope. When used, it mirrors the hardened embedded defaults and backs off for host-provided beans.

## Publication Checks

The first-release library modules attach source and javadoc jars during `verify` and `install`:

```bash
mvn -pl coderhino-runtime-api,coderhino-services,coderhino-tools,coderhino-agent-runtime -am verify
```

The runtime dependency tree should exclude application modules:

```bash
mvn -pl coderhino-agent-runtime -am dependency:tree -Dscope=runtime
```

Local consumption can be verified by building the external-consumer verification module through the root reactor:

```bash
mvn -pl coderhino-external-consumer -am test
```

The external consumer depends only on `com.coderhino:coderhino-agent-runtime` and creates a `CoderhinoAgent` with an injected `ModelClient`, so it does not require API credentials during verification.
