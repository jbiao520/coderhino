# Coderhino External Consumer Spring Verification

This module is a small verification sample for consuming `coderhino-agent-spring` from a consumer-shaped Spring Boot application that now exposes a synchronous JSON chat endpoint.

Use `AI_USAGE.md` for the full guide, checked-in source map, and recipe details. This README covers the quick verification split, the checked-in `application.properties` entry point, and the exact credential order for the default Spring auto-configured `ModelClient`.

## Baseline verification, local and credential-free

Normal module tests use fake or local behavior. They do not require live provider credentials and should be the default verification path from the repository root.

```bash
env -u CODERHINO_AGENT_API_KEY -u ANTHROPIC_API_KEY -u OPENAI_API_KEY mvn -pl coderhino-external-consumer-spring -am test
```

## Optional live manual run

The runnable sample starts a web-capable Spring Boot context and exposes `POST http://localhost:8080/chat`. The endpoint is synchronous JSON, stateless per request, and non-streaming. Treat that path as opt-in only.

`src/main/resources/application.properties` is the primary checked-in configuration example. Keep placeholders in source control. For real deployments, prefer environment variables, a secret manager behind `CoderhinoAgentCredentialProvider`, or a custom `ModelClient`.

```properties
coderhino.agent.provider-api-type=OPENAI
coderhino.agent.model=gpt-4.1-mini
coderhino.agent.context-window=65536
coderhino.agent.max-output-tokens=4096
coderhino.agent.api-base-url=https://api.openai.com/v1
coderhino.agent.api-key=replace-with-your-api-key
```

`coderhino.agent.api-key` may be either the real key value or a path to a local file that contains the key. When the configured value points to an existing regular file, the Spring auto-configuration reads that file and uses its trimmed contents as the key.

Default credential resolution for the Spring auto-configured `ModelClient` is:

1. host-provided `ModelClient` bean, full override path
2. `CoderhinoAgentCredentialProvider` bean
3. `coderhino.agent.api-key` or direct `CODERHINO_AGENT_API_KEY`
4. `ANTHROPIC_API_KEY`
5. fail fast with configuration guidance

If `coderhino.agent.api-base-url` is omitted, the default base URL follows `coderhino.agent.provider-api-type`: `OPENAI` uses `https://api.openai.com`, `CLAUDE_CODE` uses `https://api.anthropic.com`.

This is the idiomatic Spring provider-bean pattern when your host app fetches credentials from another service:

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

Run from the repository root with `CODERHINO_AGENT_API_KEY` set, `coderhino.agent.api-key` configured, or the sample credential provider in place. The current default Spring auto-config path also accepts `ANTHROPIC_API_KEY` as a fallback. Bare `OPENAI_API_KEY` is not part of this default credential order.

```bash
mvn -pl coderhino-external-consumer-spring exec:java
```

Then call the chat sample with the exact endpoint below:

```bash
curl -s -X POST http://localhost:8080/chat -H 'Content-Type: application/json' -d '{"message":"Hello from the Spring sample"}'
```

Representative successful response:

```json
{
  "finalText": "Hello from the Spring sample.",
  "stopReason": "END_TURN",
  "iterationCount": 1,
  "success": true
}
```

Representative invalid request response for a missing or blank `message`:

```json
{
  "error": "invalid_request",
  "message": "message is required"
}
```

This module is verification/sample code only and is not part of the first-release public runtime library set.

The `/chat` route delegates to `CoderhinoAgent` through `ChatAgentRunner` and the default `CoderhinoChatAgentRunner`. That runner builds a request-owned `BootstrapState` for each call, so there is no session reuse across requests.

## Chat sample wiring notes

The runnable Spring sample now imports chat-specific host wiring:

- `ExternalConsumerSpringApplication` imports `ChatAgentConfiguration` and `HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration` for this chat sample.
- `HardcodedCredentialProviderConfiguration` supplies `sk-coderhino-example-placeholder-not-a-real-secret` through a `CoderhinoAgentCredentialProvider`. This is sample-only and must not be treated as production credential guidance.
- `ChatAgentConfiguration` intentionally wires the full verification surface through `ToolRegistry.createDefault()`, `CommandRegistry.createDefault(Path.of("").toAbsolutePath().normalize())`, and `commandRegistry.asToolCommandRegistry()`.

Safety note: `ToolRegistry.createDefault()` exposes broad write, bash, and network-capable tools. That is useful for this verification sample, but it is not production-safe by default. The default Spring auto-config still uses embedded-safe tools unless a host app overrides that behavior.
