package com.coderhino.types.permissions;

import com.coderhino.types.PermissionMode;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

public record PermissionContext(
        String toolName,
        Map<String, Object> toolArgs,
        PermissionMode mode,
        Optional<String> userId,
        Path workingDirectory,
        Instant timestamp
) {
    public PermissionContext {
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public PermissionContext(String toolName, Map<String, Object> toolArgs, PermissionMode mode, Path workingDirectory) {
        this(toolName, toolArgs, mode, Optional.empty(), workingDirectory, Instant.now());
    }
}
