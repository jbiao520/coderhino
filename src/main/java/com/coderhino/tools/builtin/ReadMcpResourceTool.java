package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

public final class ReadMcpResourceTool implements ToolDefinition<ReadMcpResourceTool.Input, ReadMcpResourceTool.Output> {
    @Override
    public String name() {
        return "ReadMcpResourceTool";
    }

    @Override
    public String description() {
        return "Read a specific resource from an MCP server";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "server", Map.of("type", "string"),
            "uri", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.server() == null || input.server().isBlank()) {
            return PermissionResult.deny("server must not be blank.");
        }
        if (input.uri() == null || input.uri().isBlank()) {
            return PermissionResult.deny("uri must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var content = context.services().mcp().readResource(input.server().trim(), input.uri().trim())
            .orElseThrow(() -> new IllegalArgumentException("Server not found: " + input.server().trim()));
        return new Output(List.of(new ResourceContent(input.uri().trim(), null, content)));
    }

    public record Input(String server, String uri) {
    }

    public record Output(List<ResourceContent> contents) {
    }

    public record ResourceContent(String uri, String mimeType, String text) {
    }
}
