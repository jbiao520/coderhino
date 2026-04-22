package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class ToolSearchTool implements ToolDefinition<ToolSearchTool.Input, List<String>> {
    private final ToolRegistry registry;

    public ToolSearchTool(ToolRegistry registry) {
        this.registry = registry;
    }

    @Override
    public String name() {
        return "tool_search";
    }

    @Override
    public String description() {
        return "List available tools matching a query, or list all tools if no query is provided";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "query", Map.of("type", "string", "description", "Optional search query to filter tool names")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        return PermissionResult.allow();
    }

    @Override
    public List<String> execute(Input input, ToolContext context) throws Exception {
        var query = input.query();
        var allTools = registry.all();
        var result = new ArrayList<String>();

        if (query == null || query.isBlank()) {
            for (var tool : allTools) {
                result.add(tool.name() + ": " + tool.description());
            }
        } else {
            var lowerQuery = query.toLowerCase();
            for (var tool : allTools) {
                if (tool.name().toLowerCase().contains(lowerQuery)
                    || tool.description().toLowerCase().contains(lowerQuery)) {
                    result.add(tool.name() + ": " + tool.description());
                }
            }
        }

        return result;
    }

    public record Input(String query) {
    }
}
