package com.coderhino.query;

import com.coderhino.services.summary.FileChange;
import com.coderhino.services.summary.FileChangeListener;

import java.util.UUID;

enum NoOpFileChangeListener implements FileChangeListener {
    INSTANCE;

    @Override
    public void onFileChange(UUID sessionId, FileChange change) {
    }
}
