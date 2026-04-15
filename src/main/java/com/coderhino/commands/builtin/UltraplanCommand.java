package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.PrintStream;
import java.util.List;

public final class UltraplanCommand implements CommandDefinition {

    @Override
    public String name() {
        return "ultraplan";
    }

    @Override
    public String description() {
        return "Multi-agent planning session (requires Code Rhino on the web)";
    }

    @Override
    public List<String> aliases() {
        return List.of();
    }

    @Override
    public boolean hidden() {
        return true;
    }

    @Override
    public void execute(CommandContext context, String args) {
        PrintStream out = context.out();
        out.println("The /ultraplan command requires a Code Rhino web session (claude.ai/code).");
        out.println("It runs a multi-agent planning session in the cloud and is not available in this CLI version.");
        out.println();
        out.println("For local planning:");
        out.println("- Use /plan to toggle plan mode (Claude proposes actions before executing)");
        out.println();
        out.println("For cloud-powered planning, visit: https://code.claude.com");
    }
}
