package com.coderhino.query;

import com.coderhino.services.ServiceRegistry;
import com.coderhino.types.PermissionMode;

/**
 * Carries depth-guard context for recursive sub-agent sessions.
 * Passed through ToolContext so nested AgentTool invocations can detect
 * runaway recursion before spawning another QueryEngine.
 */
public record SubAgentContext(
    PermissionMode permissionMode,
    ServiceRegistry serviceRegistry,
    int depth
) {
    public static final int MAX_DEPTH = 5;

    /** Returns true when the current depth is at or beyond the maximum allowed. */
    public boolean isTooDeep() {
        return depth >= MAX_DEPTH;
    }

    /** Returns a new SubAgentContext one level deeper, preserving all other fields. */
    public SubAgentContext deeper() {
        return new SubAgentContext(permissionMode, serviceRegistry, depth + 1);
    }
}
