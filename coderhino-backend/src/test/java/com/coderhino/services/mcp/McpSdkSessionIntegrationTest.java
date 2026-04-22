package com.coderhino.services.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpSdkSessionIntegrationTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void sdkSessionListsToolsAndCallsTool(@TempDir Path tempDir) throws Exception {
        var script = tempDir.resolve("sdk-mcp-tools.sh");
        Files.writeString(script, "#!/bin/sh\n"
            + "while IFS= read -r line; do\n"
            + "  id=$(printf '%s\n' \"$line\" | sed -n 's/.*\"id\":[[:space:]]*\\([^,}][^,}]*\\).*/\\1/p')\n"
            + "  case \"$line\" in\n"
            + "    *'\"method\":\"initialize\"'*)\n"
            + "      printf '{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{},\"resources\":{}}}}\\n' \"$id\"\n"
            + "      ;;\n"
            + "    *'\"method\":\"notifications/initialized\"'*)\n"
            + "      ;;\n"
            + "    *'\"method\":\"tools/list\"'*)\n"
            + "      printf '{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"tools\":[{\"name\":\"read_file\",\"description\":\"Read file\",\"inputSchema\":{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}},\"annotations\":{\"readOnlyHint\":true}}]}}\\n' \"$id\"\n"
            + "      ;;\n"
            + "    *'\"method\":\"tools/call\"'*)\n"
            + "      printf '{\"jsonrpc\":\"2.0\",\"id\":%s,\"result\":{\"content\":[{\"type\":\"text\",\"text\":\"ok\"}]}}\\n' \"$id\"\n"
            + "      ;;\n"
            + "  esac\n"
            + "done\n");
        script.toFile().setExecutable(true);

        var stderr = new ArrayList<String>();
        var session = new McpSdkSession(
            new McpServerDefinition("filesystem", script.toString(), List.of(), java.util.Map.of(), true, 5_000L),
            objectMapper,
            5_000L,
            stderr::add
        );
        try {
            var tools = session.listTools();
            var result = session.callTool("read_file", objectMapper.createObjectNode().put("path", "README.md"));

            assertEquals(1, tools.size());
            assertEquals("read_file", tools.get(0).name());
            assertTrue(tools.get(0).readOnlyHint());
            assertEquals("ok", result);
        } finally {
            session.close();
        }
    }

    @Test
    void sdkSessionForwardsStderrLines(@TempDir Path tempDir) throws Exception {
        var script = tempDir.resolve("sdk-mcp-stderr.sh");
        Files.writeString(script, "#!/bin/sh\necho 'missing display' >&2\nsleep 2\n");
        script.toFile().setExecutable(true);

        var stderr = new ArrayList<String>();
        var session = new McpSdkSession(
            new McpServerDefinition("playwright", script.toString(), List.of(), java.util.Map.of(), true, 200L),
            objectMapper,
            200L,
            stderr::add
        );
        try {
            try {
                session.listTools();
            } catch (Exception ignored) {
            }

            for (int i = 0; i < 20 && stderr.stream().noneMatch(line -> line.contains("missing display")); i++) {
                Thread.sleep(25);
            }
            assertTrue(stderr.stream().anyMatch(line -> line.contains("missing display")));
        } finally {
            session.close();
        }
    }
}
