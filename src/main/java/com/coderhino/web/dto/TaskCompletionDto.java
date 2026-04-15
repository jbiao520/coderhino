package com.coderhino.web.dto;

import com.coderhino.services.tasks.TaskRecord;
import com.coderhino.web.notifications.CompletionNotification;

import java.time.Instant;

public record TaskCompletionDto(
    String completionId,
    String taskId,
    String runId,
    String description,
    String projectId,
    String sessionId,
    Instant completedAt
) {
    public static TaskCompletionDto from(TaskRecord record) {
        return new TaskCompletionDto(
            record.id().toString(),
            record.id().toString(),
            null,
            record.description(),
            record.projectId(),
            record.sessionId(),
            record.updatedAt()
        );
    }

    public static TaskCompletionDto from(CompletionNotification notification) {
        return new TaskCompletionDto(
            notification.completionId(),
            notification.taskId(),
            notification.runId(),
            notification.description(),
            notification.projectId(),
            notification.sessionId(),
            notification.completedAt()
        );
    }
}
