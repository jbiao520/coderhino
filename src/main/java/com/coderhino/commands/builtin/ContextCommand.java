package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.context.ContextCollector;

import java.nio.file.Path;

public final class ContextCommand implements CommandDefinition {
    @Override
    public String name() {
        return "context";
    }

    @Override
    public String description() {
        return "Show or manage context window contents";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        switch (sub) {
            case "", "show" -> {
                renderer.printLine("Context information:");
                renderer.printLine("  cwd:      " + state.cwd());
                renderer.printLine("  model:    " + state.model());
                renderer.printLine("  messages: " + state.messages().size());
                renderer.printLine("  session:  " + state.sessionRuntime().sessionId());
                if (state.sessionRuntime().customTitle() != null) {
                    renderer.printLine("  title:    " + state.sessionRuntime().customTitle());
                }
                renderer.printLine("  tokens:   input=" + state.totalInputTokens()
                    + " output=" + state.totalOutputTokens()
                    + " cost=" + String.format("%.6f", state.totalCostUsd()) + " USD");
                renderer.printLine("  mode:     " + state.permissionMode().name().toLowerCase());
            }
            case "collect", "refresh" -> {
                renderer.printLine("Collecting environment context from: " + state.cwd());
                try {
                    var collector = new ContextCollector();
                    var snapshot = collector.collect(Path.of(state.cwd()));
                    if (snapshot.systemContext() != null && !snapshot.systemContext().isBlank()) {
                        renderer.printLine("System context:");
                        for (var line : snapshot.systemContext().split("\\R", -1)) {
                            renderer.printLine("  " + line);
                        }
                    }
                    if (snapshot.userContext() != null && !snapshot.userContext().isBlank()) {
                        renderer.printLine("User context:");
                        for (var line : snapshot.userContext().split("\\R", -1)) {
                            renderer.printLine("  " + line);
                        }
                    }
                } catch (Exception e) {
                    renderer.printLine("Error collecting context: " + e.getMessage());
                }
            }
            default -> {
                renderer.printLine("Usage: /context [show|collect]");
                renderer.printLine("  show    - Show current context window summary (default)");
                renderer.printLine("  collect - Collect and display environment/git context");
            }
        }
    }
}
