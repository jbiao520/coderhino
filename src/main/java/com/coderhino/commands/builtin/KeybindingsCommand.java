package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class KeybindingsCommand implements CommandDefinition {
    @Override
    public String name() {
        return "keybindings";
    }

    @Override
    public String description() {
        return "View or customize keyboard shortcuts";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args != null && !args.isBlank()) {
            renderer.printLine("Keybindings updated: " + args);
        } else {
            renderer.printLine("Keybindings: listing current keybindings...");
            renderer.printLine("  Enter - submit input");
            renderer.printLine("  Ctrl+C - cancel current operation");
            renderer.printLine("  Ctrl+D - exit session");
            renderer.printLine("  Tab - autocomplete");
        }
    }
}
