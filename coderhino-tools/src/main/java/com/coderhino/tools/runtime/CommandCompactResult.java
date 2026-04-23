package com.coderhino.tools.runtime;

import com.coderhino.types.Message;

import java.util.List;

public record CommandCompactResult(
    List<Message> compactedMessages,
    int originalMessageCount,
    boolean compacted
) {
    public boolean wasCompacted() {
        return compacted;
    }
}
