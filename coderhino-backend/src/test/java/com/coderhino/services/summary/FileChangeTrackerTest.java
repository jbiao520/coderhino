package com.coderhino.services.summary;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

class FileChangeTrackerTest {

    private final FileChangeTracker tracker = new FileChangeTracker();

    @Test
    void tracksSingleChange() {
        var sessionId = UUID.randomUUID();
        var change = new FileChange(Path.of("test.txt"), FileOperation.CREATED, Instant.now(), "write_file");
        tracker.onFileChange(sessionId, change);

        var changes = tracker.getChanges(sessionId);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
    }

    @Test
    void deduplicatesCreateThenModifyToCreated() {
        var sessionId = UUID.randomUUID();
        var path = Path.of("test.txt");
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.MODIFIED, Instant.now(), "edit_file"));

        var changes = tracker.getChanges(sessionId);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.CREATED, changes.get(0).operation());
    }

    @Test
    void deduplicatesModifyThenDeleteToDelete() {
        var sessionId = UUID.randomUUID();
        var path = Path.of("test.txt");
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.MODIFIED, Instant.now(), "edit_file"));
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.DELETED, Instant.now(), "bash"));

        var changes = tracker.getChanges(sessionId);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.DELETED, changes.get(0).operation());
    }

    @Test
    void deduplicatesCreateThenDeleteToDelete() {
        var sessionId = UUID.randomUUID();
        var path = Path.of("test.txt");
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(sessionId, new FileChange(path, FileOperation.DELETED, Instant.now(), "bash"));

        var changes = tracker.getChanges(sessionId);
        assertEquals(1, changes.size());
        assertEquals(FileOperation.DELETED, changes.get(0).operation());
    }

    @Test
    void tracksMultipleFilesIndependently() {
        var sessionId = UUID.randomUUID();
        tracker.onFileChange(sessionId, new FileChange(Path.of("a.txt"), FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(sessionId, new FileChange(Path.of("b.txt"), FileOperation.MODIFIED, Instant.now(), "edit_file"));

        var changes = tracker.getChanges(sessionId);
        assertEquals(2, changes.size());
    }

    @Test
    void enforcesCapPerSession() {
        var sessionId = UUID.randomUUID();
        for (int i = 0; i < 10_005; i++) {
            tracker.onFileChange(sessionId, new FileChange(Path.of("file" + i + ".txt"), FileOperation.CREATED, Instant.now(), "write_file"));
        }

        var changes = tracker.getChanges(sessionId);
        assertEquals(10_000, changes.size());
    }

    @Test
    void isolatesPerSession() {
        var session1 = UUID.randomUUID();
        var session2 = UUID.randomUUID();
        tracker.onFileChange(session1, new FileChange(Path.of("a.txt"), FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.onFileChange(session2, new FileChange(Path.of("b.txt"), FileOperation.MODIFIED, Instant.now(), "edit_file"));

        assertEquals(1, tracker.getChanges(session1).size());
        assertEquals(1, tracker.getChanges(session2).size());
        assertEquals(Path.of("a.txt"), tracker.getChanges(session1).get(0).file());
        assertEquals(Path.of("b.txt"), tracker.getChanges(session2).get(0).file());
    }

    @Test
    void returnsEmptyListForUnknownSession() {
        assertTrue(tracker.getChanges(UUID.randomUUID()).isEmpty());
    }

    @Test
    void clearSessionRemovesData() {
        var sessionId = UUID.randomUUID();
        tracker.onFileChange(sessionId, new FileChange(Path.of("a.txt"), FileOperation.CREATED, Instant.now(), "write_file"));
        tracker.clearSession(sessionId);
        assertTrue(tracker.getChanges(sessionId).isEmpty());
    }

    @Test
    void threadSafety() throws Exception {
        var sessionId = UUID.randomUUID();
        int threadCount = 10;
        int changesPerThread = 100;
        var latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int t = 0; t < threadCount; t++) {
            final int threadIndex = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < changesPerThread; i++) {
                        tracker.onFileChange(sessionId, new FileChange(
                            Path.of("thread" + threadIndex + "_file" + i + ".txt"),
                            FileOperation.CREATED, Instant.now(), "write_file"));
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();
        assertEquals(threadCount * changesPerThread, tracker.getChanges(sessionId).size());
    }
}
