package com.coderhino.services.mcp;

import java.util.List;
import java.util.Map;

public record McpServerDefinition(
    String name,
    String command,
    List<String> arguments,
    Map<String, String> environment,
    boolean enabled,
    long initializeTimeoutMs
) {
    public McpServerDefinition {
        arguments = List.copyOf(arguments);
        environment = Map.copyOf(environment);
        if (initializeTimeoutMs <= 0) {
            initializeTimeoutMs = 30_000L;
        }
    }
}
