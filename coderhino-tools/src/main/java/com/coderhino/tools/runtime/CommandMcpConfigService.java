package com.coderhino.tools.runtime;

import com.coderhino.services.mcp.McpServerDefinition;

import java.io.IOException;
import java.nio.file.Path;

public interface CommandMcpConfigService {
    void addServer(Path cwd, McpServerDefinition definition) throws IOException;

    void setServerEnabled(Path cwd, String serverName, boolean enabled) throws IOException;
}
