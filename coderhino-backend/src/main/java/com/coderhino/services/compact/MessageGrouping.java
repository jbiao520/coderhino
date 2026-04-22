package com.coderhino.services.compact;

import com.coderhino.types.Message;
import com.coderhino.types.CompactBoundary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MessageGrouping {

    private MessageGrouping() {
    }

    public static List<MessageGroup> groupMessages(List<Message> messages, CompactBoundary boundary) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        List<MessageGroup> groups = new ArrayList<>();
        List<Message> currentGroup = new ArrayList<>();
        long currentTokens = 0;

        for (Message msg : messages) {
            long msgTokens = estimateTokens(msg);
            if (currentTokens + msgTokens > boundary.maxTokens() && !currentGroup.isEmpty()) {
                groups.add(new MessageGroup(List.copyOf(currentGroup), currentTokens));
                currentGroup = new ArrayList<>();
                currentTokens = 0;
            }
            currentGroup.add(msg);
            currentTokens += msgTokens;
        }

        if (!currentGroup.isEmpty()) {
            groups.add(new MessageGroup(List.copyOf(currentGroup), currentTokens));
        }

        return groups;
    }

    public static List<MessageGroup> groupMessagesForCompaction(
            List<Message> messages,
            CompactBoundary boundary,
            int keepRecentCount) {
        if (messages == null || messages.isEmpty()) {
            return Collections.emptyList();
        }

        int totalMessages = messages.size();
        int preserveCount = Math.min(keepRecentCount, totalMessages);

        List<Message> toCompact = messages.subList(0, totalMessages - preserveCount);
        List<Message> toPreserve = messages.subList(totalMessages - preserveCount, totalMessages);

        List<MessageGroup> groups = new ArrayList<>();
        if (!toCompact.isEmpty()) {
            groups.addAll(groupMessages(toCompact, boundary));
        }

        if (!toPreserve.isEmpty()) {
            long preserveTokens = estimateTokens(toPreserve);
            groups.add(new MessageGroup(List.copyOf(toPreserve), preserveTokens));
        }

        return groups;
    }

    public static MessageGroup compactGroup(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return new MessageGroup(Collections.emptyList(), 0);
        }
        long tokens = estimateTokens(messages);
        return new MessageGroup(List.copyOf(messages), tokens);
    }

    public static long estimateTokens(Message message) {
        if (message instanceof Message.UserMessage m) {
            return roughTokens(m.content());
        }
        if (message instanceof Message.AssistantMessage m) {
            return roughTokens(m.content());
        }
        if (message instanceof Message.AssistantToolUseMessage m) {
            return roughTokens(m.content());
        }
        if (message instanceof Message.SystemMessage m) {
            return roughTokens(m.content());
        }
        if (message instanceof Message.ToolResultMessage m) {
            return roughTokens(m.content());
        }
        return 0;
    }

    public static long estimateTokens(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return 0;
        }
        return messages.stream().mapToLong(MessageGrouping::estimateTokens).sum();
    }

    private static long roughTokens(String content) {
        if (content == null || content.isEmpty()) {
            return 0;
        }
        return content.length() / 4;
    }

    public record MessageGroup(List<Message> messages, long tokenCount) {
        public MessageGroup {
            messages = messages != null ? List.copyOf(messages) : Collections.emptyList();
        }

        public int messageCount() {
            return messages.size();
        }

        public boolean isEmpty() {
            return messages.isEmpty();
        }
    }
}