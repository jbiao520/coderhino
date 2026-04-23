package com.coderhino.state;

import com.coderhino.types.Message;

import java.time.Instant;
import java.util.UUID;

public record SessionRecord(
    String entryType,
    UUID sessionId,
    UUID uuid,
    UUID parentUuid,
    Instant timestamp,
    String messageType,
    String content,
    String toolName,
    String toolUseId,
    String sourceAssistantMessageId,
    String assistantMessageId,
    String activityTimelineJson,
    String fileSummaryJson,
    String customTitle,
    String cwd
) {
    public static SessionRecord forMessage(UUID sessionId, String cwd, Message.Envelope envelope) {
        String toolName = null;
        String toolUseId = null;
        if (envelope.message() instanceof Message.ToolResultMessage toolResultMessage) {
            toolName = toolResultMessage.toolName();
            toolUseId = toolResultMessage.toolUseId();
        } else if (envelope.message() instanceof Message.AssistantToolUseMessage toolUseMessage) {
            toolName = toolUseMessage.toolName();
            toolUseId = toolUseMessage.toolUseId();
        }
        var sourceAssistantMessageId = envelope.message() instanceof Message.ToolResultMessage toolResultMessage
            ? toolResultMessage.sourceAssistantMessageId()
            : envelope.message() instanceof Message.AssistantToolUseMessage toolUseMessage
                ? toolUseMessage.assistantMessageId()
                : null;
        return new SessionRecord(
            "message",
            sessionId,
            envelope.uuid(),
            envelope.parentUuid(),
            envelope.timestamp(),
            envelope.message().type(),
            envelope.message().content(),
            toolName,
            toolUseId,
            sourceAssistantMessageId,
            null,
            null,
            null,
            null,
            cwd
        );
    }

    public static SessionRecord forCustomTitle(UUID sessionId, String cwd, String customTitle) {
        return new SessionRecord(
            "custom-title",
            sessionId,
            UUID.randomUUID(),
            null,
            Instant.now(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            customTitle,
            cwd
        );
    }
}
