package com.coderhino.tools.builtin;

import com.coderhino.services.mcp.McpToolDescriptor;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.runtime.ToolMcpService;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Optional;

public final class MCPTool implements ToolDefinition<MCPTool.Input, String> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String name() {
        return "mcp";
    }

    @Override
    public String description() {
        return "Execute a tool provided by an MCP server";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "serverName", Map.of("type", "string"),
            "toolName", Map.of("type", "string"),
            "arguments", Map.of("type", "object")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.serverName() == null || input.serverName().isBlank()) {
            return PermissionResult.deny("serverName must not be blank.");
        }
        if (input.toolName() == null || input.toolName().isBlank()) {
            return PermissionResult.deny("toolName must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public String execute(Input input, ToolContext context) throws Exception {
        ToolMcpService mcp = context.services().mcp();

        Optional<McpToolDescriptor> foundTool = Optional.empty();
        var tools = mcp.listTools(input.serverName());
        if (tools.isPresent()) {
            foundTool = tools.get().stream()
                .filter(t -> t.name().equals(input.toolName()))
                .findFirst();
        }

        if (tools.isEmpty()) {
            return "MCP server not found: " + input.serverName();
        }

        if (foundTool.isEmpty()) {
            return "MCP tool not found: " + input.toolName() + " on server " + input.serverName();
        }

        var argumentsNode = input.arguments() != null
            ? objectMapper.valueToTree(input.arguments())
            : objectMapper.createObjectNode();

        var result = mcp.callTool(input.serverName(), input.toolName(), argumentsNode);
        if (result.isEmpty()) {
            return "MCP tool call returned no result";
        }
        return result.get();
    }

    public record Input(String serverName, String toolName, Map<String, Object> arguments) {
    }
}
