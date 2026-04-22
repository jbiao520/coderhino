package com.coderhino.tools;

import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

public interface ToolDefinition<I, O> {
    String name();

    String description();

    ToolInputSchema inputSchema();

    default boolean isEnabled() {
        return true;
    }

    default boolean isReadOnly() {
        return false;
    }

    default PermissionResult validate(I input, ToolContext context) {
        return PermissionResult.allow();
    }

    O execute(I input, ToolContext context) throws Exception;
}
