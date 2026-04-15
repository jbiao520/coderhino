package com.coderhino.services.summary.integration;

import com.coderhino.services.summary.FileChangeSummary;
import com.coderhino.services.summary.FileChangeSummaryFormatter;
import com.coderhino.services.summary.SessionEndSummary;
import com.coderhino.services.summary.FileChangeTracker;
import com.coderhino.services.summary.FileChange;
import com.coderhino.services.summary.FileOperation;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SummaryOutputIntegrationTest {

    @Test
    void sessionEndSummaryFormatsCorrectly() {
        var tracker = new FileChangeTracker();
        var sessionId = UUID.randomUUID();
        tracker.onFileChange(sessionId, new FileChange(Path.of("src/NewFile.java"), FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(sessionId, new FileChange(Path.of("src/Existing.java"), FileOperation.MODIFIED, Instant.now(), "edit_file"));

        var summaryService = new SessionEndSummary(tracker);
        var summary = summaryService.buildSummary(sessionId);
        var output = FileChangeSummaryFormatter.format(summary);

        assertTrue(output.contains("2 files changed"));
        assertTrue(output.contains("Created (1): src/NewFile.java"));
        assertTrue(output.contains("Modified (1): src/Existing.java"));
    }

    @Test
    void summaryCommandOutputShowsFileChanges() {
        var tracker = new FileChangeTracker();
        var sessionId = UUID.randomUUID();
        tracker.onFileChange(sessionId, new FileChange(Path.of("test.txt"), FileOperation.CREATED, Instant.now(), "write_file"));

        var summaryService = new SessionEndSummary(tracker);
        var summary = summaryService.buildSummary(sessionId);

        assertEquals(1, summary.totalChanges());
        assertEquals(1, summary.created().size());
        assertEquals(Path.of("test.txt"), summary.created().get(0));
        assertTrue(summary.modified().isEmpty());
        assertTrue(summary.deleted().isEmpty());
    }

    @Test
    void emptySessionProducesNoChanges() {
        var tracker = new FileChangeTracker();
        var summaryService = new SessionEndSummary(tracker);
        var summary = summaryService.buildSummary(UUID.randomUUID());
        var output = FileChangeSummaryFormatter.format(summary);

        assertEquals("Session Summary — No file changes detected", output);
    }
}
