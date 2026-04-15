package com.coderhino.services.summary;

import java.util.UUID;

public interface FileChangeListener {
    void onFileChange(UUID sessionId, FileChange change);
}
