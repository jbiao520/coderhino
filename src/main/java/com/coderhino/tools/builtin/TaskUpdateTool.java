package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class TaskUpdateTool implements ToolDefinition<TaskUpdateTool.Input, TaskUpdateTool.Output> {
    @Override
    public String name() {
        return "TaskUpdate";
    }

    @Override
    public String description() {
        return "Update the status of a local task";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "taskId", Map.of("type", "string"),
            "status", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.taskId() == null || input.taskId().isBlank()) {
            return PermissionResult.deny("taskId must not be blank.");
        }
        if (input.status() == null || input.status().isBlank()) {
            return PermissionResult.deny("status must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var updated = context.services().tasks().update(input.taskId().trim(), input.status().trim()).orElse(null);
        if (updated == null) {
            return new Output(false, input.taskId().trim(), null);
        }
        return new Output(true, updated.id().toString(), updated.status());
    }

    public record Input(String taskId, String status) {
    }

    public record Output(boolean success, String taskId, String status) {
    }
}
