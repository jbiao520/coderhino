package com.coderhino.tools.runtime;

import com.coderhino.services.analytics.FeatureFlagService;
import com.coderhino.services.cron.CronScheduler;
import com.coderhino.services.trigger.RemoteTriggerService;

public interface ToolServices {
    ToolMcpService mcp();

    ToolLspService lsp();

    ToolTaskService tasks();

    FeatureFlagService featureFlags();

    CronScheduler cronScheduler();

    RemoteTriggerService remoteTriggerService();
}
