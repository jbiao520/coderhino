package com.coderhino.tools.runtime;

import java.util.Optional;
import java.util.UUID;

public interface CommandSummaryService {
    Optional<String> formatFileChanges(UUID sessionId);
}
