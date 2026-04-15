package com.coderhino.services.summary;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SessionEndSummaryAndFormatterTest {

    @Test
    void buildSummaryGroupsByOperation() {
        var tracker = new FileChangeTracker();
        var sessionId = UUID.randomUUID();
        tracker.onFileChange(sessionId, new FileChange(Path.of("a.txt"), FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(sessionId, new FileChange(Path.of("b.txt"), FileOperation.MODIFIED, Instant.now(), "edit_file"));
        tracker.onFileChange(sessionId, new FileChange(Path.of("c.txt"), FileOperation.DELETED, Instant.now(), "bash"));

        var summaryService = new SessionEndSummary(tracker);
        var summary = summaryService.buildSummary(sessionId);

        assertEquals(List.of(Path.of("a.txt")), summary.created());
        assertEquals(List.of(Path.of("b.txt")), summary.modified());
        assertEquals(List.of(Path.of("c.txt")), summary.deleted());
        assertEquals(3, summary.totalChanges());
    }

    @Test
    void buildSummaryEmptySession() {
        var tracker = new FileChangeTracker();
        var summaryService = new SessionEndSummary(tracker);
        var summary = summaryService.buildSummary(UUID.randomUUID());

        assertTrue(summary.created().isEmpty());
        assertTrue(summary.modified().isEmpty());
        assertTrue(summary.deleted().isEmpty());
        assertEquals(0, summary.totalChanges());
    }

    @Test
    void formatterNoChanges() {
        var summary = new FileChangeSummary(List.of(), List.of(), List.of());
        var output = FileChangeSummaryFormatter.format(summary);
        assertEquals("Session Summary — No file changes detected", output);
    }

    @Test
    void formatterSingleChange() {
        var summary = new FileChangeSummary(List.of(Path.of("test.txt")), List.of(), List.of());
        var output = FileChangeSummaryFormatter.format(summary);
        assertEquals("Session Summary — 1 file changed\n  Created (1): test.txt", output);
    }

    @Test
    void formatterMultipleChanges() {
        var summary = new FileChangeSummary(
            List.of(Path.of("a.txt"), Path.of("b.txt")),
            List.of(Path.of("c.txt")),
            List.of(Path.of("d.txt"))
        );
        var output = FileChangeSummaryFormatter.format(summary);
        assertTrue(output.startsWith("Session Summary — 4 files changed"));
        assertTrue(output.contains("Created (2): a.txt, b.txt"));
        assertTrue(output.contains("Modified (1): c.txt"));
        assertTrue(output.contains("Deleted (1): d.txt"));
    }
}
