package com.coderhino.web.controller;

import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.TerminalCreateRequest;
import com.coderhino.web.dto.TerminalDto;
import com.coderhino.web.dto.TerminalListDto;
import com.coderhino.web.terminal.WebTerminalService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sessions/{sessionId}/terminals")
public class TerminalController {

    private final WebTerminalService webTerminalService;

    public TerminalController(WebTerminalService webTerminalService) {
        this.webTerminalService = webTerminalService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<TerminalListDto> listTerminals(@PathVariable("sessionId") String sessionId) {
        var terminals = webTerminalService.listTerminals(sessionId).stream()
            .map(TerminalDto::from)
            .toList();
        return ResponseEntity.ok(new TerminalListDto(terminals));
    }

    @PostMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> createTerminal(
        @PathVariable("sessionId") String sessionId,
        @RequestBody(required = false) TerminalCreateRequest request
    ) {
        try {
            var terminal = webTerminalService.createTerminal(
                sessionId,
                request != null ? request.getLabel() : null,
                request != null ? request.getWorktreeId() : null
            );
            return ResponseEntity.status(HttpStatus.CREATED).body(TerminalDto.from(terminal));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ErrorResponse("Failed to create terminal", null, e.getMessage()));
        }
    }

    @DeleteMapping(value = "/{terminalId}")
    public ResponseEntity<Void> closeTerminal(@PathVariable("sessionId") String sessionId, @PathVariable("terminalId") String terminalId) {
        if (!webTerminalService.closeTerminal(sessionId, terminalId)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.noContent().build();
    }
}
