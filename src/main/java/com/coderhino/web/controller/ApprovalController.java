package com.coderhino.web.controller;

import com.coderhino.web.approval.ApprovalAlreadyResolvedException;
import com.coderhino.web.approval.ApprovalNotFoundException;
import com.coderhino.web.approval.ApprovalRecord;
import com.coderhino.web.approval.ApprovalService;
import com.coderhino.web.dto.ErrorResponse;
import com.coderhino.web.dto.RunDto;
import com.coderhino.web.service.RunService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/sessions/{id}/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final RunService runService;

    public ApprovalController(ApprovalService approvalService, RunService runService) {
        this.approvalService = approvalService;
        this.runService = runService;
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<List<ApprovalRecord>> listApprovals(@PathVariable("id") String sessionId) {
        var approvals = approvalService.listBySession(sessionId);
        return ResponseEntity.ok(approvals);
    }

    @PostMapping(value = "/{approvalId}/approve", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> approve(@PathVariable("id") String sessionId,
                                     @PathVariable("approvalId") String approvalId) {
        try {
            var runDto = runService.approveAndExecute(approvalId);
            var record = approvalService.find(approvalId);
            return ResponseEntity.ok(new ApprovalResult(record, runDto));
        } catch (ApprovalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("approval_not_found", null, e.getMessage()));
        } catch (ApprovalAlreadyResolvedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("approval_already_resolved", null, e.getMessage()));
        }
    }

    @PostMapping(value = "/{approvalId}/deny", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<?> deny(@PathVariable("id") String sessionId,
                                  @PathVariable("approvalId") String approvalId) {
        try {
            var runDto = runService.denyWithoutExecution(approvalId);
            var record = approvalService.find(approvalId);
            return ResponseEntity.ok(new ApprovalResult(record, runDto));
        } catch (ApprovalNotFoundException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(new ErrorResponse("approval_not_found", null, e.getMessage()));
        } catch (ApprovalAlreadyResolvedException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(new ErrorResponse("approval_already_resolved", null, e.getMessage()));
        }
    }

    public record ApprovalResult(ApprovalRecord approval, RunDto run) {}
}
