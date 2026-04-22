package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class ReleaseNotesCommand implements CommandDefinition {
    @Override
    public String name() {
        return "release-notes";
    }

    @Override
    public String description() {
        return "Show release notes and version history";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();

        renderer.printLine("Code Rhino Java — Release Notes");
        renderer.printLine("================================");
        renderer.printLine("");
        renderer.printLine("Current version: 1.0.0-SNAPSHOT");
        renderer.printLine("");
        renderer.printLine("v1.0.0-SNAPSHOT (Development)");
        renderer.printLine("  - Initial Java rewrite of Code Rhino CLI");
        renderer.printLine("  - Core tool system: Bash, FileRead, FileWrite, FileEdit, Glob, Grep, WebSearch, WebFetch");
            renderer.printLine("  - Agent tool and Skill tool integration");
        renderer.printLine("  - MCP server support via stdio JSON-RPC");
        renderer.printLine("  - LSP client integration");
        renderer.printLine("  - Session persistence and resume");
        renderer.printLine("  - Permission system with configurable modes");
        renderer.printLine("  - Token usage tracking and cost estimation");
        renderer.printLine("  - Context compression service");
        renderer.printLine("  - Plugin and skill framework");
        renderer.printLine("  - Interactive REPL with 50+ slash commands");
        renderer.printLine("  - Full test suite (800+ tests)");

        var sub = args == null ? "" : args.trim();
        if (sub.equals("--check") || sub.equals("check")) {
            renderer.printLine("");
            checkForUpdates(renderer);
        } else {
            renderer.printLine("");
            renderer.printLine("Use /release-notes check to check for available updates.");
        }
    }

    private void checkForUpdates(com.coderhino.cli.TerminalRenderer renderer) {
        try {
            var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
            var request = HttpRequest.newBuilder()
                .uri(URI.create("https://registry.npmjs.org/@anthropic-ai/claude-code/latest"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                var body = response.body();
                var versionIdx = body.indexOf("\"version\"");
                if (versionIdx >= 0) {
                    var start = body.indexOf("\"", versionIdx + 9) + 1;
                    var end = body.indexOf("\"", start);
                    var latestVersion = body.substring(start, end);
                    renderer.printLine("Latest upstream version: " + latestVersion);
                    renderer.printLine("This is an independent Java rewrite (version comparison not applicable).");
                } else {
                    renderer.printLine("Could not parse upstream version.");
                }
            } else {
                renderer.printLine("Could not reach npm registry (status " + response.statusCode() + ").");
            }
        } catch (Exception e) {
            renderer.printLine("Update check failed: " + e.getMessage());
            renderer.printLine("You may be offline or behind a firewall.");
        }
    }
}
