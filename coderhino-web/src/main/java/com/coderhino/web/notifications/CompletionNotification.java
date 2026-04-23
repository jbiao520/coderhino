package com.coderhino.web.notifications;

import java.time.Instant;

public record CompletionNotification(
    String completionId,
    String description,
    String projectId,
    String sessionId,
    Instant completedAt,
    String runId,
    String taskId
) {}
