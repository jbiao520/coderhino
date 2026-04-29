# AI Usage Guide

This file is for AI agents and host developers consuming `coderhino-agent-spring` through the checked-in Spring verification module.

Use this guide when you need one of these things:

- a real Spring Boot `CoderhinoAgent` bean
- a synchronous `POST /chat` sample that wraps `CoderhinoAgent`
- property binding for the public Spring surface
- a credential-free local test path with a fake `ModelClient`
- a host-owned tool, event sink, or request-specific session state

Don't use this guide as a map for internal CLI or web app code. Stay inside the public Spring consumer surface shown here.

## Quick decision tree

1. Need a Spring bean you can inject and call?
   - Use `CoderhinoAgent` auto-config plus `RunApiExample`.
   - Start with `src/main/java/com/coderhino/verification/examples/spring/RunApiExample.java`.
   - Proof test: `src/test/java/com/coderhino/verification/examples/spring/RunApiExampleTest.java`.
2. Need to know which `coderhino.agent.*` properties are real?
   - Use `AgentPropertiesExample` and `CoderhinoAgentProperties`.
   - Start with `src/main/java/com/coderhino/verification/examples/spring/AgentPropertiesExample.java`.
   - Proof test: `src/test/java/com/coderhino/verification/examples/spring/AgentPropertiesExampleTest.java`.
3. Need local, credential-free verification?
   - Inject a fake `ModelClient` bean.
   - Start with `src/main/java/com/coderhino/verification/examples/spring/FakeModelClientConfiguration.java`.
   - Proof tests: `src/test/java/com/coderhino/verification/examples/spring/RunApiExampleTest.java`, `ObservedCoderhinoRunTest.java`, `SessionIsolationExampleTest.java`.
4. Need a host-owned tool without opening the full built-in tool set?
    - Override `ToolRegistry` and add your tool to `ToolRegistry.createEmbeddedDefault()`.
    - Start with `src/main/java/com/coderhino/verification/examples/spring/HostEchoTool.java` and `ToolingConfigurationExample.java`.
    - Proof test: `src/test/java/com/coderhino/verification/examples/spring/ToolingConfigurationExampleTest.java`.
5. Need callback observation for text, usage, or errors?
    - Provide a `QueryEventSink` bean or a request-level sink.
    - Start with `src/main/java/com/coderhino/verification/examples/spring/ObservedCoderhinoRun.java`.
    - Proof test: `src/test/java/com/coderhino/verification/examples/spring/ObservedCoderhinoRunTest.java`.
6. Need one run to keep its own session state?
     - Pass a request-owned `BootstrapState`.
     - Start with `src/main/java/com/coderhino/verification/examples/spring/SessionIsolationExample.java`.
     - Proof test: `src/test/java/com/coderhino/verification/examples/spring/SessionIsolationExampleTest.java`.
7. Need a real provider call?
   - Treat it as opt-in only.
   - Start with `src/main/java/com/coderhino/verification/examples/spring/LiveProviderRunner.java`.
   - Proof test: `src/test/java/com/coderhino/verification/examples/spring/LiveProviderRunnerTest.java`.

## What is public enough to use, and what to avoid

| Surface | Use it? | Notes |
| --- | --- | --- |
| `CoderhinoAgent` bean from `coderhino-agent-spring` | Yes | Main host entry point. Auto-configured by `CoderhinoAgentAutoConfiguration`. |
| `coderhino.agent.*` properties in `CoderhinoAgentProperties` | Yes | Public Spring binding surface. Property matrix below matches the current source. |
| Host-provided `ModelClient` bean | Yes | Best way to keep tests local and credential-free. |
| Host-provided `ToolRegistry` bean | Yes | Use when you need host tools or tighter tool control than property filtering. |
| Host-provided `ServiceRegistry` bean | Yes | Extension point when embedded-safe defaults are not enough. |
| Host-provided `PermissionChecker` bean | Yes | Supported auto-config override point. |
| Host-provided `QueryEventSink` bean | Yes | Applies to the configured agent for normal runs. |
| Host-provided `ToolCommandRegistry` bean | Yes | Passed into the auto-configured `CoderhinoAgent`; required for model-visible prompt commands through `SkillTool`. |
| Request-level `QueryEventSink` in `AgentRequest` | Yes | Overrides the configured sink for a single run. |
| Request-level `BootstrapState` in `AgentRequest` | Yes | Use for session isolation or per-run state ownership. |
| `enabled-tools` empty list | Yes | Safe default. Gives `read_file`, `glob`, `grep`. |
| `enabled-tools` non-empty list | Yes, with care | Filters `ToolRegistry.createDefault()`, not the embedded-safe subset. You can expose broader built-ins if you list them. |
| `embeddedIntegrationsEnabled` | No, not today | Bindable and present on properties, but current auto-config does not read it. |
| `coderhino-backend`, `coderhino-web`, frontend, CLI internals | No | Out of scope for this Spring consumer guide. |
| Generated outputs or build artifacts | No | Never cite them. Use checked-in source paths only. |
| Assuming live providers are part of default tests | No | Default verification is local and fake. Live usage is separate opt-in behavior. |

