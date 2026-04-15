package com.coderhino.services.compact;

import com.coderhino.types.CompactBoundary;
import com.coderhino.types.Message;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;

public final class CompactService {

    private final CompactBoundary defaultBoundary;

    public CompactService() {
        this(CompactBoundary.moderate());
    }

    public CompactService(CompactBoundary boundary) {
        this.defaultBoundary = boundary != null ? boundary : CompactBoundary.moderate();
    }

    public CompactResult compact(List<Message> messages) {
        return compact(messages, defaultBoundary);
    }

    public CompactResult compactManual(List<Message> messages) {
        return compactManual(messages, defaultBoundary, null);
    }

    public CompactResult compactManual(List<Message> messages, String customInstructions) {
        return compactManual(messages, defaultBoundary, customInstructions);
    }

    public CompactResult compactManual(List<Message> messages, CompactBoundary boundary, String customInstructions) {
        if (messages == null || messages.isEmpty()) {
            return CompactResult.empty();
        }

        CompactBoundary effectiveBoundary = boundary != null ? boundary : defaultBoundary;
        List<Message> projectedMessages = projectMessagesForManualCompaction(messages);
        if (projectedMessages.isEmpty()) {
            return CompactResult.empty();
        }

        int preserveRecent = Math.min(Math.max(1, effectiveBoundary.maxMessages()), projectedMessages.size());
        int compactedCount = projectedMessages.size() - preserveRecent;
        if (compactedCount <= 0) {
            long tokens = MessageGrouping.estimateTokens(projectedMessages);
            return new CompactResult(
                messages,
                projectedMessages,
                MessageGrouping.estimateTokens(messages),
                tokens,
                messages.size(),
                projectedMessages.size(),
                CompactStatus.ALREADY_WITHIN_LIMITS,
                "Not enough conversation history to compact yet."
            );
        }

        List<Message> compactable = projectedMessages.subList(0, compactedCount);
        List<Message> preserved = projectedMessages.subList(compactedCount, projectedMessages.size());
        String summaryText = buildManualSummaryText(compactable, customInstructions);

        var compactedMessages = new ArrayList<Message>(preserved.size() + 1);
        compactedMessages.add(new Message.AssistantMessage(summaryText));
        compactedMessages.addAll(preserved);

        long originalTokens = MessageGrouping.estimateTokens(messages);
        long compactedTokens = MessageGrouping.estimateTokens(compactedMessages);
        return new CompactResult(
            messages,
            compactedMessages,
            originalTokens,
            compactedTokens,
            messages.size(),
            compactedMessages.size(),
            CompactStatus.COMPACTED,
            CompactPromptBuilder.formatCompactResult(
                originalTokens,
                compactedTokens,
                messages.size(),
                compactedMessages.size()
            )
        );
    }

    public CompactResult compact(List<Message> messages, CompactBoundary boundary) {
        if (messages == null || messages.isEmpty()) {
            return CompactResult.empty();
        }

        CompactBoundary effectiveBoundary = boundary != null ? boundary : defaultBoundary;

        long originalTokens = MessageGrouping.estimateTokens(messages);
        int originalCount = messages.size();

        if (!effectiveBoundary.exceedsMax(originalTokens)) {
            return new CompactResult(
                messages,
                messages,
                originalTokens,
                originalTokens,
                originalCount,
                originalCount,
                CompactStatus.ALREADY_WITHIN_LIMITS,
                ""
            );
        }

        int preserveRecent = Math.min(10, originalCount);
        List<MessageGrouping.MessageGroup> groups = MessageGrouping.groupMessagesForCompaction(
            messages, effectiveBoundary, preserveRecent
        );

        List<MessageGrouping.MessageGroup> toCompact = new ArrayList<>();
        List<Message> recent = Collections.emptyList();

        if (!groups.isEmpty()) {
            recent = groups.get(groups.size() - 1).messages();
            for (int i = 0; i < groups.size() - 1; i++) {
                toCompact.add(groups.get(i));
            }
        }

        String summaryText = buildSummaryText(toCompact);
        long compactedTokens = MessageGrouping.estimateTokens(recent) +
            estimateSummaryTokens(summaryText);
        int compactedCount = recent.size() + 1;

        List<Message> compactedMessages = new ArrayList<>(recent);
        compactedMessages.add(new Message.AssistantMessage(summaryText));

        return new CompactResult(
            messages,
            compactedMessages,
            originalTokens,
            compactedTokens,
            originalCount,
            compactedCount,
            CompactStatus.COMPACTED,
            CompactPromptBuilder.formatCompactResult(
                originalTokens, compactedTokens, originalCount, compactedCount
            )
        );
    }

