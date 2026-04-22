package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.UUID;

public final class ExitWorktreeTool implements ToolDefinition<ExitWorktreeTool.Input, ExitWorktreeTool.Output> {

    @Override
    public String name() {
        return "exit_worktree";
    }

    @Override
    public String description() {
        return "Deactivate the current git worktree context and return to the default working directory";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of());
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        UUID sessionId = context.bootstrapState().get().sessionRuntime().sessionId();
        String removed = EnterWorktreeTool.ACTIVE_WORKTREES.remove(sessionId);
        if (removed != null) {
            return new Output(removed, "exited");
        }
        return new Output(null, "no_active_worktree");
    }

    public record Input() {
    }

    public record Output(String previousPath, String status) {
    }
}