## Chat sample, run path, and safety semantics

The runnable sample in this module is web-capable now. `ExternalConsumerSpringApplication` imports `ChatAgentConfiguration` and `HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration`, so starting the app gives you a synchronous JSON `POST http://localhost:8080/chat` endpoint.

Run it from the repository root:

```bash
mvn -pl coderhino-external-consumer-spring exec:java
```

Call it with the exact sample request:

```bash
curl -s -X POST http://localhost:8080/chat -H 'Content-Type: application/json' -d '{"message":"Hello from the Spring sample"}'
```

Request JSON:

```json
{
  "message": "Hello from the Spring sample"
}
```

Representative success response JSON:

```json
{
  "finalText": "Hello from the Spring sample.",
  "stopReason": "END_TURN",
  "iterationCount": 1,
  "success": true
}
```

Representative invalid request response JSON:

```json
{
  "error": "invalid_request",
  "message": "message is required"
}
```

Behavior notes:

- `/chat` is synchronous JSON, stateless per request, and non-streaming.
- `ChatController` delegates to `ChatAgentRunner`, and the default implementation is `CoderhinoChatAgentRunner`.
- `CoderhinoChatAgentRunner` calls `CoderhinoAgent` with a request-owned `BootstrapState`, so each request gets fresh state instead of reusing a shared conversation session.
- This sample imports `HardcodedCredentialProviderConfiguration`, which returns the placeholder key from `HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration`. That setup is sample-only and must not be treated as production credential guidance.
- `ChatAgentConfiguration` intentionally wires `ToolRegistry.createDefault()`, `CommandRegistry.createDefault(Path.of("").toAbsolutePath().normalize())`, and `commandRegistry.asToolCommandRegistry()` so the sample can exercise the full verification surface.
- `ToolRegistry.createDefault()` exposes broad write, bash, and network-capable tools. That is not production-safe by default. This warning applies to this sample override, not to the default Spring auto-config path.

## Safe defaults you get if you do nothing extra

- `ServiceRegistry.createEmbeddedDefault(properties.getCwd())`
- `ToolRegistry.createEmbeddedDefault()` when `coderhino.agent.enabled-tools` is empty
- default safe built-ins: `read_file`, `glob`, `grep`
- `PermissionMode.DEFAULT`
- `maxToolIterations=200`
- `maxBudgetUsd=0.0`
- `apiBaseUrl` follows `providerApiType` when `coderhino.agent.api-base-url` is unset
- `providerApiType=CLAUDE_CODE`
- `CLAUDE_CODE` default base URL is `https://api.anthropic.com`
- `OPENAI` default base URL is `https://api.openai.com`
- `contextWindow=128000`
- `maxOutputTokens=128000`

Source of truth:

- `coderhino-agent-spring/src/main/java/com/coderhino/agent/spring/CoderhinoAgentProperties.java`
- `coderhino-agent-spring/src/main/java/com/coderhino/agent/spring/CoderhinoAgentAutoConfiguration.java`

## Recipe index with exact paths

