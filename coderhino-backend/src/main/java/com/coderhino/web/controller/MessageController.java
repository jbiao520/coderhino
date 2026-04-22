package com.coderhino.web.controller;

import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.MessageSubmitRequest;
import com.coderhino.web.dto.MessageSubmitResponse;
import com.coderhino.web.dto.PendingQuestionAnswerRequest;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.exception.RunNotFoundException;
import com.coderhino.web.exception.SessionBusyException;
import com.coderhino.web.service.RunService;
import com.coderhino.web.session.WebSessionRegistry;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/sessions/{id}")
public class MessageController {

    private final WebSessionRegistry registry;
    private final RunService runService;

    public MessageController(WebSessionRegistry registry, RunService runService) {
        this.registry = registry;
        this.runService = runService;
    }

    @PostMapping(value = {"/messages", "/runs"}, consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> submitMessage(
            @PathVariable("id") String sessionId,
            @RequestBody MessageSubmitRequest request) {

        var sessionOpt = registry.find(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var session = sessionOpt.get();
        try {
            var result = runService.submitRun(session, request.getInput(), request);
            return ResponseEntity.status(HttpStatus.ACCEPTED)
                    .body(new MessageSubmitResponse(result.getRunId(), result.getApprovalId(), result.getVisiblePrompt()));
        } catch (SessionBusyException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("session_busy", e.getActiveRunId()));
        }
    }

    @PostMapping(value = "/runs/{runId}/cancel", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelRun(
            @PathVariable("id") String sessionId,
            @PathVariable("runId") String runId) {

        var sessionOpt = registry.find(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var session = sessionOpt.get();
        try {
            runService.cancelRun(session, runId);
            return ResponseEntity.ok(new RunDto(runId, RunDto.RunStatus.CANCELLED));
        } catch (RunNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("run_not_found", runId));
        }
    }

    @PostMapping(value = "/runs/{runId}/answer", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> answerPendingQuestion(
            @PathVariable("id") String sessionId,
            @PathVariable("runId") String runId,
            @RequestBody PendingQuestionAnswerRequest request) {

        var sessionOpt = registry.find(sessionId);
        if (sessionOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        var session = sessionOpt.get();
        try {
            return ResponseEntity.ok(runService.answerPendingQuestion(session, runId, request));
        } catch (RunNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("run_not_found", runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(new ErrorResponse("invalid_request", runId, e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("pending_question_not_found", runId, e.getMessage()));
        }
    }

    @DeleteMapping(value = "/runs/{runId}", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> cancelRunDelete(
            @PathVariable("id") String sessionId,
            @PathVariable("runId") String runId) {
        return cancelRun(sessionId, runId);
    }
}
