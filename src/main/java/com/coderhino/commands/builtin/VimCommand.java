package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.concurrent.atomic.AtomicBoolean;

public final class VimCommand implements CommandDefinition {
    private static final AtomicBoolean vimMode = new AtomicBoolean(false);

    public static void clearStore() {
        vimMode.set(false);
    }

    public static boolean isVimMode() {
        return vimMode.get();
    }

    @Override
    public String name() {
        return "vim";
    }

    @Override
    public String description() {
        return "Toggle vim keybinding mode for the REPL";
    }

    @Override
    public boolean webCompatible() {
        return false;
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty()) {
            renderer.printLine("Vim mode: " + (vimMode.get() ? "ON" : "OFF"));
            renderer.printLine("Usage: /vim [on|off|toggle]");
            renderer.printLine("");
            renderer.printLine("When enabled, REPL uses vim-style keybindings:");
            renderer.printLine("  Normal mode: h/j/k/l navigation, dd to delete line");
            renderer.printLine("  Insert mode: i to enter, Escape to return to normal");
            renderer.printLine("  Command mode: : to enter command prefix");
        } else if (sub.equals("on")) {
            vimMode.set(true);
            renderer.printLine("Vim mode enabled. Keybinding style: vim");
        } else if (sub.equals("off")) {
            vimMode.set(false);
            renderer.printLine("Vim mode disabled. Keybinding style: default");
        } else if (sub.equals("toggle")) {
            var previous = vimMode.get();
            vimMode.set(!previous);
            renderer.printLine("Vim mode: " + (!previous ? "ON" : "OFF"));
        } else {
            renderer.printLine("Usage: /vim [on|off|toggle]");
        }
    }
}
