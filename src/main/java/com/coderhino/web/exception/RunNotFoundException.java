package com.coderhino.web.exception;

public class RunNotFoundException extends RuntimeException {

    private final String runId;

    public RunNotFoundException(String runId) {
        super("Run not found or does not belong to this session: " + runId);
        this.runId = runId;
    }

    public String getRunId() {
        return runId;
    }
}
