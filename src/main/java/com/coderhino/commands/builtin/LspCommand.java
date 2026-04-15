package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class LspCommand implements CommandDefinition {
    @Override
    public String name() {
        return "lsp";
    }

    @Override
    public String description() {
        return "List configured LSP servers or inspect one by language";
    }

    @Override
    public void execute(CommandContext context, String args) {
        if (args == null || args.isBlank()) {
            context.services().lsp().connections().forEach(connection ->
                context.out().printf("%s connected=%s status=%s pid=%s cmd=%s%n",
                    connection.language(),
                    connection.connected(),
                    connection.statusMessage(),
                    connection.processId(),
                    connection.commandLine()));
            return;
        }

        if (args.startsWith("disconnect ")) {
            var language = args.substring("disconnect ".length()).trim();
            var disconnected = context.services().lsp().disconnect(language);
            if (disconnected.isEmpty()) {
                context.err().printf("Unknown LSP language: %s%n", language);
                return;
            }
            var connection = disconnected.get();
            context.out().printf("LSP %s status=%s connected=%s%n", connection.language(), connection.statusMessage(), connection.connected());
            return;
        }

        if (args.startsWith("symbols ")) {
            var remainder = args.substring("symbols ".length()).trim();
            var firstSpace = remainder.indexOf(' ');
            if (firstSpace < 0) {
                context.err().println("Usage: /lsp symbols <language> <query>");
                return;
            }
            var language = remainder.substring(0, firstSpace).trim();
            var query = remainder.substring(firstSpace + 1).trim();
            if (query.isEmpty()) {
                context.err().println("Usage: /lsp symbols <language> <query>");
                return;
            }
            var symbols = context.services().lsp().workspaceSymbols(language, query);
            if (symbols.isEmpty()) {
                context.err().printf("Unknown LSP language: %s%n", language);
                return;
            }
            if (symbols.get().isEmpty()) {
                context.out().printf("No symbols found for %s%n", query);
                return;
            }
            symbols.get().forEach(symbol -> context.out().printf("%s kind=%d %s:%d:%d%n",
                symbol.name(),
                symbol.kind(),
                symbol.uri(),
                symbol.line(),
                symbol.character()));
            return;
        }

        if (args.startsWith("document-symbols ")) {
            var remainder = args.substring("document-symbols ".length()).trim();
            var firstSpace = remainder.indexOf(' ');
            if (firstSpace < 0) {
                context.err().println("Usage: /lsp document-symbols <language> <uri>");
                return;
            }
            var language = remainder.substring(0, firstSpace).trim();
            var uri = remainder.substring(firstSpace + 1).trim();
            if (uri.isEmpty()) {
                context.err().println("Usage: /lsp document-symbols <language> <uri>");
                return;
            }
            var symbols = context.services().lsp().documentSymbols(language, uri);
            if (symbols.isEmpty()) {
                context.err().printf("Unknown LSP language: %s%n", language);
                return;
            }
            if (symbols.get().isEmpty()) {
                context.out().printf("No document symbols found for %s%n", uri);
                return;
            }
            symbols.get().forEach(symbol -> context.out().printf("%s kind=%d %s:%d:%d%n",
                symbol.name(),
                symbol.kind(),
                symbol.uri(),
                symbol.line(),
                symbol.character()));
            return;
        }

        if (args.startsWith("definition ")) {
            var remainder = args.substring("definition ".length()).trim();
            var parts = remainder.split("\\s+");
            if (parts.length != 4) {
                context.err().println("Usage: /lsp definition <language> <uri> <line> <character>");
                return;
            }
            try {
                var locations = context.services().lsp().definition(parts[0], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (locations.isEmpty()) {
                    context.err().printf("Unknown LSP language: %s%n", parts[0]);
                    return;
                }
                if (locations.get().isEmpty()) {
                    context.out().printf("No definition found for %s:%s:%s%n", parts[1], parts[2], parts[3]);
                    return;
                }
                locations.get().forEach(location -> context.out().printf("%s:%d:%d%n", location.uri(), location.line(), location.character()));
            } catch (NumberFormatException exception) {
                context.err().println("Usage: /lsp definition <language> <uri> <line> <character>");
            }
            return;
        }

        if (args.startsWith("hover ")) {
            var remainder = args.substring("hover ".length()).trim();
            var parts = remainder.split("\\s+");
            if (parts.length != 4) {
                context.err().println("Usage: /lsp hover <language> <uri> <line> <character>");
                return;
            }
            try {
                var hover = context.services().lsp().hover(parts[0], parts[1], Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
                if (hover.isEmpty()) {
                    context.err().printf("Unknown LSP language: %s%n", parts[0]);
                    return;
                }
                context.out().println(hover.get());
            } catch (NumberFormatException exception) {
                context.err().println("Usage: /lsp hover <language> <uri> <line> <character>");
            }
            return;
        }

        var definition = context.services().lsp().find(args.trim());
        if (definition.isEmpty()) {
            context.err().printf("Unknown LSP language: %s%n", args.trim());
            return;
        }

        var connection = context.services().lsp().start(args.trim()).orElseThrow();
        context.out().printf("LSP %s status=%s connected=%s pid=%s%n",
            connection.language(),
            connection.statusMessage(),
            connection.connected(),
            connection.processId());
    }
}
