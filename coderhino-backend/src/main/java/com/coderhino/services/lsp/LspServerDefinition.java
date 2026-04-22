package com.coderhino.services.lsp;

import java.util.List;

public record LspServerDefinition(
    String language,
    String command,
    List<String> arguments,
    boolean enabled
) {
    public LspServerDefinition {
        arguments = List.copyOf(arguments);
    }
}
