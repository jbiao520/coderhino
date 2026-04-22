package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class ExitPlanModeTool implements ToolDefinition<ExitPlanModeTool.Input, ExitPlanModeTool.Output> {

    @Override
    public String name() {
        return "exit_plan_mode";
    }

    @Override
    public String description() {
        return "Exit plan mode, returning to the previous or specified permission mode";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "targetMode", Map.of("type", "string")
        ));
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        var previous = context.permissionMode();
        PermissionMode target;
        if (input.targetMode() != null && !input.targetMode().isBlank()) {
            target = PermissionMode.valueOf(input.targetMode().toUpperCase());
        } else {
            target = PermissionMode.DEFAULT;
        }
        context.bootstrapState().updatePermissionMode(target);
        return new Output(previous.name(), target.name(),
            "Exited plan mode → " + target.name() + " (was: " + previous.name() + ")");
    }

    public record Input(String targetMode) {
        public Input {
            if (targetMode != null) targetMode = targetMode.strip();
        }
    }

    public record Output(String previousMode, String currentMode, String message) {
    }
}
