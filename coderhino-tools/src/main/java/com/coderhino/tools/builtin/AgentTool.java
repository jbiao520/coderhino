package com.coderhino.tools.builtin;

import com.coderhino.query.SubAgentContext;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.runtime.ToolAgentExecutor;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.Set;

public final class AgentTool implements ToolDefinition<AgentTool.Input, AgentTool.Output> {

    private static final Set<String> VALID_SUBAGENT_TYPES = Set.of(
        "explore", "librarian", "oracle", "build"
    );

    @Override
    public String name() {
        return "agent";
    }

    @Override
    public String description() {
        return "Spawn a sub-agent to execute a task synchronously or asynchronously";
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "description", Map.of("type", "string"),
            "prompt", Map.of("type", "string"),
            "subagentType", Map.of("type", "string"),
            "runInBackground", Map.of("type", "boolean"),
            "worktree", Map.of("type", "string")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.description() == null || input.description().isBlank()) {
            return PermissionResult.deny("description must not be blank.");
        }
        if (input.prompt() == null || input.prompt().isBlank()) {
            return PermissionResult.deny("prompt must not be blank.");
        }
        if (input.subagentType() != null && !input.subagentType().isBlank()
            && !VALID_SUBAGENT_TYPES.contains(input.subagentType())) {
            return PermissionResult.deny(
                "Unknown subagentType '%s'. Valid values: %s".formatted(
                    input.subagentType(), VALID_SUBAGENT_TYPES));
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) throws Exception {
        if (Boolean.TRUE.equals(input.runInBackground())) {
            return executeAsync(input, context);
        } else {
            return executeSync(input, context);
        }
    }

    private Output executeSync(Input input, ToolContext context) throws Exception {
        var parentCtx = context.subAgentContext();
        int depth = parentCtx != null ? parentCtx.depth() : 0;
        if (parentCtx != null && parentCtx.isTooDeep()) {
            return new Output(null,
                "Agent refused: maximum recursion depth (%d) reached".formatted(
                    SubAgentContext.MAX_DEPTH),
                "failed");
        }

        var request = new ToolAgentExecutor.Request(
            input.description(),
            input.prompt(),
            input.subagentType(),
            input.worktree(),
            new SubAgentContext(context.permissionMode(), depth + 1)
        );
        var result = context.agentExecutor().executeSync(request);
        return new Output(null, result.text(), result.stopReason());
    }

    private Output executeAsync(Input input, ToolContext context) throws Exception {
        var parentCtx = context.subAgentContext();
        int depth = parentCtx != null ? parentCtx.depth() : 0;
        if (parentCtx != null && parentCtx.isTooDeep()) {
            return new Output(null,
                "Agent refused: maximum recursion depth (%d) reached".formatted(
                    SubAgentContext.MAX_DEPTH),
                "failed");
        }

        var request = new ToolAgentExecutor.Request(
            input.description(),
            input.prompt(),
            input.subagentType(),
            input.worktree(),
            new SubAgentContext(context.permissionMode(), depth + 1)
        );
        var result = context.agentExecutor().executeAsync(request);
        return new Output(result.taskId(), result.summary(), result.status());
    }

    public record Input(
        String description,
        String prompt,
        String subagentType,
        Boolean runInBackground,
        String worktree
    ) {
        public Input {
            if (description != null) {
                description = description.strip();
            }
            if (prompt != null) {
                prompt = prompt.strip();
            }
            if (subagentType != null) {
                subagentType = subagentType.strip();
            }
            if (worktree != null) {
                worktree = worktree.strip();
            }
        }
    }

    public record Output(String taskId, String summary, String status) {
    }
}
