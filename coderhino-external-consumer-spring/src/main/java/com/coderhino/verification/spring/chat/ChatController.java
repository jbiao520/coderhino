package com.coderhino.verification.spring.chat;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

@RestController
public class ChatController {

    private final ChatAgentRunner runner;

    public ChatController(ChatAgentRunner runner) {
        this.runner = Objects.requireNonNull(runner, "runner");
    }

    @PostMapping(value = "/chat", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> chat(@RequestBody(required = false) ChatRequest request) {
        if (request == null || request.message() == null || request.message().isBlank()) {
            return ResponseEntity.badRequest().body(new ErrorResponse("invalid_request", "message is required"));
        }
        try {
            var result = runner.run(request.message());
            return ResponseEntity.ok(new ChatResponse(
                result.finalText(),
                result.stopReason().name(),
                result.iterationCount(),
                result.isSuccess()
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new ErrorResponse("internal_error", "chat request failed"));
        }
    }
}
