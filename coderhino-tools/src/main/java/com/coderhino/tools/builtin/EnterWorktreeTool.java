package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EnterWorktreeTool implements ToolDefinition<EnterWorktreeTool.Input, EnterWorktreeTool.Output> {

    static final ConcurrentHashMap<UUID, String> ACTIVE_WORKTREES = new ConcurrentHashMap<>();

    @Override
    public String name() {
        return "enter_worktree";
    }

    @Override
    public String description() {
        return "Activate a git worktree path for the current session, isolating subsequent operations to that worktree";
    }

    @Override
    public boolean isReadOnly() {
        return false;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "path", Map.of("type", "string", "description", "The absolute or relative path to the git worktree to activate")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.path() == null || input.path().isBlank()) {
            return PermissionResult.deny("path must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        UUID sessionId = context.bootstrapState().sessionId();
        String previous = ACTIVE_WORKTREES.put(sessionId, input.path());
        return new Output(input.path(), previous, "entered");
    }

    public static void clearWorktrees() {
        ACTIVE_WORKTREES.clear();
    }

    public static String getActiveWorktree(UUID sessionId) {
        return ACTIVE_WORKTREES.get(sessionId);
    }

    public record Input(String path) {
        public Input {
            if (path != null) path = path.strip();
        }
    }

    public record Output(String path, String previousPath, String status) {
    }
}
