package com.coderhino.tools.runtime;

public interface CommandServices extends ToolServices {
    CommandCostService commandCosts();

    CommandCompactService commandCompact();

    CommandServerService commandServer();

    PluginCommandService commandPlugins();

    CommandMcpConfigService mcpConfig();

    CommandCoordinatorService commandCoordinator();

    CommandVoiceService commandVoice();

    CommandSummaryService summary();
}
