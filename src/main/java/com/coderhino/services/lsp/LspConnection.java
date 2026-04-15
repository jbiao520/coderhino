package com.coderhino.services.lsp;

import java.time.Instant;
import java.util.List;

public record LspConnection(
    String language,
    boolean connected,
    Instant lastStartedAt,
    String statusMessage,
    Long processId,
    List<String> commandLine
) {
    public LspConnection {
        commandLine = List.copyOf(commandLine == null ? List.of() : commandLine);
    }
}
