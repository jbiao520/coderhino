package com.coderhino.services.mcp;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

public interface McpSession extends AutoCloseable {
    void initialize() throws Exception;

    List<McpToolDescriptor> listTools() throws Exception;

    List<McpResourceDescriptor> listResources() throws Exception;

    String readResource(String uri) throws Exception;

    String callTool(String toolName, JsonNode arguments) throws Exception;

    boolean ping() throws Exception;

    void subscribeResource(String uri) throws Exception;

    void unsubscribeResource(String uri) throws Exception;

    boolean hasStartedProcess();

    boolean isProcessAlive();

    Long processId();

    @Override
    void close();
}
