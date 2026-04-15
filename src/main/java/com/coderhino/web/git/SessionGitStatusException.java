package com.coderhino.web.git;

public class SessionGitStatusException extends RuntimeException {

    public SessionGitStatusException(String message) {
        super(message);
    }

    public SessionGitStatusException(String message, Throwable cause) {
        super(message, cause);
    }
}
