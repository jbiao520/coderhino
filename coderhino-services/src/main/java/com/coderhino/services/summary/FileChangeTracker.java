package com.coderhino.services.summary;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public final class FileChangeTracker implements FileChangeListener {

    private static final int MAX_ENTRIES_PER_SESSION = 10_000;

    private final ConcurrentHashMap<UUID, CopyOnWriteArrayList<FileChange>> sessions = new ConcurrentHashMap<>();

    @Override
    public void onFileChange(UUID sessionId, FileChange change) {
        var changes = sessions.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        applyDeduplication(changes, change);
        if (changes.size() > MAX_ENTRIES_PER_SESSION) {
            changes.remove(0);
        }
    }

    public List<FileChange> getChanges(UUID sessionId) {
        var changes = sessions.get(sessionId);
        return changes == null ? Collections.emptyList() : Collections.unmodifiableList(changes);
    }

    public void clearSession(UUID sessionId) {
        sessions.remove(sessionId);
    }

    private void applyDeduplication(CopyOnWriteArrayList<FileChange> changes, FileChange incoming) {
        Path incomingPath = incoming.file();
        for (int i = changes.size() - 1; i >= 0; i--) {
            FileChange existing = changes.get(i);
            if (!existing.file().equals(incomingPath)) {
                continue;
            }
            changes.remove(i);
            FileOperation merged = merge(existing.operation(), incoming.operation());
            changes.add(new FileChange(incomingPath, merged, Instant.now(), incoming.toolName()));
            return;
        }
        changes.add(incoming);
    }

    private FileOperation merge(FileOperation previous, FileOperation current) {
        if (previous == FileOperation.CREATED && current == FileOperation.MODIFIED) {
            return FileOperation.CREATED;
        }
        if (previous == FileOperation.MODIFIED && current == FileOperation.DELETED) {
            return FileOperation.DELETED;
        }
        if (previous == FileOperation.CREATED && current == FileOperation.DELETED) {
            return FileOperation.DELETED;
        }
        return current;
    }
}
