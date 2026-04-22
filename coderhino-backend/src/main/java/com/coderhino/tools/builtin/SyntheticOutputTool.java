package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class SyntheticOutputTool implements ToolDefinition<SyntheticOutputTool.Input, String> {
    @Override
    public String name() {
        return "synthetic_output";
    }

    @Override
    public String description() {
        return "Return structured JSON-like output wrapping the provided content";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "content", Map.of("type", "string", "description", "Content to wrap in structured output")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.content() == null) {
            return PermissionResult.deny("content must not be null.");
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        var escaped = input.content()
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
        return "{\"output\": \"" + escaped + "\"}";
    }

    public record Input(String content) {
    }
}
