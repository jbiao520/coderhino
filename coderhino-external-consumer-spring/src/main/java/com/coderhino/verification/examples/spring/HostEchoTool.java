package com.coderhino.verification.examples.spring;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class HostEchoTool implements ToolDefinition<HostEchoTool.Input, String> {
    public static final String TOOL_NAME = "host_echo";
    private static final String PREFIX = "host:";

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "Echo host-provided text deterministically";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "message", Map.of("type", "string")
        ));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.message() == null || input.message().isBlank()) {
            return PermissionResult.deny("message must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) {
        return PREFIX + input.message().trim();
    }

    public record Input(String message) {
    }
}
