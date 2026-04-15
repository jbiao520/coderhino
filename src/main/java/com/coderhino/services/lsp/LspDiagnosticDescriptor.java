package com.coderhino.services.lsp;

public record LspDiagnosticDescriptor(
    String uri,
    String message,
    int severity,
    String code,
    String source,
    int line,
    int character
) {}
