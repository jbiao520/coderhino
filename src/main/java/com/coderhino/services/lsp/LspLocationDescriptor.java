package com.coderhino.services.lsp;

public record LspLocationDescriptor(
    String uri,
    int line,
    int character
) {
}
