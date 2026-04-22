package com.coderhino.tools;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.query.SubAgentContext;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.BootstrapState;
import com.coderhino.types.PermissionMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public record ToolContext(BootstrapState bootstrapState, PermissionMode permissionMode, ServiceRegistry services, SubAgentContext subAgentContext, CommandRegistry commandRegistry) {
    private static final ThreadLocal<List<String>> progressHolder = new ThreadLocal<>();

    public ToolContext(BootstrapState bootstrapState, PermissionMode permissionMode) {
        this(bootstrapState, permissionMode, ServiceRegistry.createDefault(Path.of(bootstrapState.get().cwd())), null, CommandRegistry.createDefault(Path.of(bootstrapState.get().cwd())));
    }

    public ToolContext(BootstrapState bootstrapState, PermissionMode permissionMode, ServiceRegistry services) {
        this(bootstrapState, permissionMode, services, null, CommandRegistry.createDefault(Path.of(bootstrapState.get().cwd())));
    }

    public ToolContext(BootstrapState bootstrapState, PermissionMode permissionMode, ServiceRegistry services, SubAgentContext subAgentContext) {
        this(bootstrapState, permissionMode, services, subAgentContext, CommandRegistry.createDefault(Path.of(bootstrapState.get().cwd())));
    }

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
