package com.coderhino.services.tasks;

/**
 * Well-known task lifecycle statuses.
 *
 * <p>TaskRecord stores status as a plain String for backward compatibility
 * with arbitrary user-supplied status values (e.g. "paused", "in-progress").
 * This enum provides type-safe constants for the standard lifecycle:
 * {@code PENDING → RUNNING → DONE | CANCELLED | FAILED}.
 */
public enum TaskStatus {
    PENDING,
    RUNNING,
    DONE,
    CANCELLED,
    FAILED;

    /** Lowercase string value — matches existing string-based status comparisons. */
    public String value() {
        return name().toLowerCase();
    }

    /**
     * Parse a string to a TaskStatus, case-insensitive.
     * Returns {@code null} if the string does not match any enum constant.
     */
    public static TaskStatus fromString(String status) {
        if (status == null) {
            return null;
        }
        try {
            return valueOf(status.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
