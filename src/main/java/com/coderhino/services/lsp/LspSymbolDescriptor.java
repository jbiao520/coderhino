package com.coderhino.services.lsp;

public record LspSymbolDescriptor(
    String name,
    int kind,
    String uri,
    int line,
    int character
) {
}
