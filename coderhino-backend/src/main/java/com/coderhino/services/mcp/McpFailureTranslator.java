package com.coderhino.services.mcp;

import java.util.ArrayList;
import java.util.List;

final class McpFailureTranslator {
    private McpFailureTranslator() {
    }

    static String message(Throwable throwable) {
        var messages = new ArrayList<String>();
        Throwable current = throwable;
        while (current != null) {
            var message = current.getMessage();
            if (message != null && !message.isBlank() && !messages.contains(message)) {
                messages.add(message.trim());
            }
            current = current.getCause();
        }

        if (messages.isEmpty()) {
            return throwable == null ? "Unknown MCP failure" : throwable.getClass().getSimpleName();
        }

        return messages.get(messages.size() - 1);
    }

    static String renderMessages(List<String> fragments) {
        if (fragments == null || fragments.isEmpty()) {
            return "";
        }
        return String.join(System.lineSeparator(), fragments);
    }
}
