package com.coderhino.web.approval;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Domain record representing a pending, approved, or denied approval request
 * within a web session.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApprovalRecord {

    public enum Status {
        PENDING,
        APPROVED,
        DENIED
    }

    @JsonProperty("approvalId")
    private String approvalId;

    @JsonProperty("sessionId")
    private String sessionId;

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("summary")
    private String summary;

    @JsonProperty("status")
    private Status status;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("resolvedAt")
    private Instant resolvedAt;

    public ApprovalRecord() {
    }

    public ApprovalRecord(String approvalId, String sessionId, String runId,
                          String action, String summary, Status status,
                          Instant createdAt, Instant resolvedAt) {
        this.approvalId = approvalId;
        this.sessionId = sessionId;
        this.runId = runId;
        this.action = action;
        this.summary = summary;
        this.status = status;
        this.createdAt = createdAt;
        this.resolvedAt = resolvedAt;
    }

    public static ApprovalRecord pending(String approvalId, String sessionId, String runId,
                                         String action, String summary) {
        return new ApprovalRecord(approvalId, sessionId, runId, action, summary,
                Status.PENDING, Instant.now(), null);
    }

    public String getApprovalId() { return approvalId; }
    public void setApprovalId(String approvalId) { this.approvalId = approvalId; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public String getRunId() { return runId; }
    public void setRunId(String runId) { this.runId = runId; }

    public String getAction() { return action; }
    public void setAction(String action) { this.action = action; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }

    @Override
    public String toString() {
        return "ApprovalRecord{approvalId='" + approvalId + "', sessionId='" + sessionId +
               "', status=" + status + '}';
    }
}