    public CompactResult compactWithSimulatedSummary(
            List<Message> messages,
            Function<String, String> summarySimulator) {
        return compactWithSimulatedSummary(messages, defaultBoundary, summarySimulator);
    }

    public CompactResult compactWithSimulatedSummary(
            List<Message> messages,
            CompactBoundary boundary,
            Function<String, String> summarySimulator) {

        if (messages == null || messages.isEmpty()) {
            return CompactResult.empty();
        }

        CompactBoundary effectiveBoundary = boundary != null ? boundary : defaultBoundary;

        long originalTokens = MessageGrouping.estimateTokens(messages);
        int originalCount = messages.size();

        if (!effectiveBoundary.exceedsMax(originalTokens)) {
            return new CompactResult(
                messages,
                messages,
                originalTokens,
                originalTokens,
                originalCount,
                originalCount,
                CompactStatus.ALREADY_WITHIN_LIMITS,
                ""
            );
        }

        int preserveRecent = Math.min(10, originalCount);
        List<MessageGrouping.MessageGroup> groups = MessageGrouping.groupMessagesForCompaction(
            messages, effectiveBoundary, preserveRecent
        );

        List<MessageGrouping.MessageGroup> toCompact = new ArrayList<>();
        List<Message> recent = Collections.emptyList();

        if (!groups.isEmpty()) {
            recent = groups.get(groups.size() - 1).messages();
            for (int i = 0; i < groups.size() - 1; i++) {
                toCompact.add(groups.get(i));
            }
        }

        String promptText = CompactPromptBuilder.buildCompactPromptText(toCompact, recent);
        String simulatedSummary = summarySimulator.apply(promptText);

        long compactedTokens = MessageGrouping.estimateTokens(recent) +
            estimateSummaryTokens(simulatedSummary);
        int compactedCount = recent.size() + 1;

        List<Message> compactedMessages = new ArrayList<>(recent);
        compactedMessages.add(new Message.AssistantMessage(simulatedSummary));

        return new CompactResult(
            messages,
            compactedMessages,
            originalTokens,
            compactedTokens,
            originalCount,
            compactedCount,
            CompactStatus.COMPACTED,
            CompactPromptBuilder.formatCompactResult(
                originalTokens, compactedTokens, originalCount, compactedCount
            )
        );
    }

    public boolean shouldCompact(List<Message> messages) {
        return shouldCompact(messages, defaultBoundary);
    }

