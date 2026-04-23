package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.List;

public final class DesktopCommand implements CommandDefinition {
    @Override
    public String name() {
        return "desktop";
    }

    @Override
    public String description() {
        return "Continue the current session in Claude Desktop";
    }

    @Override
    public List<String> aliases() {
        return List.of("app");
    }

    @Override
    public boolean hidden() {
        return !isSupportedPlatform();
    }

    @Override
    public boolean webCompatible() {
        return false;
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var subcommand = args == null ? "" : args.trim();

        if (!subcommand.isEmpty()) {
            renderer.printLine("Usage: /desktop");
            return;
        }

        if (!isSupportedPlatform()) {
            renderer.printLine("Claude Desktop handoff is available on macOS and Windows x64.");
            renderer.printLine("Current platform: " + System.getProperty("os.name") + " (" + System.getProperty("os.arch") + ")");
            return;
        }

        var state = context.bootstrapState().get();
        var sessionId = state.sessionRuntime().sessionId();
        var transcriptPath = context.sessionStore().transcriptPath(state.cwd(), sessionId);

        renderer.printLine("Desktop continuation details");
        renderer.printLine("Session:     " + sessionId);
        renderer.printLine("Model:       " + state.model());
        renderer.printLine("Workspace:   " + state.cwd());
        renderer.printLine("Transcript:  " + transcriptPath);
        renderer.printLine("Persisted:   " + context.sessionStore().sessionExists(sessionId, state.cwd()));
        renderer.printLine("Next step:   Open Claude Desktop and continue from this repository.");
    }

    private static boolean isSupportedPlatform() {
        var osName = System.getProperty("os.name", "").toLowerCase();
        var arch = System.getProperty("os.arch", "").toLowerCase();
        return osName.contains("mac") || (osName.contains("win") && (arch.equals("x86_64") || arch.equals("amd64")));
    }
}
