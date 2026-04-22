package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;

public final class EnterPlanModeTool implements ToolDefinition<EnterPlanModeTool.Input, EnterPlanModeTool.Output> {

    @Override
    public String name() {
        return "enter_plan_mode";
    }

    @Override
    public String description() {
        return "Switch the session into plan mode, restricting tools to read-only operations";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of());
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
        context.bootstrapState().updatePermissionMode(PermissionMode.PLAN);
        return new Output(previous.name(), PermissionMode.PLAN.name(),
            "Entered plan mode (was: " + previous.name() + ")");
    }

    public record Input() {
    }

    public record Output(String previousMode, String currentMode, String message) {
    }
}
