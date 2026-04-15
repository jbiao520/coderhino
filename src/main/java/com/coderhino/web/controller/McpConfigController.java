package com.coderhino.web.controller;

import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.mcp.McpConfigDocument;
import com.coderhino.web.mcp.McpConfigPersistenceService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/mcp-config")
public class McpConfigController {

    private final McpConfigPersistenceService persistenceService;

    public McpConfigController(McpConfigPersistenceService persistenceService) {
        this.persistenceService = persistenceService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<McpConfigDocument> getConfig() {
        return ResponseEntity.ok(persistenceService.load());
    }

    @PutMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> updateConfig(@RequestBody McpConfigDocument updates) {
        try {
            return ResponseEntity.ok(persistenceService.save(updates));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }
}
