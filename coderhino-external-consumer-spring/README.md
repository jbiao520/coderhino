# Coderhino External Consumer Spring Verification

This module is a manual verification sample for consuming `coderhino-agent-spring` from a consumer-shaped non-web Spring Boot application.

It depends on `com.coderhino:coderhino-agent-spring` plus minimal Spring Boot support and intentionally does not depend on `coderhino-backend` or `coderhino-web`. The sample uses the real auto-configured `ModelClient`, so startup requires `coderhino.agent.api-key` (or `CODERHINO_AGENT_API_KEY`) even though the verification test stops at context creation and does not make an outbound provider call.

## Build Verification

Verify the module from the repository root:

```bash
mvn -pl coderhino-external-consumer-spring -am test
```

## Manual Run

Build from the repository root and then run the sample from this module directory:

```bash
mvn -pl coderhino-external-consumer-spring -am package
CODERHINO_AGENT_API_KEY=your-key mvn exec:java
```

The runnable example starts a non-web Spring Boot context, lets `coderhino-agent-spring` auto-configure a `CoderhinoAgent` plus the real `ModelClient`, and prints a short startup confirmation.

This module is verification/sample code only and is not part of the first-release public runtime library set.
