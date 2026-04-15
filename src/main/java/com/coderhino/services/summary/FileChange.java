package com.coderhino.services.summary;

import java.nio.file.Path;
import java.time.Instant;

public record FileChange(Path file, FileOperation operation, Instant timestamp, String toolName) {
}