### 1. Property binding and agent config

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/AgentPropertiesExample.java`
- Public truth source: `coderhino-agent-spring/src/main/java/com/coderhino/agent/spring/CoderhinoAgentProperties.java`
- Auto-config wiring source: `coderhino-agent-spring/src/main/java/com/coderhino/agent/spring/CoderhinoAgentAutoConfiguration.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/AgentPropertiesExampleTest.java`
- Use this when: you need the current property list, defaults, and which ones are actually wired into beans.

Make `src/main/resources/application.properties` your main checked-in entry point, with placeholders only:

```properties
coderhino.agent.provider-api-type=OPENAI
coderhino.agent.model=gpt-4.1-mini
coderhino.agent.context-window=65536
coderhino.agent.max-output-tokens=4096
coderhino.agent.api-base-url=https://api.openai.com/v1
coderhino.agent.api-key=replace-with-your-api-key
```

Keep real credentials out of source-controlled files. For production, prefer `CODERHINO_AGENT_API_KEY`, a secret manager behind `CoderhinoAgentCredentialProvider`, or a host-provided `ModelClient`.

### 2. Fake model client for local tests

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/FakeModelClientConfiguration.java`
- Related proving tests:
  - `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/RunApiExampleTest.java`
  - `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/ObservedCoderhinoRunTest.java`
  - `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/SessionIsolationExampleTest.java`
- Use this when: you need deterministic agent runs without `coderhino.agent.api-key`, `CODERHINO_AGENT_API_KEY`, or `ANTHROPIC_API_KEY`.

### 3. Basic run API

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/RunApiExample.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/RunApiExampleTest.java`
- Use this when: you need `agent.run(String)` or `agent.run(AgentRequest)` and want the host-visible input split shown clearly.

### 4. Host tool plus tool registry override

- Tool class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/HostEchoTool.java`
- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/ToolingConfigurationExample.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/ToolingConfigurationExampleTest.java`
- Use this when: you need to keep the safe embedded tools and add a host-owned tool.

### 5. Chat sample credential provider and full-registry wiring

- Credential provider: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/HardcodedCredentialProviderConfiguration.java`
  - `HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration` is imported by the runnable chat sample. It is sample-only, returns a placeholder key, and should not be copied as production credential guidance.
- Chat wiring source: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/spring/chat/ChatAgentConfiguration.java`
- Runnable app import site: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/spring/ExternalConsumerSpringApplication.java`
- Proving tests:
  - `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/spring/chat/ChatAgentWiringTest.java`
  - `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/spring/chat/ChatControllerTest.java`
- Use this when: you need the runnable `/chat` sample to expose the full verification surface while keeping placeholder credential guidance explicit.

### 6. Event sink observation

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/ObservedCoderhinoRun.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/ObservedCoderhinoRunTest.java`
- Use this when: you need callback capture for success, usage, text chunks, or error-only completion behavior.

### 7. Request-level session isolation

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/SessionIsolationExample.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/SessionIsolationExampleTest.java`
- Use this when: each run needs its own `BootstrapState` without mutating the agent's managed default state.

### 8. Live provider opt-in gate

