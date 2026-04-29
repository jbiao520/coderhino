# Coderhino External Consumer Spring Verification

This module is a small verification sample for consuming `coderhino-agent-spring` from a consumer-shaped non-web Spring Boot application.

Use `AI_USAGE.md` for the full guide, checked-in source map, and recipe details. This README covers the quick verification split, the checked-in `application.properties` entry point, and the exact credential order for the default Spring auto-configured `ModelClient`.

## Baseline verification, local and credential-free

Normal module tests use fake or local behavior. They do not require live provider credentials and should be the default verification path from the repository root.

```bash
env -u CODERHINO_AGENT_API_KEY -u ANTHROPIC_API_KEY -u OPENAI_API_KEY mvn -pl coderhino-external-consumer-spring -am test
```

## Optional live manual run

The runnable sample starts a non-web Spring Boot context and uses the real auto-configured `ModelClient`. Treat that path as opt-in only.

`src/main/resources/application.properties` is the primary checked-in configuration example. Keep placeholders in source control. For real deployments, prefer environment variables, a secret manager behind `CoderhinoAgentCredentialProvider`, or a custom `ModelClient`.

```properties
coderhino.agent.provider-api-type=OPENAI
coderhino.agent.model=gpt-4.1-mini
coderhino.agent.context-window=65536
coderhino.agent.max-output-tokens=4096
coderhino.agent.api-base-url=https://api.openai.com/v1
coderhino.agent.api-key=replace-with-your-api-key
```

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

Build from the repository root, then run from this module directory with `CODERHINO_AGENT_API_KEY` set, `coderhino.agent.api-key` configured, or a `CoderhinoAgentCredentialProvider` bean in place. The current default Spring auto-config path also accepts `ANTHROPIC_API_KEY` as a fallback. Bare `OPENAI_API_KEY` is not part of this default credential order.

```bash
mvn -pl coderhino-external-consumer-spring -am package
CODERHINO_AGENT_API_KEY=your-key mvn exec:java
```

This module is verification/sample code only and is not part of the first-release public runtime library set.
