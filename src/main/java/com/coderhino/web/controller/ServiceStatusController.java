package com.coderhino.web.controller;

import com.coderhino.services.ServiceRegistry;
import com.coderhino.web.dto.ServiceStatusDto;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class ServiceStatusController {

    private final ServiceRegistry serviceRegistry;

    public ServiceStatusController(ServiceRegistry serviceRegistry) {
        this.serviceRegistry = serviceRegistry;
    }

    @GetMapping(value = "/status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ServiceStatusDto> getStatus() {
        var body = ServiceStatusDto.from(
            serviceRegistry.mcp().definitions().stream().toList(),
            serviceRegistry.mcp().connections(),
            serviceRegistry.lsp().definitions(),
            serviceRegistry.lsp().connections(),
            serviceRegistry.pluginService().list()
        );
        return ResponseEntity.ok(body);
    }
}
