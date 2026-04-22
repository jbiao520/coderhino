package com.coderhino.services.summary;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class SessionEndSummary {

    private final FileChangeTracker tracker;

    public SessionEndSummary(FileChangeTracker tracker) {
        this.tracker = tracker;
    }

    public FileChangeSummary buildSummary(UUID sessionId) {
        var changes = tracker.getChanges(sessionId);
        List<Path> created = new ArrayList<>();
        List<Path> modified = new ArrayList<>();
        List<Path> deleted = new ArrayList<>();

        for (var change : changes) {
            switch (change.operation()) {
                case CREATED -> created.add(change.file());
                case MODIFIED -> modified.add(change.file());
                case DELETED -> deleted.add(change.file());
            }
        }

        return new FileChangeSummary(created, modified, deleted);
    }
}
