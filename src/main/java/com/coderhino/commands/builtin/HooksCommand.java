package com.coderhino.commands.builtin;

import com.coderhino.cli.TerminalRenderer;
import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.hooks.HookConfig;
import com.coderhino.hooks.HookEvent;
import com.coderhino.hooks.HookExecutor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class HooksCommand implements CommandDefinition {
    @Override
    public String name() {
        return "hooks";
    }

    @Override
    public String description() {
        return "Show hook configuration and registered hooks. Use 'run <event> <subject>' to fire hooks manually.";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        var configDir = Path.of(state.cwd()).resolve(".coderhino");
        var hooksFile = configDir.resolve("hooks.json");

        if (sub.startsWith("run ")) {
            runHooks(renderer, hooksFile, sub.substring(4).trim());
            return;
        }

        displayConfig(renderer, configDir, hooksFile);
    }

    private void runHooks(TerminalRenderer renderer, Path hooksFile, String runArgs) {
        int spaceIdx = runArgs.indexOf(' ');
        String eventKey;
        String subject;
        if (spaceIdx < 0) {
            eventKey = runArgs;
            subject = "";
        } else {
            eventKey = runArgs.substring(0, spaceIdx);
            subject = runArgs.substring(spaceIdx + 1).trim();
        }

        HookEvent event;
        try {
            event = HookEvent.fromJsonKey(eventKey);
        } catch (IllegalArgumentException e) {
            renderer.printLine("Unknown event: " + eventKey);
            return;
        }

        var config = HookConfig.load(hooksFile);
        var results = new HookExecutor(config).fire(event, subject);

        if (results.isEmpty()) {
            renderer.printLine("No hooks matched for event '" + eventKey + "' subject '" + subject + "'");
            return;
        }

        for (var result : results) {
            renderer.printLine("[hook] " + result.entry().command());
            renderer.printLine("  exit: " + result.exitCode());
            if (!result.stdout().isEmpty()) {
                renderer.printLine("  stdout: " + result.stdout().trim());
            }
            if (!result.stderr().isEmpty()) {
                renderer.printLine("  stderr: " + result.stderr().trim());
            }
        }
    }

    private void displayConfig(TerminalRenderer renderer, Path configDir, Path hooksFile) {
        renderer.printLine("Hook Configuration");
        renderer.printLine("===================");
        renderer.printLine("  Config dir: " + configDir);
        renderer.printLine("  Hooks file: " + hooksFile);
        renderer.printLine("  File exists: " + Files.exists(hooksFile));

        if (Files.exists(hooksFile)) {
            try {
                var content = Files.readString(hooksFile);
                renderer.printLine("");
                renderer.printLine("Hooks configuration:");
                for (var line : content.split("\\R", -1)) {
                    renderer.printLine("  " + line);
                }
            } catch (IOException e) {
                renderer.printLine("  Error reading hooks file: " + e.getMessage());
            }
        } else {
            renderer.printLine("");
            renderer.printLine("No hooks configured.");
            renderer.printLine("Create " + hooksFile + " to configure hooks.");
            renderer.printLine("");
            renderer.printLine("Hook file format (JSON):");
            renderer.printLine("  {");
            renderer.printLine("    \"hooks\": {");
            renderer.printLine("      \"beforeToolUse\": [{\"command\": \"...\", \"pattern\": \"...\"}],");
            renderer.printLine("      \"afterToolUse\": [{\"command\": \"...\", \"pattern\": \"...\"}],");
            renderer.printLine("      \"beforeCommand\": [{\"command\": \"...\", \"pattern\": \"...\"}],");
            renderer.printLine("      \"afterCommand\": [{\"command\": \"...\", \"pattern\": \"...\"}]");
            renderer.printLine("    }");
            renderer.printLine("  }");
        }
    }
}