- Recipe class: `coderhino-external-consumer-spring/src/main/java/com/coderhino/verification/examples/spring/LiveProviderRunner.java`
- Proving test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/LiveProviderRunnerTest.java`
- Use this when: you want an explicit live-run gate that stays skipped unless credentials are present.

### Shared test helpers worth reusing as references

- `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/DeterministicFakeModelClient.java`
- `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/RecordingQueryEventSink.java`
- `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/ExampleHarnessTest.java`

Use these only as test harness references. They are checked in and proven, but they are not the public Spring auto-config surface.

## Property matrix

This matches `CoderhinoAgentProperties` plus current auto-config wiring.

| Property | Type | Default | Wired today? | Behavior |
| --- | --- | --- | --- | --- |
| `coderhino.agent.model` | `String` | `MiniMax-M2.7` | Yes | Sets the auto-configured agent model and the default model client inputs. |
| `coderhino.agent.cwd` | `Path` | current working directory | Yes | Sets embedded service cwd and agent cwd. |
| `coderhino.agent.permission-mode` | `PermissionMode` | `DEFAULT` | Yes | Sets the auto-configured agent permission mode. |
| `coderhino.agent.enabled-tools` | `List<String>` | `[]` | Yes | Empty uses embedded-safe tools. Non-empty filters the full default registry. |
| `coderhino.agent.custom-system-prompt` | `String` | `null` | Yes | Sets `customSystemPrompt` on the agent. |
| `coderhino.agent.append-system-prompt` | `String` | `null` | Yes | Sets `appendSystemPrompt` on the agent. |
| `coderhino.agent.max-tool-iterations` | `int` | `200` | Yes | Sets the agent iteration limit. |
| `coderhino.agent.max-budget-usd` | `double` | `0.0` | Yes | Sets the agent budget limit. |
| `coderhino.agent.embedded-integrations-enabled` | `boolean` | `false` | Present, not wired | Bindable, but current auto-config does not consume it. |
| `coderhino.agent.api-key` | `String` | `null` | Yes | Used by the default `ModelClient` after any `CoderhinoAgentCredentialProvider` bean and before direct env fallbacks. |
| `coderhino.agent.api-base-url` | `String` | provider default when omitted | Yes | Passed to the default `ModelClient`. Explicit values win. Omitted values follow `provider-api-type`. |
| `coderhino.agent.provider-api-type` | `ProviderApiType` | `CLAUDE_CODE` | Yes | Passed to the default `ModelClientFactory` and used to select the default base URL when `api-base-url` is omitted. |
| `coderhino.agent.context-window` | `long` | `128000` | Yes | Passed to the default `ModelClientFactory`. |
| `coderhino.agent.max-output-tokens` | `long` | `128000` | Yes | Passed to both the default `ModelClientFactory` and agent config. |

## Extension points that are actually supported

These are the auto-config override points in `CoderhinoAgentAutoConfiguration`.

- `ModelClient`
- `ToolRegistry`
- `ToolCommandRegistry`
- `ServiceRegistry`
- `PermissionChecker`
- `QueryEventSink`

If you declare your own bean for one of those, Spring backs off from creating the default bean.

## Minimal snippets

All snippets below are either direct copies of checked-in patterns or clearly marked conceptual.

### Credential-free local test path, tested

This is a checked-in pattern adapted from the main-source `FakeModelClientConfiguration` recipe.

```java
@Configuration(proxyBeanMethods = false)
class FakeModelClientConfig {
    @Bean
    ModelClient deterministicSpringModelClient() {
        return (bootstrapState, request) ->
            new ModelResponse.AssistantReply(
                "spring fake model reply",
                new ModelResponse.Usage(7, 3, 0, 0)
            );
    }
}
```

Why use it: a custom `ModelClient` bean avoids credential requirements and keeps tests local.

### Basic agent run, tested

This follows `RunApiExample`.

```java
CoderhinoAgent.AgentResult result = agent.run(
    new CoderhinoAgent.AgentRequest(
        "Describe the host-owned agent request flow.",
        "Visible host request for deterministic Spring verification.",
        null,
        null
    )
);
```

What it shows:

- raw input goes to the model-facing request
- `visibleInput` is the host-facing value persisted in state

### Host tool registry override, tested

This follows `ToolingConfigurationExample`.

```java
@Configuration(proxyBeanMethods = false)
class HostToolRegistryOverrideConfiguration {
    @Bean
    HostEchoTool hostEchoTool() {
        return new HostEchoTool();
    }

    @Bean
    ToolRegistry coderhinoToolRegistry(HostEchoTool hostEchoTool) {
        return ToolRegistry.createEmbeddedDefault().with(hostEchoTool);
    }
}
```

Why use it: you keep `read_file`, `glob`, and `grep`, then add your own tool. You do not need to open the broader built-in registry just to add one host tool.

### Request-level sink override, tested

This follows `ObservedCoderhinoRun`.

```java
QueryEventSink requestSink = ObservedCoderhinoRun.newRecorder();
CoderhinoAgent.AgentRequest request = new CoderhinoAgent.AgentRequest(
    "Describe the request-level QueryEventSink override flow.",
    "Observe the request-level Spring sink override.",
    requestSink,
    null
);
CoderhinoAgent.AgentResult result = agent.run(request);
```

### Request-level session state, tested

This follows `SessionIsolationExample`.

```java
BootstrapState requestState = SessionIsolationExample.newSessionState(agent);
CoderhinoAgent.AgentRequest request = new CoderhinoAgent.AgentRequest(
    "Describe why this run uses request-owned BootstrapState.",
    "Session-isolated Spring request visible to the host.",
    null,
    requestState
);
CoderhinoAgent.AgentResult result = agent.run(request);
```

### Manual live provider path, conceptual wrapper around checked-in gate

This is grounded in `LiveProviderRunner`, but the exact supplier body is host-owned.

```java
var outcome = LiveProviderRunner.runWhenReady(
    com.coderhino.query.ProviderApiType.CLAUDE_CODE,
    () -> agent.run(LiveProviderRunner.LIVE_INPUT)
);
```

This is opt-in only. Do not treat it as part of default local verification.

### Credential provider bean, conceptual and aligned with current auto-config

Use this when another service owns the real credential:

```java
interface ExternalSecretService {
    String lookupCoderhinoApiKey();
}

