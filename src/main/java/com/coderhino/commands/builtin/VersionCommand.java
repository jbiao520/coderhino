package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class VersionCommand implements CommandDefinition {
    private static final String VERSION = "claudecode-java 1.0.0-SNAPSHOT";

    @Override
    public String name() {
        return "version";
    }

    @Override
    public String description() {
        return "Print the version this session is running";
    }

    @Override
    public void execute(CommandContext context, String args) {
        context.out().println(VERSION);
    }
}
