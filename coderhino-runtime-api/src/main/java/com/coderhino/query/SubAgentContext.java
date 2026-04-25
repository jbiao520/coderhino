package com.coderhino.query;

import com.coderhino.types.PermissionMode;

/**
 * Carries depth-guard context for recursive sub-agent sessions.
 */
public record SubAgentContext(
    PermissionMode permissionMode,
    int depth
) {
    public static final int MAX_DEPTH = 5;

    public boolean isTooDeep() {
        return depth >= MAX_DEPTH;
    }

    public SubAgentContext deeper() {
        return new SubAgentContext(permissionMode, depth + 1);
    }
}
