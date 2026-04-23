package com.coderhino.web.terminal;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;

public final class TerminalSession {

    private static final int MAX_BACKLOG_CHARS = 65536;

    private final String terminalId;
    private final String sessionId;
    private final String projectId;
    private final String worktreeId;
    private final String label;
    private final Path cwd;
    private final Instant createdAt;
    private final TerminalProcess process;
    private final CopyOnWriteArrayList<TerminalEventListener> listeners = new CopyOnWriteArrayList<>();
    private final StringBuilder backlog = new StringBuilder();
    private final AtomicReference<TerminalStatus> status = new AtomicReference<>(TerminalStatus.RUNNING);
    private final AtomicReference<Integer> exitCode = new AtomicReference<>(null);
    private final AtomicReference<String> message = new AtomicReference<>(null);

    public TerminalSession(String terminalId, String sessionId, String projectId, String worktreeId, String label, Path cwd, Instant createdAt, TerminalProcess process) {
        this.terminalId = Objects.requireNonNull(terminalId, "terminalId");
        this.sessionId = Objects.requireNonNull(sessionId, "sessionId");
        this.projectId = Objects.requireNonNull(projectId, "projectId");
        this.worktreeId = worktreeId;
        this.label = Objects.requireNonNull(label, "label");
        this.cwd = Objects.requireNonNull(cwd, "cwd").toAbsolutePath().normalize();
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.process = Objects.requireNonNull(process, "process");
    }

    public String getTerminalId() {
        return terminalId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getProjectId() {
        return projectId;
    }

    public String getWorktreeId() {
        return worktreeId;
    }

    public String getLabel() {
        return label;
    }

    public Path getCwd() {
        return cwd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public TerminalStatus getStatus() {
        return status.get();
    }

    public Integer getExitCode() {
        return exitCode.get();
    }

    public String getMessage() {
        return message.get();
    }

    public boolean isAlive() {
        return process.isAlive();
    }

    public void write(String data) throws java.io.IOException {
        process.write(data);
    }

    public void resize(int cols, int rows) throws java.io.IOException {
        process.resize(cols, rows);
    }

    public void addListener(TerminalEventListener listener) {
        listeners.add(listener);
        var snapshot = readBacklog();
        if (!snapshot.isEmpty()) {
            listener.onOutput(snapshot);
        }
        if (status.get() == TerminalStatus.EXITED) {
            listener.onExit(exitCode.get() != null ? exitCode.get() : 0);
        } else if (status.get() == TerminalStatus.ERROR) {
            listener.onError(message.get() != null ? message.get() : "Terminal error");
        }
    }

    public void removeListener(TerminalEventListener listener) {
        listeners.remove(listener);
    }

    public void start() throws java.io.IOException {
        process.start(new TerminalProcess.TerminalListener() {
            @Override
            public void onOutput(String chunk) {
                appendBacklog(chunk);
                for (var listener : listeners) {
                    listener.onOutput(chunk);
                }
            }

            @Override
            public void onExit(int code) {
                status.set(TerminalStatus.EXITED);
                exitCode.set(code);
                for (var listener : listeners) {
                    listener.onExit(code);
                }
            }

            @Override
            public void onError(Throwable error) {
                status.set(TerminalStatus.ERROR);
                var text = error.getMessage() != null ? error.getMessage() : error.getClass().getSimpleName();
                message.set(text);
                for (var listener : listeners) {
                    listener.onError(text);
                }
            }
        });
    }

    public void close() {
        process.close();
    }

    private void appendBacklog(String chunk) {
        synchronized (backlog) {
            backlog.append(chunk);
            if (backlog.length() > MAX_BACKLOG_CHARS) {
                backlog.delete(0, backlog.length() - MAX_BACKLOG_CHARS);
            }
        }
    }

    private String readBacklog() {
        synchronized (backlog) {
            return backlog.toString();
        }
    }

    public interface TerminalEventListener {
        void onOutput(String chunk);

        void onExit(int exitCode);

        void onError(String message);
    }
}
