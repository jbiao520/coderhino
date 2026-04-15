package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ExportCommand implements CommandDefinition {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "export";
    }

    @Override
    public String description() {
        return "Export the current conversation to a file";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var messages = state.messages();
        var transcript = state.sessionRuntime().transcript();

        if (messages.isEmpty() && transcript.isEmpty()) {
            renderer.printLine("No conversation to export.");
            return;
        }

        var targetPath = resolveExportPath(state.cwd(), args);
        var sb = new StringBuilder();
        sb.append("# Code Rhino Conversation Export\n");
        sb.append("# Session: ").append(state.sessionRuntime().sessionId()).append("\n");
        sb.append("# Model: ").append(state.model()).append("\n");
        sb.append("# Exported: ").append(FORMATTER.format(java.time.Instant.now())).append("\n");
        sb.append("# Messages: ").append(messages.size()).append("\n\n");

        if (!transcript.isEmpty()) {
            sb.append("## Transcript (").append(transcript.size()).append(" entries)\n\n");
            for (var envelope : transcript) {
                var ts = FORMATTER.format(envelope.timestamp());
                sb.append("### [").append(ts).append("] ").append(envelope.message().type()).append("\n");
                sb.append(envelope.message().content()).append("\n\n");
            }
        }

        if (!messages.isEmpty()) {
            sb.append("## Messages (").append(messages.size()).append(")\n\n");
            for (var msg : messages) {
                sb.append("### ").append(msg.type()).append("\n");
                sb.append(msg.content()).append("\n\n");
            }
        }

        sb.append("---\n");
        sb.append("Tool uses: ").append(state.totalToolUses()).append("\n");
        sb.append("Total cost: $").append(String.format("%.6f", state.totalCostUsd())).append("\n");
        sb.append("Input tokens: ").append(state.totalInputTokens()).append("\n");
        sb.append("Output tokens: ").append(state.totalOutputTokens()).append("\n");

        try {
            Files.createDirectories(targetPath.getParent());
            Files.writeString(targetPath, sb.toString());
            renderer.printLine("Conversation exported to: " + targetPath);
            renderer.printLine("  Messages: " + messages.size());
            renderer.printLine("  Transcript entries: " + transcript.size());
            renderer.printLine("  File size: " + Files.size(targetPath) + " bytes");
        } catch (IOException e) {
            renderer.printLine("Failed to export conversation: " + e.getMessage());
        }
    }

    private Path resolveExportPath(String cwd, String args) {
        if (args != null && !args.isBlank()) {
            var trimmed = args.trim();
            if (!trimmed.startsWith("/")) {
                return Path.of(cwd).resolve(trimmed);
            }
            return Path.of(trimmed);
        }
        var timestamp = java.time.LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        return Path.of(cwd).resolve("claude-export-" + timestamp + ".md");
    }
}
