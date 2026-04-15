package com.coderhino.types;

import java.time.Instant;
import java.util.UUID;

public sealed interface Message permits Message.UserMessage, Message.AssistantMessage, Message.AssistantToolUseMessage, Message.SystemMessage, Message.ToolResultMessage {
    String content();

    default String type() {
        if (this instanceof UserMessage) {
            return "user";
        }
        if (this instanceof AssistantMessage) {
            return "assistant";
        }
        if (this instanceof AssistantToolUseMessage) {
            return "assistant_tool_use";
        }
        if (this instanceof SystemMessage) {
            return "system";
        }
        if (this instanceof ToolResultMessage) {
            return "tool_result";
        }
        throw new IllegalStateException("Unsupported message type: " + getClass().getName());
    }

    record UserMessage(String content) implements Message {
    }

    record AssistantMessage(String content) implements Message {
    }

    record AssistantToolUseMessage(String content, String toolName, String toolUseId, String assistantMessageId) implements Message {
        public AssistantToolUseMessage(String content, String toolName, String toolUseId) {
            this(content, toolName, toolUseId, null);
        }
    }

    record SystemMessage(String content) implements Message {
    }

    record ToolResultMessage(String content, String toolName, String toolUseId, String sourceAssistantMessageId) implements Message {
        public ToolResultMessage(String content, String toolName) {
            this(content, toolName, null, null);
        }

        public ToolResultMessage(String content, String toolName, String toolUseId) {
            this(content, toolName, toolUseId, null);
        }
    }

    record Envelope(
        UUID uuid,
        UUID parentUuid,
        Instant timestamp,
        Message message
    ) {
    }
}
