package com.coderhino.services.tokens;

import com.coderhino.types.Message;

import java.util.List;

public final class TokenEstimation {

    private TokenEstimation() {
    }

    public static long roughEstimate(String content) {
        if (content == null || content.isEmpty()) {
            return 0L;
        }
        return content.length() / 4L;
    }

    public static long roughEstimateForJson(String content) {
        if (content == null || content.isEmpty()) {
            return 0L;
        }
        return content.length() / 2L;
    }

    public static long estimateForMessages(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0L;
        }
        long total = 0L;
        for (Message message : messages) {
            total += estimateForMessage(message);
        }
        return total;
    }

    public static long estimateForMessage(Message message) {
        return roughEstimate(contentLength(message));
    }

    private static String contentLength(Message msg) {
        if (msg instanceof Message.UserMessage userMsg) {
            return userMsg.content();
        }
        if (msg instanceof Message.AssistantMessage assistantMsg) {
            return assistantMsg.content();
        }
        if (msg instanceof Message.AssistantToolUseMessage toolUseMsg) {
            return toolUseMsg.content();
        }
        if (msg instanceof Message.SystemMessage systemMsg) {
            return systemMsg.content();
        }
        if (msg instanceof Message.ToolResultMessage toolResultMsg) {
            return toolResultMsg.content();
        }
        return "";
    }
}