package com.coderhino.services.compact;

import com.coderhino.types.Message;

import java.util.List;
import java.util.stream.Collectors;

public final class CompactPromptBuilder {

    private CompactPromptBuilder() {
    }

    public static String buildSummaryPrompt(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Summarize the following conversation history concisely. ");
        sb.append("Focus on key decisions, findings, and current state.\n\n");

        for (Message msg : messages) {
            sb.append(formatMessage(msg));
            sb.append("\n");
        }

        return sb.toString();
    }

    public static String buildSummaryPrompt(String conversationBlock) {
        if (conversationBlock == null || conversationBlock.isEmpty()) {
            return "";
        }

        return "Summarize the following conversation concisely. Focus on key decisions, findings, and current state.\n\n" + conversationBlock;
    }

    public static String formatMessage(Message message) {
        if (message instanceof Message.UserMessage m) {
            return "[User] " + truncate(m.content(), 500);
        }
        if (message instanceof Message.AssistantMessage m) {
            return "[Assistant] " + truncate(m.content(), 500);
        }
        if (message instanceof Message.AssistantToolUseMessage m) {
            return "[Assistant used " + m.toolName() + "] " + truncate(m.content(), 200);
        }
        if (message instanceof Message.SystemMessage m) {
            return "[System] " + truncate(m.content(), 300);
        }
        if (message instanceof Message.ToolResultMessage m) {
            return "[Tool " + m.toolName() + " result] " + truncate(m.content(), 300);
        }
        return "";
    }

    public static String formatGroupSummary(MessageGrouping.MessageGroup group) {
        if (group == null || group.isEmpty()) {
            return "[Empty conversation segment]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Summary of ").append(group.messageCount()).append(" messages, ~")
          .append(group.tokenCount()).append(" tokens]:\n");

        List<String> summaries = group.messages().stream()
            .map(CompactPromptBuilder::extractBrief)
            .collect(Collectors.toList());

        sb.append(String.join(" | ", summaries));
        return sb.toString();
    }

    public static String formatCompactResult(
            long originalTokens,
            long compactedTokens,
            int originalMessageCount,
            int compactedMessageCount) {

        double reductionPercent = originalTokens > 0
            ? (1.0 - (double) compactedTokens / originalTokens) * 100
            : 0;

        return """
            Compaction Summary
            =================
            Original: %d messages, ~%d tokens
            After compaction: %d messages, ~%d tokens
            Reduction: %.1f%%
            """.formatted(
                originalMessageCount, originalTokens,
                compactedMessageCount, compactedTokens,
                reductionPercent
        );
    }

    public static String extractBrief(Message message) {
        String content;
        if (message instanceof Message.UserMessage m) {
            content = m.content();
        } else if (message instanceof Message.AssistantMessage m) {
            content = m.content();
        } else if (message instanceof Message.AssistantToolUseMessage m) {
            content = m.content();
        } else if (message instanceof Message.SystemMessage m) {
            content = m.content();
        } else if (message instanceof Message.ToolResultMessage m) {
            content = m.content();
        } else {
            content = "";
        }

        if (content == null || content.isEmpty()) {
            return message.type() + "(empty)";
        }

        return truncate(content, 100);
    }

    public static String truncate(String text, int maxLength) {
        if (text == null) {
            return "";
        }
        if (text.length() <= maxLength) {
            return text;
        }
        return text.substring(0, maxLength - 3) + "...";
    }

    public static String buildCompactPromptText(
            List<MessageGrouping.MessageGroup> groupsToCompact,
            List<Message> recentMessages) {

        StringBuilder sb = new StringBuilder();

        if (groupsToCompact != null && !groupsToCompact.isEmpty()) {
            sb.append("Earlier conversation:\n");
            for (MessageGrouping.MessageGroup group : groupsToCompact) {
                sb.append(formatGroupSummary(group));
                sb.append("\n\n");
            }
        }

        if (recentMessages != null && !recentMessages.isEmpty()) {
            sb.append("Recent conversation:\n");
            for (Message msg : recentMessages) {
                sb.append(formatMessage(msg));
                sb.append("\n");
            }
        }

        return sb.toString();
    }
}