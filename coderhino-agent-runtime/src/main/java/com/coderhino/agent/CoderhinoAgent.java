package com.coderhino.agent;

import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.query.ModelClient;
import com.coderhino.query.ModelClientFactory;
import com.coderhino.query.QueryEngine;
import com.coderhino.query.QueryEventSink;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.types.Message;
import com.coderhino.types.PermissionMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class CoderhinoAgent {
    private final AgentConfig config;
    private final BootstrapState managedState;

    private CoderhinoAgent(AgentConfig config) {
        this.config = config;
        this.managedState = config.bootstrapState() == null ? createBootstrapState(config) : config.bootstrapState();
    }

    public static Builder builder() {
        return new Builder();
    }

    public AgentResult run(String input) {
        return run(new AgentRequest(input));
    }

    public AgentResult run(AgentRequest request) {
        Objects.requireNonNull(request, "request");
        var state = request.bootstrapState() == null ? managedState : request.bootstrapState();
        var engine = createQueryEngine();
        var visibleInput = request.visibleInput() == null ? request.input() : request.visibleInput();
        var sink = request.eventSink() == null ? config.eventSink() : request.eventSink();
        var result = sink == null
            ? engine.execute(state, request.input(), visibleInput, NoOpAgentQueryEventSink.INSTANCE)
            : engine.execute(state, request.input(), visibleInput, sink);
        return new AgentResult(result.text(), result.stopReason(), result.iterationsUsed(), result.usage(), state.get(), state);
    }

    private enum NoOpAgentQueryEventSink implements QueryEventSink {
        INSTANCE;

        @Override public void onTextChunk(String chunk) {}
        @Override public void onStatus(String message) {}
        @Override public void onToolCall(String toolName, String toolUseId, String argumentsJson) {}
        @Override public void onToolResult(String toolName, String toolUseId, String result) {}
        @Override public void onUsage(long inputTokens, long outputTokens, long cacheCreationTokens, long cacheReadTokens) {}
        @Override public void onError(String error) {}
        @Override public void onCompleted(String finalText) {}
    }

    public AppState state() {
        return managedState.get();
    }

    public BootstrapState bootstrapState() {
        return managedState;
    }

    public AgentConfig config() {
        return config;
    }

    private QueryEngine createQueryEngine() {
        return new QueryEngine(
            config.toolRegistry(),
            config.modelClient(),
            config.permissionChecker(),
            new ContextCollector(),
            config.serviceRegistry(),
            config.customSystemPrompt(),
            config.appendSystemPrompt(),
            config.maxToolIterations(),
            config.maxBudgetUsd(),
            config.commandRegistry()
        );
    }

    private static BootstrapState createBootstrapState(AgentConfig config) {
        return new BootstrapState(new AppState(
            false,
            config.model(),
            config.cwd().toString(),
            false,
            true,
            config.permissionMode(),
            0.0,
            SessionRuntime.create(),
            List.of()
        ));
    }

    public record AgentRequest(String input, String visibleInput, QueryEventSink eventSink, BootstrapState bootstrapState) {
        public AgentRequest(String input) {
            this(input, input, null, null);
        }

        public AgentRequest {
            if (input == null || input.isBlank()) {
                throw new IllegalArgumentException("input is required");
            }
        }
    }

    public record AgentResult(
        String finalText,
        com.coderhino.query.QueryResult.StopReason stopReason,
        int iterationCount,
        com.coderhino.query.ModelResponse.Usage usage,
        AppState state,
        BootstrapState bootstrapState
    ) {
    }

    public record AgentConfig(
        ModelClient modelClient,
        String model,
        Path cwd,
        PermissionMode permissionMode,
        ToolRegistry toolRegistry,
        ServiceRegistry serviceRegistry,
        PermissionChecker permissionChecker,
        QueryEventSink eventSink,
        String customSystemPrompt,
        String appendSystemPrompt,
        int maxToolIterations,
        double maxBudgetUsd,
        BootstrapState bootstrapState,
        ToolCommandRegistry commandRegistry
    ) {
    }

    public static final class Builder {
        private ModelClient modelClient;
        private String model = "sonnet";
        private Path cwd = Path.of("").toAbsolutePath().normalize();
        private PermissionMode permissionMode = PermissionMode.DEFAULT;
        private ToolRegistry toolRegistry;
        private ServiceRegistry serviceRegistry;
        private PermissionChecker permissionChecker = new PermissionChecker();
        private QueryEventSink eventSink;
        private String customSystemPrompt;
        private String appendSystemPrompt;
        private int maxToolIterations = 200;
        private double maxBudgetUsd = 0.0;
        private BootstrapState bootstrapState;
        private ToolCommandRegistry commandRegistry;
        private final List<ToolDefinition<?, ?>> customTools = new ArrayList<>();

        public Builder modelClient(ModelClient modelClient) {
            this.modelClient = modelClient;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder cwd(Path cwd) {
            this.cwd = cwd == null ? this.cwd : cwd.toAbsolutePath().normalize();
            return this;
        }

        public Builder permissionMode(PermissionMode permissionMode) {
            this.permissionMode = permissionMode == null ? this.permissionMode : permissionMode;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder enabledBuiltInTools(List<String> toolNames) {
            this.toolRegistry = ToolRegistry.createDefault().filtered(toolNames);
            return this;
        }

        public Builder addTool(ToolDefinition<?, ?> tool) {
            this.customTools.add(Objects.requireNonNull(tool, "tool"));
            return this;
        }

        public Builder serviceRegistry(ServiceRegistry serviceRegistry) {
            this.serviceRegistry = serviceRegistry;
            return this;
        }

        public Builder permissionChecker(PermissionChecker permissionChecker) {
            this.permissionChecker = permissionChecker == null ? this.permissionChecker : permissionChecker;
            return this;
        }

        public Builder eventSink(QueryEventSink eventSink) {
            this.eventSink = eventSink;
            return this;
        }

        public Builder customSystemPrompt(String customSystemPrompt) {
            this.customSystemPrompt = customSystemPrompt;
            return this;
        }

        public Builder appendSystemPrompt(String appendSystemPrompt) {
            this.appendSystemPrompt = appendSystemPrompt;
            return this;
        }

        public Builder maxToolIterations(int maxToolIterations) {
            this.maxToolIterations = maxToolIterations;
            return this;
        }

        public Builder maxBudgetUsd(double maxBudgetUsd) {
            this.maxBudgetUsd = maxBudgetUsd;
            return this;
        }

        public Builder bootstrapState(BootstrapState bootstrapState) {
            this.bootstrapState = bootstrapState;
            return this;
        }

        public Builder commandRegistry(ToolCommandRegistry commandRegistry) {
            this.commandRegistry = commandRegistry;
            return this;
        }

        public CoderhinoAgent build() {
            var resolvedServiceRegistry = serviceRegistry == null ? ServiceRegistry.createEmbeddedDefault(cwd) : serviceRegistry;
            var resolvedToolRegistry = toolRegistry == null ? ToolRegistry.createReadOnlyDefault() : toolRegistry;
            resolvedToolRegistry = resolvedToolRegistry.withAll(customTools);
            var resolvedModelClient = modelClient == null ? ModelClientFactory.create(model) : modelClient;
            var config = new AgentConfig(
                resolvedModelClient,
                model,
                cwd,
                permissionMode,
                resolvedToolRegistry,
                resolvedServiceRegistry,
                permissionChecker,
                eventSink,
                customSystemPrompt,
                appendSystemPrompt,
                maxToolIterations,
                maxBudgetUsd,
                bootstrapState,
                commandRegistry
            );
            return new CoderhinoAgent(config);
        }
    }
}
