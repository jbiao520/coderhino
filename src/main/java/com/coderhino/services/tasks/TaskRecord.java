package com.coderhino.services.tasks;

import java.time.Instant;
import java.util.UUID;

public record TaskRecord(
    UUID id,
    String description,
    String status,
    String output,
    Instant createdAt,
    Instant updatedAt,
    String projectId,
    String sessionId
) {
    public TaskRecord(UUID id, String description, String status, Instant createdAt, Instant updatedAt) {
        this(id, description, status, null, createdAt, updatedAt, null, null);
    }

    public TaskRecord(UUID id, String description, String status, Instant createdAt, Instant updatedAt,
                      String projectId, String sessionId) {
        this(id, description, status, null, createdAt, updatedAt, projectId, sessionId);
    }
}
