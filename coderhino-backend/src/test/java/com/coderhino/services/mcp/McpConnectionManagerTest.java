package com.coderhino.services.mcp;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpConnectionManagerTest {
    private static final long TEST_INITIALIZE_TIMEOUT_MS = 50L;

    @Test
    void listToolsSurfacesProtocolStartupFailureWithStderrDiagnostics(@TempDir Path tempDir) throws Exception {
        var manager = new McpConnectionManager(50);
        var script = tempDir.resolve("stderr-only-mcp.sh");
        Files.writeString(script, "#!/bin/sh\necho 'missing display' >&2\nsleep 1\n");
        script.toFile().setExecutable(true);

        manager.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, TEST_INITIALIZE_TIMEOUT_MS));

        var tools = manager.listTools("playwright");
        var connection = awaitConnection(manager, "playwright");

        assertTrue(tools.isPresent());
        assertTrue(tools.get().isEmpty());
        assertTrue(connection.statusMessage().startsWith("protocol-startup-failed:"));
        assertTrue(connection.statusMessage().contains("stderr: missing display"));
    }

    @Test
    void listToolsLogsProtocolStartupFailureWithStderrDiagnostics(@TempDir Path tempDir) throws Exception {
        var appender = attachLogs();
        try {
            var manager = new McpConnectionManager(50);
            var script = tempDir.resolve("stderr-only-mcp.sh");
            Files.writeString(script, "#!/bin/sh\necho 'missing display' >&2\nsleep 1\n");
            script.toFile().setExecutable(true);

            manager.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, TEST_INITIALIZE_TIMEOUT_MS));

            manager.listTools("playwright");
            awaitConnection(manager, "playwright");

            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("MCP protocol failure for server 'playwright' during tools/list")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("stderr: missing display")));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void listToolsLogsProtocolStartupFailureWithoutStderrDiagnostics(@TempDir Path tempDir) throws Exception {
        var appender = attachLogs();
        try {
            var manager = new McpConnectionManager(50);
            var script = tempDir.resolve("silent-hanging-mcp.sh");
            Files.writeString(script, "#!/bin/sh\nsleep 1\n");
            script.toFile().setExecutable(true);

            manager.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, TEST_INITIALIZE_TIMEOUT_MS));

            manager.listTools("playwright");
            awaitConnection(manager, "playwright");

            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("MCP protocol failure for server 'playwright' during tools/list")));
            assertTrue(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("Did not observe any item or terminal signal within 50ms")));
            assertFalse(appender.list.stream().anyMatch(event -> event.getFormattedMessage().contains("| stderr:")));
        } finally {
            detachLogs(appender);
        }
    }

    @Test
    void listToolsMarksConnectionProtocolReadyAfterSuccessfulDiscovery(@TempDir Path tempDir) throws Exception {
        var manager = new McpConnectionManager(2_000);
        var script = tempDir.resolve("working-mcp.sh");
        Files.writeString(script, "#!/bin/sh\n"
            + "while IFS= read -r line; do\n"
            + "  id=$(printf '%s\n' \"$line\" | sed -n 's/.*\"id\":[[:space:]]*\\([^,}][^,}]*\\).*/\\1/p')\n"
            + "  case \"$line\" in\n"
            + "    *'\"method\":\"initialize\"'*)\n"
            + "      printf '{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}}}}\\n' \"$id\"\n"
            + "      ;;\n"
            + "    *'\"method\":\"notifications/initialized\"'*)\n"
            + "      ;;\n"
            + "    *'\"method\":\"tools/list\"'*)\n"
            + "      printf '{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"tools\":[{\"name\":\"browser_snapshot\",\"description\":\"Snapshot\",\"inputSchema\":{\"type\":\"object\"}}]}}\\n' \"$id\"\n"
            + "      ;;\n"
            + "  esac\n"
            + "done\n");
        script.toFile().setExecutable(true);

        manager.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, 30_000L));

        var tools = manager.listTools("playwright");
        var connection = awaitConnection(manager, "playwright");

        assertEquals(1, tools.orElseThrow().size());
        assertEquals("browser_snapshot", tools.orElseThrow().get(0).name());
        assertEquals("protocol-ready", connection.statusMessage());
        assertTrue(connection.connected());
    }

    @Test
    void connectMarksProcessStartedBeforeProtocolInitialization(@TempDir Path tempDir) throws Exception {
        var manager = new McpConnectionManager(50);
        var script = tempDir.resolve("idle-mcp.sh");
        Files.writeString(script, "#!/bin/sh\nsleep 1\n");
        script.toFile().setExecutable(true);

        manager.register(new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, 30_000L));

        var connection = manager.connect("playwright").orElseThrow();

        assertEquals("process-started", connection.statusMessage());
        assertTrue(connection.connected());
        manager.disconnect("playwright");
        assertFalse(manager.connection("playwright").orElseThrow().connected());
    }

    private static McpConnection awaitConnection(McpConnectionManager manager, String serverName) throws InterruptedException {
        McpConnection latest = manager.connection(serverName).orElseThrow();
        for (int i = 0; i < 20; i++) {
            latest = manager.connection(serverName).orElseThrow();
            if (latest.statusMessage().contains("stderr:") || "protocol-ready".equals(latest.statusMessage())) {
                return latest;
            }
            Thread.sleep(25);
        }
        return latest;
    }

    private static ListAppender<ILoggingEvent> attachLogs() {
        var logger = (Logger) LoggerFactory.getLogger(McpConnectionManager.class);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private static void detachLogs(ListAppender<ILoggingEvent> appender) {
        var logger = (Logger) LoggerFactory.getLogger(McpConnectionManager.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
