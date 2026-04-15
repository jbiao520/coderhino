package com.coderhino.services.mcp;

import java.util.Map;

public record McpToolDescriptor(
    String name,
    String description,
    Map<String, Object> inputSchema,
    boolean readOnlyHint,
    boolean destructiveHint
) {
    public McpToolDescriptor {
        inputSchema = inputSchema == null ? Map.of() : Map.copyOf(inputSchema);
    }
}
