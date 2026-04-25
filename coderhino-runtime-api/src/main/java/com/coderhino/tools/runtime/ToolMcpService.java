package com.coderhino.tools.runtime;

import com.coderhino.services.mcp.McpConnection;
import com.coderhino.services.mcp.McpResourceDescriptor;
import com.coderhino.services.mcp.McpServerDefinition;
import com.coderhino.services.mcp.McpToolDescriptor;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToolMcpService {
    Collection<String> serverNames();

    Collection<McpServerDefinition> definitions();

    List<McpConnection> connections();

    void register(McpServerDefinition definition);

    void unregister(String name);
    Optional<McpConnection> connect(String serverName);
    Optional<McpConnection> disconnect(String serverName);
    Optional<McpConnection> reconnect(String serverName);
    Optional<McpConnection> enable(String serverName);
    Optional<McpConnection> disable(String serverName);
    Optional<McpConnection> connection(String serverName);
    Optional<List<McpToolDescriptor>> listTools(String serverName);
    Optional<List<McpResourceDescriptor>> listResources(String serverName);
    Optional<String> readResource(String serverName, String uri);
    Optional<String> callTool(String serverName, String toolName, JsonNode arguments);
    Optional<ResolvedTool> resolveTool(String qualifiedToolName);

    record ResolvedTool(String serverName, String toolName, McpToolDescriptor descriptor) {
    }
}
