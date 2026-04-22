package com.coderhino.tools.builtin;

import com.coderhino.context.ContextCollector;
import com.coderhino.coordinator.DefaultCoordinatorService;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.query.SubAgentContext;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.services.tasks.TaskOriginContext;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
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

    private Output executeSync(Input input, ToolContext context) {
        // 1. Depth guard
        var parentCtx = context.subAgentContext();
        int depth = parentCtx != null ? parentCtx.depth() : 0;
        if (parentCtx != null && parentCtx.isTooDeep()) {
            return new Output(null,
                "Agent refused: maximum recursion depth (%d) reached".formatted(
                    SubAgentContext.MAX_DEPTH),
                "failed");
        }

        // 2. Build sub-agent context
        var services = context.services() != null
            ? context.services()
            : ServiceRegistry.createDefault();
        var subAgentCtx = new SubAgentContext(context.permissionMode(), services, depth + 1);

        // 3. Build a fresh QueryEngine for this sub-agent
        var toolRegistry = ToolRegistry.createDefault();
        var modelClient = ModelClientFactory.create("sonnet");
        var engine = new QueryEngine(
            toolRegistry, modelClient,
            new PermissionChecker(),
            new ContextCollector(),
            services,
            subAgentCtx);

        // 4. Run the sub-agent session
        var queryResult = engine.execute(context.bootstrapState(), input.prompt());
        return new Output(null, queryResult.text(), queryResult.stopReason().name().toLowerCase());
    }

    private Output executeAsync(Input input, ToolContext context) {
        // Depth guard
        var parentCtx = context.subAgentContext();
        int depth = parentCtx != null ? parentCtx.depth() : 0;
        if (parentCtx != null && parentCtx.isTooDeep()) {
            return new Output(null,
                "Agent refused: maximum recursion depth (%d) reached".formatted(
                    SubAgentContext.MAX_DEPTH),
                "failed");
        }

        var services = context.services() != null
            ? context.services()
            : ServiceRegistry.createDefault();
        var subAgentCtx = new SubAgentContext(context.permissionMode(), services, depth + 1);

        var taskDescription = "[agent] %s (%s)".formatted(
            input.description(),
            input.subagentType() != null ? input.subagentType() : "build");

        // Capture only primitives/immutables in lambda — avoid capturing ToolContext
        final String prompt = input.prompt();
        final var bootstrapState = context.bootstrapState();
        final var capturedServices = services;
        final var capturedSubAgentCtx = subAgentCtx;

        TaskService tasks = services.tasks();
        var origin = TaskOriginContext.current();
        var record = tasks.submit(
            taskDescription,
            origin != null ? origin.projectId() : null,
            origin != null ? origin.sessionId() : null,
            () -> {
            var toolRegistry = ToolRegistry.createDefault();
            var modelClient = ModelClientFactory.create("sonnet");
            var engine = new QueryEngine(
                toolRegistry, modelClient,
                new PermissionChecker(),
                new ContextCollector(),
                capturedServices,
                capturedSubAgentCtx);
                var result = engine.execute(bootstrapState, prompt);
                return result.text();
            }
        );

        return new Output(record.id().toString(),
            "Agent task queued (taskId=%s)".formatted(record.id()),
            "running");
    }

    private boolean isCoordinatorEnabled(ToolContext context) {
        if (context.services() == null) {
            return false;
        }
        var coordinator = context.services().coordinatorService();
        if (coordinator instanceof DefaultCoordinatorService dcs) {
            return dcs.isEnabled();
        }
        return coordinator != null && coordinator.isMultiAgent();
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
