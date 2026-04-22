package com.coderhino.tools.builtin;

import com.coderhino.services.mcp.McpResourceDescriptor;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ListMcpResourcesTool implements ToolDefinition<ListMcpResourcesTool.Input, List<ListMcpResourcesTool.Output>> {
    @Override
    public String name() {
        return "ListMcpResourcesTool";
    }

    @Override
    public String description() {
        return "List available resources from configured MCP servers";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "server", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null) {
            return PermissionResult.allow();
        }
        if (input.server() != null && input.server().isBlank()) {
            return PermissionResult.deny("server must not be blank when provided.");
        }
        return PermissionResult.allow();
    }

    @Override
    public List<Output> execute(Input input, ToolContext context) {
        var mcp = context.services().mcp();
        var targetServer = input == null || input.server() == null ? null : input.server().trim();

        if (targetServer != null && !targetServer.isEmpty()) {
            var resources = mcp.listResources(targetServer)
                .orElseThrow(() -> new IllegalArgumentException("Server not found: " + targetServer));
            return resources.stream().map(resource -> new Output(resource, targetServer)).toList();
        }

        var outputs = new ArrayList<Output>();
        for (var serverName : mcp.serverNames()) {
            var resources = mcp.listResources(serverName).orElse(List.of());
            for (McpResourceDescriptor resource : resources) {
                outputs.add(new Output(resource, serverName));
            }
        }
        return outputs;
    }

    public record Input(String server) {
    }

    public record Output(String uri, String name, String mimeType, String description, String server) {
        public Output(McpResourceDescriptor descriptor, String server) {
            this(
                descriptor.uri(),
                descriptor.name(),
                descriptor.mimeType(),
                descriptor.description(),
                server
            );
        }
    }
}
