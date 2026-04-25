package com.coderhino.query;

import java.util.Map;

public sealed interface ModelResponse permits ModelResponse.AssistantReply, ModelResponse.ToolRequest, ModelResponse.ModelError {
    record Usage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {
        public Usage(long inputTokens, long outputTokens) {
            this(inputTokens, outputTokens, 0, 0);
        }

        public long contextLength() {
            return inputTokens + outputTokens + cacheCreationTokens + cacheReadTokens;
        }
    }

    record AssistantReply(String text, Usage usage) implements ModelResponse {
        public AssistantReply(String text) {
            this(text, new Usage(0, 0));
        }
    }

    record ToolRequest(String toolName, Map<String, Object> arguments, String toolUseId, Usage usage) implements ModelResponse {
        public ToolRequest(String toolName, Map<String, Object> arguments, String toolUseId) {
            this(toolName, arguments, toolUseId, new Usage(0, 0));
        }

        public ToolRequest(String toolName, Map<String, Object> arguments) {
            this(toolName, arguments, null, new Usage(0, 0));
        }
    }

    record ModelError(String message, Usage usage) implements ModelResponse {
        public ModelError(String message) {
            this(message, new Usage(0, 0));
        }
    }
}
