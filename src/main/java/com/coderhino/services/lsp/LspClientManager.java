package com.coderhino.services.lsp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class LspClientManager {
    private final Map<String, LspServerDefinition> definitions = new LinkedHashMap<>();
    private final Map<String, LspConnection> connections = new LinkedHashMap<>();
    private final Map<String, Process> processes = new LinkedHashMap<>();
    private final Map<String, LspJsonRpcSession> sessions = new LinkedHashMap<>();

    public void register(LspServerDefinition definition) {
        definitions.put(definition.language(), definition);
        connections.putIfAbsent(definition.language(), new LspConnection(definition.language(), false, null, "registered", null, List.of()));
    }

    public void unregister(String language) {
        definitions.remove(language);
        connections.remove(language);
    }

    public List<LspServerDefinition> definitions() {
        return new ArrayList<>(definitions.values());
    }

    public Optional<LspServerDefinition> find(String language) {
        return Optional.ofNullable(definitions.get(language));
    }

    public List<LspConnection> connections() {
        return new ArrayList<>(connections.values());
    }

    public Optional<LspConnection> start(String language) {
        var definition = definitions.get(language);
        if (definition == null) {
            return Optional.empty();
        }
        if (!definition.enabled()) {
            var disabled = new LspConnection(language, false, Instant.now(), "disabled", null, buildCommandLine(definition));
            connections.put(language, disabled);
            return Optional.of(disabled);
        }

        try {
            var processBuilder = new ProcessBuilder(buildCommandLine(definition));
            var process = processBuilder.start();
            processes.put(language, process);
            sessions.put(language, new LspJsonRpcSession(process));
            var connection = new LspConnection(language, process.isAlive(), Instant.now(), process.isAlive() ? "connected" : "exited", process.pid(), buildCommandLine(definition));
            connections.put(language, connection);
            return Optional.of(connection);
        } catch (Exception exception) {
            var failed = new LspConnection(language, false, Instant.now(), "failed: " + exception.getMessage(), null, buildCommandLine(definition));
            connections.put(language, failed);
            return Optional.of(failed);
        }
    }

    public Optional<LspConnection> disconnect(String language) {
        var session = sessions.remove(language);
        if (session != null) {
            session.close();
        }
        var process = processes.remove(language);
        if (process != null && process.isAlive()) {
            process.destroy();
        }
        var existing = connections.get(language);
        if (existing == null) {
            return Optional.empty();
        }
        var disconnected = new LspConnection(language, false, existing.lastStartedAt(), "disconnected", null, existing.commandLine());
        connections.put(language, disconnected);
        return Optional.of(disconnected);
    }

    public Optional<List<LspSymbolDescriptor>> workspaceSymbols(String language, String query) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var symbols = session.workspaceSymbols(query);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "protocol-ready",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(symbols);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(List.of());
        }
    }

    public Optional<List<LspSymbolDescriptor>> documentSymbols(String language, String uri) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var symbols = session.documentSymbols(uri);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "protocol-ready",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(symbols);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(List.of());
        }
    }

    public Optional<List<LspLocationDescriptor>> definition(String language, String uri, int line, int character) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var locations = session.definition(uri, line, character);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "protocol-ready",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(locations);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(List.of());
        }
    }

    public Optional<String> hover(String language, String uri, int line, int character) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of("No hover available.");
        }

        try {
            var hover = session.hover(uri, line, character);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "protocol-ready",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(hover);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of("No hover available.");
        }
    }

    public Optional<List<LspLocationDescriptor>> references(String language, String uri, int line, int character) {
        return references(language, uri, line, character, false);
    }

    public Optional<List<LspLocationDescriptor>> references(String language, String uri, int line, int character, boolean includeDeclaration) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var locations = session.references(uri, line, character, includeDeclaration);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "protocol-ready",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(locations);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(List.of());
        }
    }

    public Optional<List<LspDiagnosticDescriptor>> getDiagnostics(String language, String uri) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }

        if (!sessions.containsKey(language)) {
            start(language);
        }

        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }

        try {
            var diagnostics = session.getDiagnostics(uri);
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    existing.connected(),
                    existing.lastStartedAt(),
                    "diagnostics-fetched",
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(diagnostics);
        } catch (Exception exception) {
            var existing = connections.get(language);
            if (existing != null) {
                connections.put(language, new LspConnection(
                    existing.language(),
                    false,
                    existing.lastStartedAt(),
                    "protocol-error: " + exception.getMessage(),
                    existing.processId(),
                    existing.commandLine()
                ));
            }
            return Optional.of(List.of());
        }
    }

    public Optional<List<LspDiagnosticDescriptor>> getPublishedDiagnostics(String language, String uri) {
        if (!definitions.containsKey(language)) {
            return Optional.empty();
        }
        var session = sessions.get(language);
        if (session == null) {
            return Optional.of(List.of());
        }
        return Optional.of(session.getPublishedDiagnostics(uri));
    }

    private List<String> buildCommandLine(LspServerDefinition definition) {
        var command = new ArrayList<String>();
        command.add(definition.command());
        command.addAll(definition.arguments());
        return command;
    }
}
