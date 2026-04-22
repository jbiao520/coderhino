package com.coderhino.permissions;

import com.coderhino.permissions.BashCommandClassifier.Classification;
import com.coderhino.types.PermissionMode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class PermissionAuditLog {

    public enum Decision {
        ALLOWED,
        DENIED,
        ESCALATED
    }

    public record Entry(
            Instant timestamp,
            String toolName,
            String command,
            Classification classification,
            PermissionMode mode,
            Decision decision,
            String reason
    ) {
        public Entry(String toolName, String command, Classification classification,
                     PermissionMode mode, Decision decision, String reason) {
            this(Instant.now(), toolName, command, classification, mode, decision, reason);
        }
    }

    private final CopyOnWriteArrayList<Entry> entries = new CopyOnWriteArrayList<>();

    public void record(String toolName, String command, Classification classification,
                       PermissionMode mode, Decision decision, String reason) {
        entries.add(new Entry(toolName, command, classification, mode, decision, reason));
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(new ArrayList<>(entries));
    }

    public List<Entry> entriesForTool(String toolName) {
        return entries.stream()
                .filter(e -> toolName.equals(e.toolName()))
                .toList();
    }

    public long countDecision(Decision decision) {
        return entries.stream().filter(e -> e.decision() == decision).count();
    }

    public long countClassification(Classification classification) {
        return entries.stream().filter(e -> e.classification() == classification).count();
    }

    public void clear() {
        entries.clear();
    }

    public int size() {
        return entries.size();
    }
}
