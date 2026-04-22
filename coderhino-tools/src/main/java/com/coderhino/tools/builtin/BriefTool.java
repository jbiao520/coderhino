package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public final class BriefTool implements ToolDefinition<BriefTool.Input, BriefTool.Output> {

    @Override
    public String name() {
        return "SendUserMessage";
    }

    @Override
    public String description() {
        return "Send a message to the user, optionally with attachments and a status indicating normal or proactive delivery.";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "message", Map.of("type", "string", "description", "The message text to send to the user"),
            "attachments", Map.of("type", "array", "description", "Optional list of attachment references"),
            "status", Map.of("type", "string", "description", "Delivery status: 'normal' or 'proactive'")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.message() == null || input.message().isBlank()) {
            return PermissionResult.deny("message must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        String sentAt = Instant.now().toString();
        return new Output(input.message(), sentAt);
    }

    public record Input(String message, List<String> attachments, String status) {
    }

    public record Output(String message, String sentAt) {
    }
}
