package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * DTO representing the state of an active (or recently completed) run within a session.
 */
public class RunDto {

    /**
     * Possible states for a run.
     */
    public enum RunStatus {
        RUNNING,
        COMPLETED,
        CANCELLED,
        FAILED,
        PENDING_APPROVAL,
        WAITING_FOR_USER
    }

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("status")
    private RunStatus status;

    public RunDto() {
    }

    public RunDto(String runId, RunStatus status) {
        this.runId = runId;
        this.status = status;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public RunStatus getStatus() {
        return status;
    }

    public void setStatus(RunStatus status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "RunDto{runId='" + runId + "', status=" + status + '}';
    }
}
