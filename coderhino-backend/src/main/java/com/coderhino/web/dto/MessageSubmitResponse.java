package com.coderhino.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class MessageSubmitResponse {

    @JsonProperty("runId")
    private String runId;

    @JsonProperty("approvalId")
    private String approvalId;

    @JsonProperty("visiblePrompt")
    private String visiblePrompt;

    public MessageSubmitResponse() {
    }

    public MessageSubmitResponse(String runId) {
        this.runId = runId;
    }

    public MessageSubmitResponse(String runId, String approvalId) {
        this.runId = runId;
        this.approvalId = approvalId;
    }

    public MessageSubmitResponse(String runId, String approvalId, String visiblePrompt) {
        this.runId = runId;
        this.approvalId = approvalId;
        this.visiblePrompt = visiblePrompt;
    }

    public String getRunId() {
        return runId;
    }

    public void setRunId(String runId) {
        this.runId = runId;
    }

    public String getApprovalId() {
        return approvalId;
    }

    public void setApprovalId(String approvalId) {
        this.approvalId = approvalId;
    }

    public String getVisiblePrompt() {
        return visiblePrompt;
    }

    public void setVisiblePrompt(String visiblePrompt) {
        this.visiblePrompt = visiblePrompt;
    }
}
