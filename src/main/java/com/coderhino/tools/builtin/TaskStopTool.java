package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class TaskStopTool implements ToolDefinition<TaskStopTool.Input, TaskStopTool.Output> {
    @Override
    public String name() {
        return "TaskStop";
    }

    @Override
    public String description() {
        return "Stop a local task by ID";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "taskId", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.taskId() == null || input.taskId().isBlank()) {
            return PermissionResult.deny("taskId must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var stopped = context.services().tasks().stop(input.taskId().trim()).orElse(null);
        if (stopped == null) {
            return new Output(false, input.taskId().trim(), null);
        }
        return new Output(true, stopped.id().toString(), stopped.status());
    }

    public record Input(String taskId) {
    }

    public record Output(boolean success, String taskId, String status) {
    }
}