@Configuration(proxyBeanMethods = false)
class CredentialProviderConfiguration {
    @Bean
    CoderhinoAgentCredentialProvider coderhinoAgentCredentialProvider(ExternalSecretService externalSecretService) {
        return externalSecretService::lookupCoderhinoApiKey;
    }
}
```

That bean is checked before `coderhino.agent.api-key`, `CODERHINO_AGENT_API_KEY`, and `ANTHROPIC_API_KEY`. If you instead provide your own `ModelClient` bean, Spring backs off from the default `ModelClient` path entirely.

## Credential rules and common failure guidance

### Baseline local verification, no credentials

Use this from the repository root with live-provider env vars unset:

```bash
env -u CODERHINO_AGENT_API_KEY -u ANTHROPIC_API_KEY -u OPENAI_API_KEY mvn -pl coderhino-external-consumer-spring -am test
```

Why this stays local:

- recipe tests inject a fake `ModelClient`
- `LiveProviderRunnerTest` proves the live path skips cleanly without credentials
- no default test should depend on a real provider

### Live usage, opt-in only

Supported live opt-in env vars in the checked-in examples for the default Spring auto-configured `ModelClient`:

- `CODERHINO_AGENT_API_KEY`
- `ANTHROPIC_API_KEY`

Use one of these supported paths for the default Spring auto-configured `ModelClient`:

| Order | Source | Notes |
| --- | --- | --- |
| 1 | custom `ModelClient` bean | Full override path. Spring does not create the default `ModelClient`. |
| 2 | `CoderhinoAgentCredentialProvider` bean | Best fit when a host secret service owns the credential. |
| 3 | `coderhino.agent.api-key` or direct `CODERHINO_AGENT_API_KEY` | Main property-driven path. `application.properties` is the checked-in example. |
| 4 | `ANTHROPIC_API_KEY` | Fallback used by the current default auto-configured `ModelClient`. |
| 5 | fail | Throws a startup error with configuration guidance. |

Notes that matter:

- `CODERHINO_AGENT_API_KEY` is valid both as Spring-style environment binding for `coderhino.agent.api-key` and as a direct fallback lookup in the current auto-config.
- Bare `OPENAI_API_KEY` is not part of the default Spring auto-config credential order.
- If `coderhino.agent.api-base-url` is absent, the default base URL comes from `coderhino.agent.provider-api-type`.
- Use a custom `ModelClient` if your host needs a different secret source or a different provider contract.

### Common failure message

If you rely on the default auto-configured `ModelClient` and provide no credentials, current auto-config throws this guidance:

```text
Model API credentials are required. Provide a CoderhinoAgentCredentialProvider bean, set coderhino.agent.api-key, CODERHINO_AGENT_API_KEY, ANTHROPIC_API_KEY, or inject a custom ModelClient for local/test behavior.
```

Where that comes from:

- source: `coderhino-agent-spring/src/main/java/com/coderhino/agent/spring/CoderhinoAgentAutoConfiguration.java`
- proof test: `coderhino-external-consumer-spring/src/test/java/com/coderhino/verification/examples/spring/AgentPropertiesExampleTest.java`

## Verification commands

### Required baseline check

```bash
env -u CODERHINO_AGENT_API_KEY -u ANTHROPIC_API_KEY -u OPENAI_API_KEY mvn -pl coderhino-external-consumer-spring -am test
```

### Optional manual packaging

```bash
mvn -pl coderhino-external-consumer-spring -am package
```

### Optional live run, only if you mean to use a real provider

From `coderhino-external-consumer-spring/` after packaging:

```bash
CODERHINO_AGENT_API_KEY=your-key mvn exec:java
```

Do not treat that command as baseline verification. It is credential-gated and opt-in.

## Notes that matter for AI agents

- `ExternalConsumerSpringApplication` contains a `startupProbe(...)` bean that only logs readiness plus `agent.config().modelClient().getClass().getSimpleName()` and does not call `agent.run(...)` at boot. Real provider traffic happens only when `/chat` or another explicit agent-run path invokes the agent while a real default `ModelClient` is configured.
- `AgentRequest.visibleInput` is host-facing and persisted in state. Raw `input` is what the model sees.
- Request-level `QueryEventSink` and `BootstrapState` override the configured defaults for one run.
- `embeddedIntegrationsEnabled` exists for binding and documentation truth, but it is not currently consumed by the Spring auto-config.
- For docs and code generation, prefer the checked-in examples above over inventing a new pattern.
