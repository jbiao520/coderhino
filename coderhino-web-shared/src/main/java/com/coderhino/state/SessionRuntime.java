package com.coderhino.state;

import com.coderhino.types.Message;

import java.util.List;
import java.util.UUID;

public record SessionRuntime(
    UUID sessionId,
    UUID lastMessageId,
    String customTitle,
    List<Message.Envelope> transcript,
    List<RawAiHistoryEntry> rawAiHistory,
    List<CompletedTurnActivity> completedTurnActivities
) {
    public SessionRuntime {
        transcript = List.copyOf(transcript);
        rawAiHistory = List.copyOf(rawAiHistory);
        completedTurnActivities = List.copyOf(completedTurnActivities);
    }

    public static SessionRuntime create() {
        return new SessionRuntime(UUID.randomUUID(), null, null, List.of(), List.of(), List.of());
    }

    public SessionRuntime withCustomTitle(String nextTitle) {
        return new SessionRuntime(sessionId, lastMessageId, nextTitle, transcript, rawAiHistory, completedTurnActivities);
    }

    public SessionRuntime append(Message.Envelope envelope) {
        var next = new java.util.ArrayList<>(transcript);
        next.add(envelope);
        return new SessionRuntime(sessionId, envelope.uuid(), customTitle, next, rawAiHistory, completedTurnActivities);
    }

    public SessionRuntime replaceTranscript(List<Message.Envelope> envelopes) {
        UUID nextLastMessageId = envelopes.isEmpty() ? null : envelopes.get(envelopes.size() - 1).uuid();
        var retainedMessageIds = envelopes.stream()
            .map(Message.Envelope::uuid)
            .collect(java.util.stream.Collectors.toSet());
        var retainedActivities = completedTurnActivities.stream()
            .filter(activity -> retainedMessageIds.contains(activity.assistantMessageId()))
            .toList();
        return new SessionRuntime(sessionId, nextLastMessageId, customTitle, envelopes, rawAiHistory, retainedActivities);
    }

    public SessionRuntime appendRawAiHistory(RawAiHistoryEntry entry) {
        var next = new java.util.ArrayList<>(rawAiHistory);
        next.add(entry);
        return new SessionRuntime(sessionId, lastMessageId, customTitle, transcript, next, completedTurnActivities);
    }

    public SessionRuntime appendCompletedTurnActivity(CompletedTurnActivity activity) {
        var next = new java.util.ArrayList<>(completedTurnActivities);
        next.removeIf(existing -> existing.assistantMessageId().equals(activity.assistantMessageId()));
        next.add(activity);
        return new SessionRuntime(sessionId, lastMessageId, customTitle, transcript, rawAiHistory, next);
    }

    public record RawAiHistoryEntry(
        java.time.Instant timestamp,
        String direction,
        String content
    ) {
    }

    public record CompletedTurnActivity(
        UUID assistantMessageId,
        List<ActivityItem> transcript,
        FileChangeSummary fileSummary
    ) {
        public CompletedTurnActivity {
            transcript = List.copyOf(transcript);
        }

        public record ActivityItem(
            String kind,
            String content,
            String toolName,
            String toolUseId,
            String argumentsJson,
            String output
        ) {
        }

        public record FileChangeSummary(
            int totalChanges,
            List<String> created,
            List<String> modified,
            List<String> deleted
        ) {
            public FileChangeSummary {
                created = created != null ? List.copyOf(created) : List.of();
                modified = modified != null ? List.copyOf(modified) : List.of();
                deleted = deleted != null ? List.copyOf(deleted) : List.of();
            }
        }
    }
}
