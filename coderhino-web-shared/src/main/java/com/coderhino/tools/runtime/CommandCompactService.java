package com.coderhino.tools.runtime;

import com.coderhino.types.Message;

import java.util.List;

public interface CommandCompactService {
    CommandCompactResult compactManual(List<Message> messages, String customInstructions);
}
