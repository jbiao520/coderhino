package com.coderhino.services;

import com.coderhino.coordinator.CoordinatorService;
import com.coderhino.coordinator.DefaultCoordinatorService;
import com.coderhino.coordinator.NoOpCoordinatorService;
import com.coderhino.plugins.FileSystemPluginService;
import com.coderhino.plugins.NoOpPluginService;
import com.coderhino.plugins.PluginAutoUpdater;
import com.coderhino.plugins.PluginComponentLoader;
import com.coderhino.plugins.PluginScanningService;
import com.coderhino.plugins.PluginServerWirer;
import com.coderhino.plugins.PluginService;
import com.coderhino.services.analytics.AnalyticsService;
import com.coderhino.services.analytics.DefaultAnalyticsService;
import com.coderhino.services.analytics.FeatureFlagService;
import com.coderhino.services.analytics.NoOpAnalyticsService;
import com.coderhino.services.analytics.EnvFeatureFlagService;
import com.coderhino.services.analytics.NoOpFeatureFlagService;
import com.coderhino.services.compact.CompactService;
import com.coderhino.services.config.McpConfigLoader;
import com.coderhino.services.cron.CronScheduler;
import com.coderhino.services.cron.DefaultCronScheduler;
import com.coderhino.services.cron.NoOpCronScheduler;
import com.coderhino.services.lsp.LspClientManager;
import com.coderhino.services.lsp.LspServerDefinition;
import com.coderhino.services.mcp.McpConnectionManager;
import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.services.proactive.DefaultProactiveService;
import com.coderhino.services.proactive.NoOpProactiveService;
import com.coderhino.services.proactive.ProactiveService;
import com.coderhino.services.trigger.DefaultRemoteTriggerService;
import com.coderhino.services.trigger.NoOpRemoteTriggerService;
import com.coderhino.services.trigger.RemoteTriggerService;
import com.coderhino.services.voice.DefaultVoiceService;
import com.coderhino.services.voice.NoOpVoiceService;
import com.coderhino.services.voice.VoiceService;
import com.coderhino.server.LocalServerService;
import com.coderhino.server.NoOpServerService;
import com.coderhino.server.ServerService;
import com.coderhino.skills.FileSystemSkillService;
import com.coderhino.skills.NoOpSkillService;
import com.coderhino.skills.SkillService;
import com.coderhino.services.tasks.TaskService;
import com.coderhino.services.settings.LocalSettingsSyncService;
import com.coderhino.services.settings.NoOpSettingsSyncService;
import com.coderhino.services.settings.SettingsSyncService;
import com.coderhino.services.memory.LocalTeamMemoryService;
import com.coderhino.services.memory.NoOpTeamMemoryService;
import com.coderhino.services.memory.TeamMemoryService;
import com.coderhino.services.summary.FileChangeTracker;
import com.coderhino.services.summary.FileChangeSummaryFormatter;
import com.coderhino.services.summary.SessionEndSummary;
import com.coderhino.tools.runtime.CommandCompactResult;
import com.coderhino.tools.runtime.CommandCostService;
import com.coderhino.tools.runtime.CommandMcpConfigService;
import com.coderhino.tools.runtime.CommandServices;
import com.coderhino.tools.runtime.PluginCommandService;
import com.coderhino.tools.runtime.ToolServices;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class ServiceRegistry implements CommandServices {
    private final McpConnectionManager mcpConnectionManager;
    private final LspClientManager lspClientManager;
    private final TaskService taskService;
    private final CostTracker costTracker;
    private final CompactService compactService;
    private final AnalyticsService analyticsService;
    private final FeatureFlagService featureFlagService;
    private final ServerService serverService;
    private final PluginService pluginService;
    private final SkillService skillService;
    private final CoordinatorService coordinatorService;
    private final ProactiveService proactiveService;
    private final CronScheduler cronScheduler;
    private final RemoteTriggerService remoteTriggerService;
    private final VoiceService voiceService;
    private final SettingsSyncService settingsSyncService;
    private final TeamMemoryService teamMemoryService;
    private final FileChangeTracker fileChangeTracker;
    private final PluginCommandService pluginCommandService;
    private final CommandMcpConfigService mcpConfigService;

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService) {
        this(mcpConnectionManager, lspClientManager, taskService, new CostTracker());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, new CompactService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, new NoOpAnalyticsService(), new NoOpFeatureFlagService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, new NoOpServerService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, serverService, new NoOpPluginService(), new NoOpSkillService(), new NoOpCoordinatorService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService, PluginService pluginService, SkillService skillService, CoordinatorService coordinatorService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, serverService, pluginService, skillService, coordinatorService, new NoOpProactiveService(), new NoOpCronScheduler(), new NoOpRemoteTriggerService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService, PluginService pluginService, SkillService skillService, CoordinatorService coordinatorService, ProactiveService proactiveService, CronScheduler cronScheduler, RemoteTriggerService remoteTriggerService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, serverService, pluginService, skillService, coordinatorService, proactiveService, cronScheduler, remoteTriggerService, new NoOpVoiceService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService, PluginService pluginService, SkillService skillService, CoordinatorService coordinatorService, ProactiveService proactiveService, CronScheduler cronScheduler, RemoteTriggerService remoteTriggerService, VoiceService voiceService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, serverService, pluginService, skillService, coordinatorService, proactiveService, cronScheduler, remoteTriggerService, voiceService, new NoOpSettingsSyncService(), new NoOpTeamMemoryService());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService, PluginService pluginService, SkillService skillService, CoordinatorService coordinatorService, ProactiveService proactiveService, CronScheduler cronScheduler, RemoteTriggerService remoteTriggerService, VoiceService voiceService, SettingsSyncService settingsSyncService, TeamMemoryService teamMemoryService) {
        this(mcpConnectionManager, lspClientManager, taskService, costTracker, compactService, analyticsService, featureFlagService, serverService, pluginService, skillService, coordinatorService, proactiveService, cronScheduler, remoteTriggerService, voiceService, settingsSyncService, teamMemoryService, new FileChangeTracker());
    }

    public ServiceRegistry(McpConnectionManager mcpConnectionManager, LspClientManager lspClientManager, TaskService taskService, CostTracker costTracker, CompactService compactService, AnalyticsService analyticsService, FeatureFlagService featureFlagService, ServerService serverService, PluginService pluginService, SkillService skillService, CoordinatorService coordinatorService, ProactiveService proactiveService, CronScheduler cronScheduler, RemoteTriggerService remoteTriggerService, VoiceService voiceService, SettingsSyncService settingsSyncService, TeamMemoryService teamMemoryService, FileChangeTracker fileChangeTracker) {
        this.mcpConnectionManager = mcpConnectionManager;
        this.lspClientManager = lspClientManager;
        this.taskService = taskService;
        this.costTracker = costTracker;
        this.compactService = compactService;
        this.analyticsService = analyticsService;
        this.featureFlagService = featureFlagService;
        this.serverService = serverService;
        this.pluginService = pluginService;
        this.skillService = skillService;
        this.coordinatorService = coordinatorService;
        this.proactiveService = proactiveService;
        this.cronScheduler = cronScheduler;
        this.remoteTriggerService = remoteTriggerService;
        this.voiceService = voiceService;
        this.settingsSyncService = settingsSyncService;
        this.teamMemoryService = teamMemoryService;
        this.fileChangeTracker = fileChangeTracker;
        this.pluginCommandService = new CommandPluginServiceAdapter(pluginService, skillService, mcpConnectionManager, lspClientManager);
        this.mcpConfigService = new com.coderhino.services.config.McpConfigWriter();
    }

    public static ServiceRegistry createDefault() {
        return createDefault(Path.of("").toAbsolutePath().normalize());
    }

    public static ServiceRegistry createDefault(Path cwd) {
        var mcp = new McpConnectionManager();
        var configLoader = new McpConfigLoader();
        var loaded = configLoader.load(cwd);
        if (loaded.isEmpty()) {
            mcp.register(new McpServerDefinition("filesystem", "npx", List.of("-y", "@modelcontextprotocol/server-filesystem", "."), Map.of(), false, 30_000L));
        } else {
            loaded.forEach(mcp::register);
        }

        var lsp = new LspClientManager();
        lsp.register(new LspServerDefinition("java", "jdtls", List.of(), false));
        lsp.register(new LspServerDefinition("typescript", "typescript-language-server", List.of("--stdio"), false));
        lsp.register(new LspServerDefinition("python", "python3", List.of("-c", "import time; time.sleep(5)"), true));

        var tasks = new TaskService(cwd.resolve(".claudecode-tasks.json"));
        var costs = new CostTracker(cwd.resolve(".claudecode-costs.json"));
        var analytics = new DefaultAnalyticsService(cwd.resolve(".coderhino").resolve("analytics-events.jsonl"));
        var featureFlags = new EnvFeatureFlagService();

        // HISTORY_SNIP: use aggressive compaction boundary when flag is enabled
        var compact = featureFlags.isEnabled(com.coderhino.services.analytics.FeatureFlag.HISTORY_SNIP)
                ? new CompactService(com.coderhino.types.CompactBoundary.aggressive())
                : new CompactService();

        // COORDINATOR_MODE: start in MULTI_AGENT mode when flag is enabled
        var coordinator = featureFlags.isEnabled(com.coderhino.services.analytics.FeatureFlag.COORDINATOR_MODE)
                ? new DefaultCoordinatorService(com.coderhino.coordinator.CoordinatorMode.MULTI_AGENT)
                : new DefaultCoordinatorService();

        // KAIROS: start proactive service pre-enabled when flag is set
        var proactive = featureFlags.isEnabled(com.coderhino.services.analytics.FeatureFlag.KAIROS)
                ? new DefaultProactiveService(true)
                : new DefaultProactiveService();

        // DAEMON: start local server in DAEMON mode when flag is set
        var server = new LocalServerService();
        if (featureFlags.isEnabled(com.coderhino.services.analytics.FeatureFlag.DAEMON)) {
            server.start(com.coderhino.server.ServerMode.DAEMON, 0);
        }

        // AGENT_TRACERS: enable tracing in analytics service when flag is set
        if (featureFlags.isEnabled(com.coderhino.services.analytics.FeatureFlag.AGENT_TRACERS)) {
            analytics.trackEvent("agent_tracers_enabled", "startup");
        }

        FileSystemPluginService plugins = new FileSystemPluginService(cwd.resolve(".claudecode-plugins"), new com.fasterxml.jackson.databind.ObjectMapper(), analytics);
        var skillService = new FileSystemSkillService(cwd.resolve(".claudecode-skills"));

        var pluginScanner = new PluginScanningService(plugins);
        var pluginLoader = new PluginComponentLoader(skillService);
        for (var manifest : pluginScanner.scanDefaultDirectory()) {
            pluginLoader.loadComponents(manifest);
        }

        var serverWirer = new PluginServerWirer(mcp, lsp);
        for (var manifest : plugins.listManifests()) {
            serverWirer.wireServers(manifest);
        }

        var autoUpdater = new PluginAutoUpdater(plugins, analytics);
        autoUpdater.startBackgroundCheck();

        return new ServiceRegistry(mcp, lsp, tasks, costs, compact, analytics, featureFlags, server, plugins, skillService, coordinator, proactive, new DefaultCronScheduler(featureFlags), new DefaultRemoteTriggerService(), new DefaultVoiceService(featureFlags), new LocalSettingsSyncService(cwd.resolve(".coderhino").resolve("settings-sync.json")), new LocalTeamMemoryService(cwd.resolve(".coderhino").resolve("team-memory")));
    }

    @Override
    public McpConnectionManager mcp() {
        return mcpConnectionManager;
    }

    @Override
    public LspClientManager lsp() {
        return lspClientManager;
    }

    @Override
    public TaskService tasks() {
        return taskService;
    }

    @Override
    public CommandCostService commandCosts() {
        return new CommandCostService() {
            @Override
            public void reset() {
                costTracker.reset();
            }

            @Override
            public Map<String, CommandCostService.ModelUsage> allModelUsage() {
                return costTracker.allModelUsage().entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> new CommandCostService.ModelUsage(
                            entry.getValue().inputTokens(),
                            entry.getValue().outputTokens(),
                            entry.getValue().costUsd()
                        ),
                        (left, right) -> right,
                        java.util.LinkedHashMap::new
                    ));
            }
        };
    }

    public CostTracker costTracker() {
        return costTracker;
    }

    @Override
    public com.coderhino.tools.runtime.CommandCompactService commandCompact() {
        return (messages, customInstructions) -> {
            var result = compactService.compactManual(messages, customInstructions);
            return new CommandCompactResult(result.compactedMessages(), result.originalMessageCount(), result.wasCompacted());
        };
    }

    public AnalyticsService analytics() {
        return analyticsService;
    }

    @Override
    public FeatureFlagService featureFlags() {
        return featureFlagService;
    }

    @Override
    public com.coderhino.tools.runtime.CommandServerService commandServer() {
        return serverService::isRunning;
    }

    public ServerService serverService() {
        return serverService;
    }

    @Override
    public PluginCommandService commandPlugins() {
        return pluginCommandService;
    }

    public PluginService pluginService() {
        return pluginService;
    }

    public SkillService skillService() {
        return skillService;
    }

    @Override
    public com.coderhino.tools.runtime.CommandCoordinatorService commandCoordinator() {
        return new com.coderhino.tools.runtime.CommandCoordinatorService() {
            @Override
            public String currentMode() {
                return coordinatorService.currentMode().name();
            }

            @Override
            public boolean isMultiAgent() {
                return coordinatorService.isMultiAgent();
            }
        };
    }

    public ProactiveService proactiveService() {
        return proactiveService;
    }

    @Override
    public CronScheduler cronScheduler() {
        return cronScheduler;
    }

    @Override
    public RemoteTriggerService remoteTriggerService() {
        return remoteTriggerService;
    }

    @Override
    public com.coderhino.tools.runtime.CommandVoiceService commandVoice() {
        return new com.coderhino.tools.runtime.CommandVoiceService() {
            @Override
            public void enable() {
                voiceService.enable();
            }

            @Override
            public void disable() {
                voiceService.disable();
            }

            @Override
            public boolean isEnabled() {
                return voiceService.isEnabled();
            }

            @Override
            public String currentMode() {
                return voiceService.currentMode().name();
            }
        };
    }

    public SettingsSyncService settingsSyncService() {
        return settingsSyncService;
    }

    public TeamMemoryService teamMemoryService() {
        return teamMemoryService;
    }

    public FileChangeTracker fileChangeTracker() {
        return fileChangeTracker;
    }

    @Override
    public com.coderhino.tools.runtime.CommandSummaryService summary() {
        return sessionId -> {
            var summary = new SessionEndSummary(fileChangeTracker).buildSummary(sessionId);
            return summary.totalChanges() > 0
                ? java.util.Optional.of(FileChangeSummaryFormatter.format(summary))
                : java.util.Optional.empty();
        };
    }

    @Override
    public CommandMcpConfigService mcpConfig() {
        return mcpConfigService;
    }
}
