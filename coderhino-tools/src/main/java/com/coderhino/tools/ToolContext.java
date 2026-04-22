package com.coderhino.tools;

import com.coderhino.query.SubAgentContext;
import com.coderhino.tools.runtime.ToolAgentExecutor;
import com.coderhino.tools.runtime.ToolBootstrapState;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.tools.runtime.ToolServices;
import com.coderhino.types.PermissionMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ToolContext(
    ToolBootstrapState bootstrapState,
    PermissionMode permissionMode,
    ToolServices services,
    SubAgentContext subAgentContext,
    ToolCommandRegistry commandRegistry,
    ToolAgentExecutor agentExecutor
) {
    private static final ThreadLocal<List<String>> progressHolder = new ThreadLocal<>();

    public void reportProgress(String message) {
        var messages = progressHolder.get();
        if (messages == null) {
            messages = Collections.synchronizedList(new ArrayList<>());
            progressHolder.set(messages);
        }
        messages.add(message);
    }

    public static List<String> drainProgressMessages() {
        var messages = progressHolder.get();
        if (messages == null) {
            return List.of();
        }
        var copy = List.copyOf(messages);
        messages.clear();
        return copy;
    }

    public static List<String> getProgressMessages() {
        var messages = progressHolder.get();
        return messages != null ? List.copyOf(messages) : List.of();
    }
}
