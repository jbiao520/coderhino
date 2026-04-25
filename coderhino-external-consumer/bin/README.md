# Coderhino External Consumer Verification

This module is a manual verification sample for consuming Coderhino's embeddable runtime from a consumer-shaped Maven project.

Run it from the repository root with:

```bash
mvn -pl coderhino-external-consumer -am test
```

It depends on `com.coderhino:coderhino-agent-runtime` and intentionally does not depend on the backend or web application modules.
