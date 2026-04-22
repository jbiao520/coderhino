package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class ThemeCommand implements CommandDefinition {
    @Override
    public String name() {
        return "theme";
    }

    @Override
    public String description() {
        return "Change the terminal theme";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args != null && !args.isBlank()) {
            renderer.printLine("Theme set to: " + args);
        } else {
            renderer.printLine("Theme command: available themes: default, dark, light");
        }
    }
}
