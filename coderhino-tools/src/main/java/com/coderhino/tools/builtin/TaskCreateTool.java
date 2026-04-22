package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class TaskCreateTool implements ToolDefinition<TaskCreateTool.Input, TaskCreateTool.Output> {
    @Override
    public String name() {
        return "TaskCreate";
    }

    @Override
    public String description() {
        return "Create a local task record";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "description", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.description() == null || input.description().isBlank()) {
            return PermissionResult.deny("description must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var created = context.services().tasks().create(input.description().trim());
        return new Output(created.id().toString(), created.description(), created.status());
    }

    public record Input(String description) {
    }

    public record Output(String taskId, String description, String status) {
    }
}
