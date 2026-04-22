package com.coderhino.web.dto;

import com.coderhino.web.terminal.TerminalSession;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class TerminalDto {

    @JsonProperty("terminalId")
    private String terminalId;

    @JsonProperty("label")
    private String label;

    @JsonProperty("status")
    private String status;

    @JsonProperty("cwd")
    private String cwd;

    @JsonProperty("worktreeId")
    private String worktreeId;

    @JsonProperty("createdAt")
    private Instant createdAt;

    @JsonProperty("exitCode")
    private Integer exitCode;

    @JsonProperty("message")
    private String message;

    public TerminalDto() {
    }

    public TerminalDto(String terminalId, String label, String status, String cwd, String worktreeId, Instant createdAt, Integer exitCode, String message) {
        this.terminalId = terminalId;
        this.label = label;
        this.status = status;
        this.cwd = cwd;
        this.worktreeId = worktreeId;
        this.createdAt = createdAt;
        this.exitCode = exitCode;
        this.message = message;
    }

    public static TerminalDto from(TerminalSession terminalSession) {
        return new TerminalDto(
            terminalSession.getTerminalId(),
            terminalSession.getLabel(),
            terminalSession.getStatus().name(),
            terminalSession.getCwd().toString(),
            terminalSession.getWorktreeId(),
            terminalSession.getCreatedAt(),
            terminalSession.getExitCode(),
            terminalSession.getMessage()
        );
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getLabel() {
        return label;
    }

    public String getStatus() {
        return status;
    }

    public String getCwd() {
        return cwd;
    }

    public String getWorktreeId() {
        return worktreeId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getMessage() {
        return message;
    }
}
