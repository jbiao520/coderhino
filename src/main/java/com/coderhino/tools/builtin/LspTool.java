package com.coderhino.tools.builtin;

import com.coderhino.services.lsp.LspLocationDescriptor;
import com.coderhino.services.lsp.LspSymbolDescriptor;
import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.Map;
import java.util.stream.Collectors;

public final class LspTool implements ToolDefinition<LspTool.Input, String> {
    @Override
    public String name() {
        return "lsp";
    }

    @Override
    public String description() {
        return "Run LSP code intelligence operations";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "operation", Map.of("type", "string"),
            "language", Map.of("type", "string"),
            "uri", Map.of("type", "string"),
            "query", Map.of("type", "string"),
            "line", Map.of("type", "integer"),
            "character", Map.of("type", "integer")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input == null || input.operation() == null || input.operation().isBlank()) {
            return PermissionResult.deny("operation must not be blank.");
        }
        if (input.language() == null || input.language().isBlank()) {
            return PermissionResult.deny("language must not be blank.");
        }
        return switch (input.operation()) {
            case "workspaceSymbol" -> input.query() == null || input.query().isBlank()
                ? PermissionResult.deny("query must not be blank for workspaceSymbol.")
                : PermissionResult.allow();
            case "documentSymbol" -> input.uri() == null || input.uri().isBlank()
                ? PermissionResult.deny("uri must not be blank for documentSymbol.")
                : PermissionResult.allow();
            case "goToDefinition", "hover" -> input.uri() == null || input.uri().isBlank() || input.line() == null || input.character() == null
                ? PermissionResult.deny("uri, line, and character are required for %s.".formatted(input.operation()))
                : PermissionResult.allow();
            default -> PermissionResult.deny("Unsupported lsp operation: " + input.operation());
        };
    }

    @Override
    public String execute(Input input, ToolContext context) {
        var lsp = context.services().lsp();
        return switch (input.operation()) {
            case "workspaceSymbol" -> formatSymbols(lsp.workspaceSymbols(input.language(), input.query()).orElseThrow(), "No symbols found.");
            case "documentSymbol" -> formatSymbols(lsp.documentSymbols(input.language(), input.uri()).orElseThrow(), "No document symbols found.");
            case "goToDefinition" -> formatLocations(lsp.definition(input.language(), input.uri(), input.line(), input.character()).orElseThrow(), "No definition found.");
            case "hover" -> lsp.hover(input.language(), input.uri(), input.line(), input.character()).orElse("No hover available.");
            default -> throw new IllegalArgumentException("Unsupported lsp operation: " + input.operation());
        };
    }

    private String formatSymbols(java.util.List<LspSymbolDescriptor> symbols, String emptyMessage) {
        if (symbols.isEmpty()) {
            return emptyMessage;
        }
        return symbols.stream()
            .map(symbol -> "%s kind=%d %s:%d:%d".formatted(symbol.name(), symbol.kind(), symbol.uri(), symbol.line(), symbol.character()))
            .collect(Collectors.joining(System.lineSeparator()));
    }

    private String formatLocations(java.util.List<LspLocationDescriptor> locations, String emptyMessage) {
        if (locations.isEmpty()) {
            return emptyMessage;
        }
        return locations.stream()
            .map(location -> "%s:%d:%d".formatted(location.uri(), location.line(), location.character()))
            .collect(Collectors.joining(System.lineSeparator()));
    }

    public record Input(String operation, String language, String uri, String query, Integer line, Integer character) {
    }
}
