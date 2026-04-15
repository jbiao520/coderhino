package com.coderhino.services.mcp;

public record McpResourceDescriptor(
    String uri,
    String name,
    String mimeType,
    String description
) {
}
