package com.coderhino.state;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

public record SessionSummary(
    UUID sessionId,
    String customTitle,
    String firstPrompt,
    int messageCount,
    Instant updatedAt,
    Path sessionFile
) {
}
