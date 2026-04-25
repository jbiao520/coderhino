package com.coderhino.services.tasks;

public final class TaskOriginContext {
    private static final ThreadLocal<TaskOrigin> CURRENT = new ThreadLocal<>();

    private TaskOriginContext() {
    }

    public static TaskOrigin current() {
        return CURRENT.get();
    }

    public static Scope open(String projectId, String sessionId) {
        var previous = CURRENT.get();
        CURRENT.set(new TaskOrigin(normalize(projectId), normalize(sessionId)));
        return new Scope(previous);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    public record TaskOrigin(String projectId, String sessionId) {
    }

    public static final class Scope implements AutoCloseable {
        private final TaskOrigin previous;

        private Scope(TaskOrigin previous) {
            this.previous = previous;
        }

        @Override
        public void close() {
            if (previous == null) {
                CURRENT.remove();
                return;
            }
            CURRENT.set(previous);
        }
    }
}
