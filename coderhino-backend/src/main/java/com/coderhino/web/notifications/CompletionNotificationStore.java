package com.coderhino.web.notifications;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class CompletionNotificationStore {

    private final CopyOnWriteArrayList<CompletionNotification> notifications = new CopyOnWriteArrayList<>();

    public void recordAiRunCompletion(String runId, String sessionId, String projectId, Instant completedAt) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        var effectiveCompletedAt = completedAt == null ? Instant.now() : completedAt;
        notifications.add(new CompletionNotification(
            runId,
            "AI run completed",
            blankToNull(projectId),
            blankToNull(sessionId),
            effectiveCompletedAt,
            runId,
            null
        ));
    }

    public List<CompletionNotification> listCompletedAfter(Instant since) {
        return notifications.stream()
            .filter(notification -> since == null || !notification.completedAt().isBefore(since))
            .sorted(Comparator.comparing(CompletionNotification::completedAt, Comparator.nullsLast(Comparator.naturalOrder())))
            .toList();
    }

    private static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        var trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
