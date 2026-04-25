package com.coderhino.tools.runtime;

import com.coderhino.types.PermissionMode;

import java.util.UUID;

public interface ToolBootstrapState {
    String cwd();

    UUID sessionId();

    void updatePermissionMode(PermissionMode permissionMode);
}
