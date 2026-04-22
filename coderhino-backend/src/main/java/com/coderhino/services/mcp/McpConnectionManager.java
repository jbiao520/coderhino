package com.coderhino.services.mcp;

import com.coderhino.tools.runtime.ToolMcpService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public final class McpConnectionManager implements ToolMcpService {
    private static final Logger log = LoggerFactory.getLogger(McpConnectionManager.class);
    private static final int DEFAULT_MAX_RECONNECT_ATTEMPTS = 3;
    private static final long RECONNECT_BASE_DELAY_MS = 100;
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 5_000;
    private static final int MAX_STDERR_DIAGNOSTIC_CHARS = 2_000;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, McpServerDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, McpConnection> connections = new LinkedHashMap<>();
    private final Map<String, McpSession> sessions = new LinkedHashMap<>();
    private final Map<String, Integer> reconnectAttempts = new LinkedHashMap<>();
    private final Map<String, List<McpToolDescriptor>> discoveredTools = new LinkedHashMap<>();
    private final Map<String, StderrTailBuffer> stderrDiagnostics = new LinkedHashMap<>();
    private final long requestTimeoutMillis;

    public McpConnectionManager() {
        this(DEFAULT_REQUEST_TIMEOUT_MS);
    }

    McpConnectionManager(long requestTimeoutMillis) {
        this.requestTimeoutMillis = requestTimeoutMillis;
    }

    public void register(McpServerDefinition definition) {
        definitions.put(definition.name(), definition);
        connections.putIfAbsent(definition.name(), new McpConnection(definition.name(), false, null, "registered", null, List.of()));
        invalidateDiscoveredTools(definition.name());
    }

    public Optional<McpConnection> enable(String serverName) {
        var definition = definitions.get(serverName);
        if (definition == null) {
            return Optional.empty();
        }
        definitions.put(serverName, new McpServerDefinition(
            definition.name(),
            definition.command(),
            definition.arguments(),
            definition.environment(),
            true,
            definition.initializeTimeoutMs()
        ));
        invalidateDiscoveredTools(serverName);
        var enabled = new McpConnection(serverName, false, Instant.now(), "enabled", null, buildCommandLine(definition));
        connections.put(serverName, enabled);
        return Optional.of(enabled);
    }

    public Optional<McpConnection> disable(String serverName) {
        var definition = definitions.get(serverName);
        if (definition == null) {
            return Optional.empty();
        }
        disconnect(serverName);
        definitions.put(serverName, new McpServerDefinition(
            definition.name(),
            definition.command(),
            definition.arguments(),
            definition.environment(),
            false,
            definition.initializeTimeoutMs()
        ));
        invalidateDiscoveredTools(serverName);
        var disabled = new McpConnection(serverName, false, Instant.now(), "disabled", null, buildCommandLine(definition));
        connections.put(serverName, disabled);
        return Optional.of(disabled);
    }

    public void unregister(String name) {
        definitions.remove(name);
        connections.remove(name);
        discoveredTools.remove(name);
    }

    public Collection<McpServerDefinition> definitions() {
        return definitions.values();
    }

    public List<McpConnection> connections() {
        return new ArrayList<>(connections.values());
    }

    public Optional<McpConnection> connect(String serverName) {
        var definition = definitions.get(serverName);
        if (definition == null) {
            return Optional.empty();
        }
        if (!definition.enabled()) {
            var disabled = new McpConnection(serverName, false, Instant.now(), "disabled", null, buildCommandLine(definition));
            connections.put(serverName, disabled);
            return Optional.of(disabled);
        }

        try {
            invalidateDiscoveredTools(serverName);
            var diagnostics = new StderrTailBuffer(MAX_STDERR_DIAGNOSTIC_CHARS);
            stderrDiagnostics.put(serverName, diagnostics);
            var session = createSession(definition, diagnostics::append);
            sessions.put(serverName, session);
            var connection = new McpConnection(
                serverName,
                true,
                Instant.now(),
                "process-started",
                session.processId(),
                buildCommandLine(definition)
            );
            connections.put(serverName, connection);
            return Optional.of(connection);
        } catch (Exception exception) {
            var failed = new McpConnection(serverName, false, Instant.now(), "failed: " + McpFailureTranslator.message(exception), null, buildCommandLine(definition));
            connections.put(serverName, failed);
            return Optional.of(failed);
        }
    }

    public Optional<McpConnection> disconnect(String serverName) {
        var session = sessions.remove(serverName);
        if (session != null) {
            session.close();
        }
        stderrDiagnostics.remove(serverName);
        var existing = connections.get(serverName);
        if (existing == null) {
            return Optional.empty();
        }
        var disconnected = new McpConnection(serverName, false, existing.lastStartedAt(), "disconnected", null, existing.commandLine());
        connections.put(serverName, disconnected);
        return Optional.of(disconnected);
    }

    public Optional<McpConnection> reconnect(String serverName) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }
        disconnect(serverName);
        return connect(serverName);
    }

    @Override
    public Optional<List<McpToolDescriptor>> listTools(String serverName) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }

        var cached = discoveredTools.get(serverName);
        if (cached != null) {
            return Optional.of(List.copyOf(cached));
        }

        if (!sessions.containsKey(serverName)) {
            connect(serverName);
        }

        var session = sessions.get(serverName);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var tools = session.listTools();
            discoveredTools.put(serverName, List.copyOf(tools));
            updateConnectionStatus(serverName, "protocol-ready", isProcessAlive(serverName));
            return Optional.of(tools);
        } catch (Exception exception) {
            var failureDetails = buildFailureDetails(serverName, exception);
            logProtocolFailure(serverName, "tools/list", failureDetails, exception);
            updateConnectionStatus(serverName, buildProtocolFailureStatus("protocol-startup-failed", failureDetails), isProcessAlive(serverName));
            return Optional.of(List.of());
        }
    }

    @Override
    public Optional<List<McpResourceDescriptor>> listResources(String serverName) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(serverName)) {
            connect(serverName);
        }

        var session = sessions.get(serverName);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var resources = session.listResources();
            updateConnectionStatus(serverName, "protocol-ready", isProcessAlive(serverName));
            return Optional.of(resources);
        } catch (Exception exception) {
            var failureDetails = buildFailureDetails(serverName, exception);
            logProtocolFailure(serverName, "resources/list", failureDetails, exception);
            updateConnectionStatus(serverName, buildProtocolFailureStatus("protocol-startup-failed", failureDetails), isProcessAlive(serverName));
            return Optional.of(List.of());
        }
    }

    @Override
    public Optional<String> readResource(String serverName, String uri) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }

        var session = ensureSession(serverName);
        if (session == null) {
            return Optional.of("No MCP session available.");
        }

        try {
            var result = session.readResource(uri);
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "resource-read",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(result);
        } catch (Exception exception) {
            var retrySession = reconnectSession(serverName);
            if (retrySession != null) {
                try {
                    var result = retrySession.readResource(uri);
                    var existing = connections.get(serverName);
                    if (existing != null) {
                        connections.put(serverName, new McpConnection(
                            existing.serverName(),
                            existing.connected(),
                            existing.lastStartedAt(),
                            "resource-read",
                            existing.processId(),
                            existing.commandLine()
                        ));
                    }
                    return Optional.of(result);
                } catch (Exception retryException) {
                    exception = retryException;
                }
            }
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    false,
                    existing.lastStartedAt(),
                    "resource-error: " + McpFailureTranslator.message(exception),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of("Resource read failed: " + McpFailureTranslator.message(exception));
        }
    }

    public Optional<String> callTool(String serverName, String toolName) {
        return callTool(serverName, toolName, objectMapper.createObjectNode());
    }

    @Override
    public Optional<String> callTool(String serverName, String toolName, JsonNode arguments) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }

        var session = ensureSession(serverName);
        if (session == null) {
            return Optional.of("No MCP session available.");
        }

        try {
            var result = session.callTool(toolName, arguments);
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "tool-called",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(result);
        } catch (Exception exception) {
            var retrySession = reconnectSession(serverName);
            if (retrySession != null) {
                try {
                    var result = retrySession.callTool(toolName, arguments);
                    var existing = connections.get(serverName);
                    if (existing != null) {
                        connections.put(serverName, new McpConnection(
                            existing.serverName(),
                            existing.connected(),
                            existing.lastStartedAt(),
                            "tool-called",
                            existing.processId(),
                            existing.commandLine()
                        ));
                    }
                    return Optional.of(result);
                } catch (Exception retryException) {
                    exception = retryException;
                }
            }
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    false,
                    existing.lastStartedAt(),
                    "tool-error: " + McpFailureTranslator.message(exception),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of("Tool call failed: " + McpFailureTranslator.message(exception));
        }
    }

    public Optional<Boolean> ping(String serverName) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }
        var session = ensureSession(serverName);
        if (session == null) {
            return Optional.of(false);
        }
        try {
            return Optional.of(session.ping());
        } catch (Exception e) {
            return Optional.of(false);
        }
    }

    public Optional<McpConnection> connection(String serverName) {
        return Optional.ofNullable(connections.get(serverName));
    }

    public Optional<String> subscribeResource(String serverName, String uri) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }
        var session = ensureSession(serverName);
        if (session == null) {
            return Optional.of("No MCP session available.");
        }
        try {
            session.subscribeResource(uri);
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "subscribed:" + uri,
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of("subscribed:" + uri);
        } catch (Exception e) {
            return Optional.of("subscribe-error: " + e.getMessage());
        }
    }

    public Optional<String> unsubscribeResource(String serverName, String uri) {
        if (!definitions.containsKey(serverName)) {
            return Optional.empty();
        }
        var session = ensureSession(serverName);
        if (session == null) {
            return Optional.of("No MCP session available.");
        }
        try {
            session.unsubscribeResource(uri);
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "unsubscribed:" + uri,
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of("unsubscribed:" + uri);
        } catch (Exception e) {
            return Optional.of("unsubscribe-error: " + e.getMessage());
        }
    }

    private McpSession ensureSession(String serverName) {
        var session = sessions.get(serverName);
        if (session != null) {
            if (!session.hasStartedProcess() || session.isProcessAlive()) {
                return session;
            }
            reconnectWithBackoff(serverName);
            return sessions.get(serverName);
        }
        connect(serverName);
        return sessions.get(serverName);
    }

    private McpSession reconnectSession(String serverName) {
        disconnect(serverName);
        connect(serverName);
        reconnectAttempts.remove(serverName);
        return sessions.get(serverName);
    }

    public Optional<McpConnection> reconnectWithBackoff(String serverName) {
        int attempts = reconnectAttempts.getOrDefault(serverName, 0);
        if (attempts >= DEFAULT_MAX_RECONNECT_ATTEMPTS) {
            var existing = connections.get(serverName);
            if (existing != null) {
                connections.put(serverName, new McpConnection(
                    existing.serverName(), false, existing.lastStartedAt(),
                    "reconnect-exhausted", null, existing.commandLine()
                ));
            }
            return connections.containsKey(serverName)
                ? Optional.of(connections.get(serverName))
                : Optional.empty();
        }
        reconnectAttempts.put(serverName, attempts + 1);
        long delay = RECONNECT_BASE_DELAY_MS * (1L << attempts);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
        disconnect(serverName);
        return connect(serverName);
    }

    private List<String> buildCommandLine(McpServerDefinition definition) {
        var command = new ArrayList<String>();
        command.add(definition.command());
        command.addAll(definition.arguments());
        return command;
    }

    private void updateConnectionStatus(String serverName, String statusMessage, boolean connected) {
        var existing = connections.get(serverName);
        if (existing == null) {
            return;
        }
        connections.put(serverName, new McpConnection(
            existing.serverName(),
            connected,
            existing.lastStartedAt(),
            statusMessage,
            processId(serverName, existing.processId()),
            existing.commandLine()
        ));
    }

    private boolean isProcessAlive(String serverName) {
        var session = sessions.get(serverName);
        return session != null && session.isProcessAlive();
    }

    private void logProtocolFailure(String serverName, String operation, String failureDetails, Exception exception) {
        log.error("MCP protocol failure for server '{}' during {}: {}", serverName, operation, failureDetails, exception);
    }

    private String buildProtocolFailureStatus(String prefix, String failureDetails) {
        return new StringBuilder(prefix).append(": ").append(failureDetails).toString();
    }

    private String buildFailureDetails(String serverName, Exception exception) {
        var message = new StringBuilder(McpFailureTranslator.message(exception));
        var tail = awaitDiagnosticTail(serverName);
        if (!tail.isBlank()) {
            message.append(" | stderr: ").append(tail);
        }
        return message.toString();
    }

    private String awaitDiagnosticTail(String serverName) {
        var diagnostics = stderrDiagnostics.get(serverName);
        if (diagnostics == null) {
            return "";
        }
        for (int i = 0; i < 30; i++) {
            var tail = diagnostics.snapshotSingleLine();
            if (!tail.isBlank()) {
                return tail;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        return diagnostics.snapshotSingleLine();
    }

    @Override
    public Optional<ResolvedTool> resolveTool(String qualifiedToolName) {
        var parsed = McpToolName.parse(qualifiedToolName);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }

        for (var definition : definitions.values()) {
            if (!McpToolName.normalize(definition.name()).equals(parsed.get().serverName())) {
                continue;
            }

            var tools = listTools(definition.name()).orElse(List.of());
            for (var tool : tools) {
                if (McpToolName.normalize(tool.name()).equals(parsed.get().toolName())) {
                    return Optional.of(new ResolvedTool(definition.name(), tool.name(), tool));
                }
            }
            return Optional.empty();
        }

        return Optional.empty();
    }

    @Override
    public Collection<String> serverNames() {
        return List.copyOf(definitions.keySet());
    }

    private void invalidateDiscoveredTools(String serverName) {
        discoveredTools.remove(serverName);
    }

    private McpSession createSession(McpServerDefinition definition, Consumer<String> stderrConsumer) {
        return new McpSdkSession(definition, objectMapper, requestTimeoutMillis, stderrConsumer);
    }

    private Long processId(String serverName, Long existingProcessId) {
        var session = sessions.get(serverName);
        if (session == null) {
            return existingProcessId;
        }
        return session.processId() == null ? existingProcessId : session.processId();
    }

    private static final class StderrTailBuffer {
        private final int maxChars;
        private final StringBuilder content = new StringBuilder();

        private StderrTailBuffer(int maxChars) {
            this.maxChars = maxChars;
        }

        synchronized void append(String text) {
            if (text == null || text.isEmpty()) {
                return;
            }
            content.append(text);
            if (content.length() > maxChars) {
                content.delete(0, content.length() - maxChars);
            }
        }

        synchronized String snapshotSingleLine() {
            return content.toString().replaceAll("\\s+", " ").trim();
        }
    }

}
