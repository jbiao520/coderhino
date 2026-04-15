package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public final class SendMessageTool implements ToolDefinition<SendMessageTool.Input, SendMessageTool.Output> {

    public record SentMessage(String recipient, String message, Instant sentAt) {
    }

    private static final CopyOnWriteArrayList<SentMessage> MESSAGE_QUEUE = new CopyOnWriteArrayList<>();

    @Override
    public String name() {
        return "send_message";
    }

    @Override
    public String description() {
        return "Send a message to another agent or session by recipient name";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "recipient", Map.of("type", "string", "description", "The recipient agent or session name"),
            "message", Map.of("type", "string", "description", "The message content to send")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.recipient() == null || input.recipient().isBlank()) {
            return PermissionResult.deny("recipient must not be blank.");
        }
        if (input.message() == null || input.message().isBlank()) {
            return PermissionResult.deny("message must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var sent = new SentMessage(input.recipient(), input.message(), Instant.now());
        MESSAGE_QUEUE.add(sent);
        return new Output(input.recipient(), input.message(), "delivered", MESSAGE_QUEUE.size());
    }

    public static List<SentMessage> getMessages() {
        return Collections.unmodifiableList(new ArrayList<>(MESSAGE_QUEUE));
    }

    public static List<SentMessage> getMessagesFor(String recipient) {
        return MESSAGE_QUEUE.stream()
            .filter(m -> m.recipient().equals(recipient))
            .toList();
    }

    public static void clearMessages() {
        MESSAGE_QUEUE.clear();
    }

    public record Input(String recipient, String message) {
        public Input {
            if (recipient != null) recipient = recipient.strip();
            if (message != null) message = message.strip();
        }
    }

    public record Output(String recipient, String message, String status, int queueSize) {
    }
}
