package com.coderhino.services.mcp;

import java.time.Instant;
import java.util.List;

public record McpConnection(
    String serverName,
    boolean connected,
    Instant lastStartedAt,
    String statusMessage,
    Long processId,
    List<String> commandLine
) {
    public McpConnection {
        commandLine = List.copyOf(commandLine == null ? List.of() : commandLine);
    }
}
