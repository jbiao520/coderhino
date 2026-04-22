package com.coderhino.tools.runtime;

import com.coderhino.services.mcp.McpResourceDescriptor;
import com.coderhino.services.mcp.McpToolDescriptor;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ToolMcpService {
    Collection<String> serverNames();

    Optional<List<McpToolDescriptor>> listTools(String serverName);

    Optional<List<McpResourceDescriptor>> listResources(String serverName);

    Optional<String> readResource(String serverName, String uri);

    Optional<String> callTool(String serverName, String toolName, JsonNode arguments);

    Optional<ResolvedTool> resolveTool(String qualifiedToolName);

    record ResolvedTool(String serverName, String toolName, McpToolDescriptor descriptor) {
    }
}
