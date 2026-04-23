package com.coderhino.query;

import com.coderhino.context.ContextCollector;
import com.coderhino.permissions.PermissionChecker;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.tools.ToolRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class TestQueryEngines {
    private TestQueryEngines() {
    }

    public static QueryEngine withMaxToolIterations(ModelClient modelClient, ServiceRegistry serviceRegistry, int maxToolIterations,
                                                    String customSystemPrompt, String appendSystemPrompt) {
        return new QueryEngine(
            ToolRegistry.createDefault(),
            modelClient,
            new PermissionChecker(),
            new ContextCollector(),
            serviceRegistry,
            new ObjectMapper(),
            maxToolIterations,
            0.0,
            null,
            customSystemPrompt,
            appendSystemPrompt
        );
    }
}
