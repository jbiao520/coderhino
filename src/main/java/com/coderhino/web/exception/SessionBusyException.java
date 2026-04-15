package com.coderhino.web.exception;

public class SessionBusyException extends RuntimeException {

    private final String activeRunId;

    public SessionBusyException(String activeRunId) {
        super("Session already has an active run: " + activeRunId);
        this.activeRunId = activeRunId;
    }

    public String getActiveRunId() {
        return activeRunId;
    }
}
