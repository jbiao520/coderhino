package com.coderhino.web.controller;

import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.mcp.McpConfigDocument;
import com.coderhino.web.mcp.McpConfigPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.http.HttpStatus;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpConfigControllerTest {

    @Test
    void getConfigReturnsDefaultDocumentWhenFileMissing(@TempDir Path tempDir) {
        var service = new McpConfigPersistenceService(tempDir.resolve(".mcp.json"));
        var controller = new McpConfigController(service);

        var response = controller.getConfig();

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().getContent().contains("\"mcpServers\""));
    }

    @Test
    void updateConfigPersistsNormalizedJson(@TempDir Path tempDir) throws Exception {
        var file = tempDir.resolve(".mcp.json");
        var service = new McpConfigPersistenceService(file);
        var controller = new McpConfigController(service);

        var response = controller.updateConfig(new McpConfigDocument("{\"mcpServers\":{\"filesystem\":{\"command\":\"npx\"}}}"));

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertTrue(Files.exists(file));
        var stored = Files.readString(file);
        assertTrue(stored.contains("\"filesystem\""));
        assertTrue(stored.contains("\"command\" : \"npx\"") || stored.contains("\"command\": \"npx\""));
    }

    @Test
    void updateConfigRejectsInvalidJson(@TempDir Path tempDir) {
        var service = new McpConfigPersistenceService(tempDir.resolve(".mcp.json"));
        var controller = new McpConfigController(service);

        var response = controller.updateConfig(new McpConfigDocument("not-json"));

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        var body = assertInstanceOf(ErrorResponse.class, response.getBody());
        assertNotNull(body.getError());
    }
}
