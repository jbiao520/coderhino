package com.coderhino.tools;

import com.coderhino.query.ToolSchema;
import com.coderhino.services.mcp.McpToolDescriptor;
import com.coderhino.services.mcp.McpToolName;
import com.coderhino.tools.builtin.AgentTool;
import com.coderhino.tools.builtin.AskUserQuestionTool;
import com.coderhino.tools.builtin.BashTool;
import com.coderhino.tools.builtin.BriefTool;
import com.coderhino.tools.builtin.ConfigTool;
import com.coderhino.tools.builtin.CronCreateTool;
import com.coderhino.tools.builtin.CronDeleteTool;
import com.coderhino.tools.builtin.CronListTool;
import com.coderhino.tools.builtin.EnterPlanModeTool;
import com.coderhino.tools.builtin.EnterWorktreeTool;
import com.coderhino.tools.builtin.ExitPlanModeTool;
import com.coderhino.tools.builtin.ExitWorktreeTool;
import com.coderhino.tools.builtin.FileEditTool;
import com.coderhino.tools.builtin.FileReadTool;
import com.coderhino.tools.builtin.FileWriteTool;
import com.coderhino.tools.builtin.GlobTool;
import com.coderhino.tools.builtin.NotebookEditTool;
import com.coderhino.tools.builtin.GrepTool;
import com.coderhino.tools.builtin.LspTool;
import com.coderhino.tools.builtin.ListMcpResourcesTool;
import com.coderhino.tools.builtin.MCPTool;
import com.coderhino.tools.builtin.REPLTool;
import com.coderhino.tools.builtin.ReadMcpResourceTool;
import com.coderhino.tools.builtin.RemoteTriggerTool;
import com.coderhino.tools.builtin.SendMessageTool;
import com.coderhino.tools.builtin.SkillTool;
import com.coderhino.tools.builtin.SleepTool;
import com.coderhino.tools.builtin.SyntheticOutputTool;
import com.coderhino.tools.builtin.TaskCreateTool;
import com.coderhino.tools.builtin.TaskGetTool;
import com.coderhino.tools.builtin.TaskListTool;
import com.coderhino.tools.builtin.TaskOutputTool;
import com.coderhino.tools.builtin.TaskStopTool;
import com.coderhino.tools.builtin.TaskUpdateTool;
import com.coderhino.tools.builtin.TeamCreateTool;
import com.coderhino.tools.builtin.TeamDeleteTool;
import com.coderhino.tools.builtin.TodoCreateTool;
import com.coderhino.tools.builtin.TodoWriteTool;
import com.coderhino.tools.builtin.ToolSearchTool;
import com.coderhino.tools.builtin.WebSearchTool;
import com.coderhino.tools.builtin.WebFetchTool;
import com.coderhino.tools.runtime.ToolMcpService;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class ToolRegistry {
    private final Map<String, ToolDefinition<?, ?>> tools;

    public ToolRegistry(Collection<? extends ToolDefinition<?, ?>> tools) {
        this.tools = new LinkedHashMap<>();
        tools.forEach(tool -> this.tools.put(tool.name(), tool));
    }

    public static ToolRegistry createDefault() {
        var tools = java.util.List.<ToolDefinition<?, ?>>of(
            new AgentTool(),
            new AskUserQuestionTool(),
            new BashTool(),
            new BriefTool(),
            new ConfigTool(),
            new CronCreateTool(),
            new CronDeleteTool(),
            new CronListTool(),
            new EnterPlanModeTool(),
            new EnterWorktreeTool(),
            new ExitPlanModeTool(),
            new ExitWorktreeTool(),
            new FileReadTool(),
            new FileWriteTool(),
            new FileEditTool(),
            new NotebookEditTool(),
            new GlobTool(),
            new GrepTool(),
            new LspTool(),
            new ListMcpResourcesTool(),
            new MCPTool(),
            new ReadMcpResourceTool(),
            new REPLTool(),
            new RemoteTriggerTool(),
            new SendMessageTool(),
            new SkillTool(),
            new TaskCreateTool(),
            new TaskGetTool(),
            new TaskListTool(),
            new TaskOutputTool(),
            new TaskStopTool(),
            new TaskUpdateTool(),
            new TeamCreateTool(),
            new TeamDeleteTool(),
            new TodoCreateTool(),
            new TodoWriteTool(),
            new WebFetchTool(),
            new WebSearchTool()
        );

        var preliminary = new ToolRegistry(tools);
        var allTools = new java.util.ArrayList<>(tools);
        allTools.add(new SleepTool());
        allTools.add(new SyntheticOutputTool());
        allTools.add(new ToolSearchTool(preliminary));
        return new ToolRegistry(allTools);
    }

    public static ToolRegistry createReadOnlyDefault() {
        return createDefault().readOnly();
    }

    public Optional<ToolDefinition<?, ?>> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public Collection<ToolDefinition<?, ?>> all() {
        return tools.values();
    }

    public ToolRegistry filtered(List<String> allowedToolNames) {
        if (allowedToolNames == null || allowedToolNames.isEmpty()) {
            return this;
        }

        Set<String> allowed = new LinkedHashSet<>(allowedToolNames);
        var filteredTools = new java.util.ArrayList<ToolDefinition<?, ?>>();
        boolean includeToolSearch = false;

        for (var tool : tools.values()) {
            if (!allowed.contains(tool.name())) {
                continue;
            }
            if (tool instanceof ToolSearchTool) {
                includeToolSearch = true;
                continue;
            }
            filteredTools.add(tool);
        }

        var filteredRegistry = new ToolRegistry(filteredTools);
        if (!includeToolSearch) {
            return filteredRegistry;
        }

        var toolsWithSearch = new java.util.ArrayList<>(filteredTools);
        toolsWithSearch.add(new ToolSearchTool(filteredRegistry));
        return new ToolRegistry(toolsWithSearch);
    }

    public ToolRegistry readOnly() {
        var readOnlyTools = tools.values().stream()
            .filter(ToolDefinition::isReadOnly)
            .toList();
        return new ToolRegistry(readOnlyTools);
    }

    public ToolRegistry with(ToolDefinition<?, ?> tool) {
        var next = new java.util.ArrayList<>(tools.values());
        next.add(tool);
        return new ToolRegistry(next);
    }

    public ToolRegistry withAll(Collection<? extends ToolDefinition<?, ?>> additionalTools) {
        if (additionalTools == null || additionalTools.isEmpty()) {
            return this;
        }
        var next = new java.util.ArrayList<>(tools.values());
        next.addAll(additionalTools);
        return new ToolRegistry(next);
    }

    public java.util.List<ToolSchema> toSchemas() {
        return tools.values().stream()
            .filter(ToolDefinition::isEnabled)
            .map(t -> new ToolSchema(t.name(), t.description(), serializeInputSchema(t.inputSchema())))
            .collect(Collectors.toList());
    }

    public java.util.List<ToolSchema> toSchemas(ToolMcpService mcpConnectionManager) {
        var schemas = new java.util.ArrayList<>(toSchemas());
        if (mcpConnectionManager == null || !tools.containsKey("mcp")) {
            return schemas;
        }

        var publishedNames = new LinkedHashSet<String>();
        schemas.stream().map(ToolSchema::name).forEach(publishedNames::add);

        for (var serverName : mcpConnectionManager.serverNames()) {
            for (var descriptor : mcpConnectionManager.listTools(serverName).orElse(java.util.List.of())) {
                toDynamicMcpSchema(serverName, descriptor)
                    .filter(schema -> publishedNames.add(schema.name()))
                    .ifPresent(schemas::add);
            }
        }

        return schemas;
    }

    private static Map<String, Object> serializeInputSchema(com.coderhino.types.ToolInputSchema schema) {
        var map = new LinkedHashMap<String, Object>();
        map.put("type", schema.type());
        map.put("properties", schema.properties());
        return map;
    }

    private static Optional<ToolSchema> toDynamicMcpSchema(String serverName, McpToolDescriptor descriptor) {
        if (descriptor == null || descriptor.name() == null || descriptor.name().isBlank()) {
            return Optional.empty();
        }

        var inputSchema = descriptor.inputSchema().isEmpty()
            ? Map.<String, Object>of("type", "object", "properties", Map.of())
            : sanitizeDynamicInputSchema(descriptor.inputSchema());
        if (inputSchema == null) {
            return Optional.empty();
        }

        return Optional.of(new ToolSchema(
            McpToolName.qualified(serverName, descriptor.name()),
            descriptor.description() == null ? "" : descriptor.description(),
            inputSchema
        ));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sanitizeDynamicInputSchema(Map<String, Object> inputSchema) {
        Object type = inputSchema.getOrDefault("type", "object");
        if (!(type instanceof String typeString) || !"object".equals(typeString)) {
            return null;
        }

        Object properties = inputSchema.get("properties");
        if (properties != null && !(properties instanceof Map<?, ?>)) {
            return null;
        }

        var sanitized = new LinkedHashMap<String, Object>(inputSchema);
        sanitized.putIfAbsent("properties", Map.of());
        return sanitized;
    }
}
