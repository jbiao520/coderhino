package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class FeedbackCommand implements CommandDefinition {
    @Override
    public String name() {
        return "feedback";
    }

    @Override
    public String description() {
        return "Send feedback about Code Rhino";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args != null && !args.isBlank()) {
            renderer.printLine("Feedback submitted: " + args);
            renderer.printLine("Thank you for your feedback!");
        } else {
            renderer.printLine("Feedback: please provide your feedback after the /feedback command.");
            renderer.printLine("Example: /feedback I love the new compact mode!");
        }
    }
}
