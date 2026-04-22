package com.coderhino.services;

import com.coderhino.server.NoOpServerService;
import com.coderhino.server.ServerService;
import com.coderhino.services.analytics.AnalyticsService;
import com.coderhino.services.analytics.FeatureFlagService;
import com.coderhino.services.analytics.NoOpAnalyticsService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;
import com.coderhino.services.compact.CompactService;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.tasks.TaskService;

/**
 * Builder/factory that assembles a {@link ServiceRegistry} with overridable service slots.
 * <p>
 * Each service defaults to a NoOp or minimal implementation so callers only need
 * to override the services they care about.
 */
public final class ConfiguredServiceRegistry {

    private ConfiguredServiceRegistry() {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private McpConnectionManager mcpConnectionManager;
        private LspClientManager lspClientManager;
        private TaskService taskService;
        private CostTracker costTracker;
        private CompactService compactService;
        private AnalyticsService analyticsService;
        private FeatureFlagService featureFlagService;
        private ServerService serverService;

        private Builder() {
        }

        public Builder withMcpConnectionManager(McpConnectionManager mcpConnectionManager) {
            this.mcpConnectionManager = mcpConnectionManager;
            return this;
        }

        public Builder withLspClientManager(LspClientManager lspClientManager) {
            this.lspClientManager = lspClientManager;
            return this;
        }

        public Builder withTaskService(TaskService taskService) {
            this.taskService = taskService;
            return this;
        }

        public Builder withCostTracker(CostTracker costTracker) {
            this.costTracker = costTracker;
            return this;
        }

        public Builder withCompactService(CompactService compactService) {
            this.compactService = compactService;
            return this;
        }

        public Builder withAnalyticsService(AnalyticsService analyticsService) {
            this.analyticsService = analyticsService;
            return this;
        }

        public Builder withFeatureFlagService(FeatureFlagService featureFlagService) {
            this.featureFlagService = featureFlagService;
            return this;
        }

        public Builder withServerService(ServerService serverService) {
            this.serverService = serverService;
            return this;
        }

        /**
         * Build a {@link ServiceRegistry} using overrides supplied via {@code with*} methods.
         * Any slot not explicitly set defaults to a NoOp or minimal implementation.
         *
         * @return a fully wired ServiceRegistry
         */
        public ServiceRegistry build() {
            return new ServiceRegistry(
                mcpConnectionManager != null ? mcpConnectionManager : new McpConnectionManager(),
                lspClientManager != null ? lspClientManager : new LspClientManager(),
                taskService != null ? taskService : new TaskService(),
                costTracker != null ? costTracker : new CostTracker(),
                compactService != null ? compactService : new CompactService(),
                analyticsService != null ? analyticsService : new NoOpAnalyticsService(),
                featureFlagService != null ? featureFlagService : new NoOpFeatureFlagService(),
                serverService != null ? serverService : new NoOpServerService()
            );
        }
    }
}
