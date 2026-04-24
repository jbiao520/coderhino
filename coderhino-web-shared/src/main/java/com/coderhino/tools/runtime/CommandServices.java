package com.coderhino.tools.runtime;

public interface CommandServices {
    CommandCostService commandCosts();

    CommandCompactService commandCompact();

    CommandServerService commandServer();

    PluginCommandService commandPlugins();

    CommandMcpConfigService mcpConfig();

    CommandCoordinatorService commandCoordinator();

    CommandVoiceService commandVoice();

    CommandSummaryService summary();

    ToolMcpService mcp();

    ToolLspService lsp();

    ToolTaskService tasks();
}