    public boolean shouldCompact(List<Message> messages, CompactBoundary boundary) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        CompactBoundary effectiveBoundary = boundary != null ? boundary : defaultBoundary;
        return effectiveBoundary.exceedsMax(MessageGrouping.estimateTokens(messages));
    }

    public long estimateTokens(List<Message> messages) {
        return MessageGrouping.estimateTokens(messages);
    }

    private String buildSummaryText(List<MessageGrouping.MessageGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return "[Earlier conversation summary - no significant history]";
        }

        int totalMessages = groups.stream().mapToInt(MessageGrouping.MessageGroup::messageCount).sum();

        List<String> keyFacts = new ArrayList<>();
        for (MessageGrouping.MessageGroup group : groups) {
            for (Message msg : group.messages()) {
                String fact = extractKeyFact(msg);
                if (fact != null && !fact.isBlank()) {
                    keyFacts.add(fact);
                }
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("[Earlier conversation summary of ").append(totalMessages).append(" messages]:\n");

        if (keyFacts.isEmpty()) {
            sb.append("No significant facts extracted from earlier messages.");
        } else {
            for (String fact : keyFacts) {
                sb.append("- ").append(fact).append("\n");
            }
        }

        return sb.toString();
    }

    private List<Message> projectMessagesForManualCompaction(List<Message> messages) {
        int lastBoundaryIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            Message message = messages.get(i);
            if (message instanceof Message.SystemMessage systemMessage
                && isCompactBoundaryMessage(systemMessage.content())) {
                lastBoundaryIndex = i;
            }
        }
        return lastBoundaryIndex >= 0
            ? List.copyOf(messages.subList(lastBoundaryIndex + 1, messages.size()))
            : List.copyOf(messages);
    }

    private boolean isCompactBoundaryMessage(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        return content.contains("Earlier conversation compacted")
            || content.contains("Earlier conversation summary")
            || content.contains("Conversation compacted");
    }

    private String buildManualSummaryText(List<Message> messages, String customInstructions) {
        if (messages == null || messages.isEmpty()) {
            return "[Earlier conversation summary - no significant history]";
        }

        List<String> keyFacts = new ArrayList<>();
        for (Message msg : messages) {
            String fact = extractKeyFact(msg);
            if (fact != null && !fact.isBlank()) {
                keyFacts.add(fact);
            }
        }

        var sb = new StringBuilder();
        sb.append("[Earlier conversation summary of ").append(messages.size()).append(" messages]");
        if (customInstructions != null && !customInstructions.isBlank()) {
            sb.append("\nFocus: ").append(customInstructions.trim());
        }
        if (keyFacts.isEmpty()) {
            sb.append("\n- No significant facts extracted from earlier messages.");
            return sb.toString();
        }
        for (String fact : keyFacts) {
            sb.append("\n- ").append(fact);
        }
        return sb.toString();
    }

    private String extractKeyFact(Message message) {
        if (message instanceof Message.UserMessage m) {
            String content = m.content();
            if (content == null || content.isBlank()) {
                return null;
            }
            String trimmed = content.trim();
            String brief = trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
            return "User: " + brief;
        }
        if (message instanceof Message.AssistantMessage m) {
            String content = m.content();
            if (content == null || content.isBlank()) {
                return null;
            }
            String trimmed = content.trim();
            String brief = trimmed.length() > 120 ? trimmed.substring(0, 117) + "..." : trimmed;
            return "Assistant: " + brief;
        }
        if (message instanceof Message.AssistantToolUseMessage m) {
            return "Tool used: " + m.toolName() + (m.content() != null && !m.content().isBlank()
                ? " (" + (m.content().length() > 80 ? m.content().substring(0, 77) + "..." : m.content()) + ")"
                : "");
        }
        if (message instanceof Message.ToolResultMessage m) {
            String content = m.content();
            if (content == null || content.isBlank()) {
                return null;
            }
            String brief = content.length() > 100 ? content.substring(0, 97) + "..." : content;
            return "Tool result (" + m.toolName() + "): " + brief;
        }
        return null;
    }

    private long estimateSummaryTokens(String summary) {
        if (summary == null || summary.isEmpty()) {
            return 0;
        }
        return summary.length() / 4;
    }

    public enum CompactStatus {
        ALREADY_WITHIN_LIMITS,
        COMPACTED,
        NO_OP
    }

    public record CompactResult(
        List<Message> originalMessages,
        List<Message> compactedMessages,
        long originalTokens,
        long compactedTokens,
        int originalMessageCount,
        int compactedMessageCount,
        CompactStatus status,
        String summary
    ) {
        public static CompactResult empty() {
            return new CompactResult(
                Collections.emptyList(),
                Collections.emptyList(),
                0,
                0,
                0,
                0,
                CompactStatus.NO_OP,
                ""
            );
        }

        public long tokenReduction() {
            return Math.max(0, originalTokens - compactedTokens);
        }

        public double reductionPercent() {
            if (originalTokens <= 0) {
                return 0;
            }
            return (1.0 - (double) compactedTokens / originalTokens) * 100;
        }

        public boolean wasCompacted() {
            return status == CompactStatus.COMPACTED;
        }
    }
}
