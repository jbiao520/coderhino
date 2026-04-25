package com.coderhino.services.summary;

import java.nio.file.Path;
import java.util.List;

public record FileChangeSummary(List<Path> created, List<Path> modified, List<Path> deleted) {
    public FileChangeSummary {
        created = List.copyOf(created);
        modified = List.copyOf(modified);
        deleted = List.copyOf(deleted);
    }

    public int totalChanges() {
        return created.size() + modified.size() + deleted.size();
    }
}
