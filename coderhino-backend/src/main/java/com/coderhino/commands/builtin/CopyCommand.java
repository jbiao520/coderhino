package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import com.coderhino.types.Message;

import java.util.List;

public final class CopyCommand implements CommandDefinition {
    @Override
    public String name() {
        return "copy";
    }

    @Override
    public String description() {
        return "Display the last assistant response for copying";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var messages = context.bootstrapState().get().messages();

        if (messages.isEmpty()) {
            renderer.printLine("No messages in conversation yet.");
            return;
        }

        Message lastAssistant = null;
        for (int i = messages.size() - 1; i >= 0; i--) {
            var msg = messages.get(i);
            if (msg instanceof Message.AssistantMessage) {
                lastAssistant = msg;
                break;
            }
        }

        if (lastAssistant == null) {
            renderer.printLine("No assistant response found in conversation.");
            return;
        }

        renderer.printLine("Last assistant response:");
        renderer.printLine("---");
        renderer.printLine(lastAssistant.content());
        renderer.printLine("---");
        renderer.printLine("(Copy the text above to clipboard)");
    }
}
