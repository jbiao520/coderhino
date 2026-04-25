package com.coderhino.tools.runtime;

import com.coderhino.query.SubAgentContext;

public interface ToolAgentExecutor {
    SyncResult executeSync(Request request) throws Exception;

    AsyncResult executeAsync(Request request) throws Exception;

    record Request(
        String description,
        String prompt,
        String subagentType,
        String worktree,
        SubAgentContext subAgentContext
    ) {
    }

    record SyncResult(String text, String stopReason) {
    }

    record AsyncResult(String taskId, String summary, String status) {
    }
}
