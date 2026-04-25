# Coderhino External Consumer Verification

This module is a manual verification sample for consuming Coderhino's embeddable runtime from a consumer-shaped Maven project.

It uses the publishable `com.coderhino:coderhino-agent-runtime` surface plus the runtime config classes that load the same persisted settings used by Coderhino itself. `ExternalConsumerApp` resolves the default provider, sends the prompt `"Hi"`, and prints the real assistant response.

## Configuration

The live run looks for Coderhino's normal local config files in the nearest ancestor `.coderhino/` directory:

- `.coderhino/api-credentials.json`: must contain a default provider with a non-empty `apiKey`
- `.coderhino/web-settings.json`: optional; can provide `defaultModel` when the provider does not list models

The current runtime supports `CLAUDE_CODE` providers. If no default provider or API key is configured, the app exits with a setup error instead of faking a response.

## Build Verification

Verify the module from the repository root without making a live provider call:

```bash
mvn -pl coderhino-external-consumer -am test
```

## Manual Live Run

Run the real external-consumer sample from the repository root:

```bash
mvn -pl coderhino-external-consumer -am package && mvn exec:java
```

Run the second command from `coderhino-external-consumer/`. The app resolves the nearest ancestor `.coderhino/` directory, so it can still use repository-level config when launched from the module directory.

It depends on publishable Coderhino runtime libraries and intentionally does not depend on the backend or web application modules.
