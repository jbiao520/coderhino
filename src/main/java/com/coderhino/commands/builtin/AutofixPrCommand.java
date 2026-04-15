package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.PrintStream;

public final class AutofixPrCommand implements CommandDefinition {

    @Override
    public String name() {
        return "autofix-pr";
    }

    @Override
    public String description() {
        return "Automatically fix CI/PR issues (not yet available)";
    }

    @Override
    public boolean hidden() {
        return true;
    }

    @Override
    public void execute(CommandContext context, String args) {
        PrintStream out = context.out();
        out.println("The /autofix-pr command is not yet available in this version.");
        out.println("It will automatically detect and fix failing CI checks and PR review comments in a future release.");
        out.println();
        out.println("For now, you can:");
        out.println("- Use /commit-push-pr to commit and open a PR");
        out.println("- Use /review to review an existing PR");
    }
}
