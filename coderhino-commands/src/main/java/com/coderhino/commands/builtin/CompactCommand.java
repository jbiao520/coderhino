package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.types.Message;

import java.util.List;

public final class CompactCommand implements CommandDefinition {

    @Override
    public String name() {
        return "compact";
    }

    @Override
    public String description() {
        return "Compact conversation history by summarizing earlier messages into a smaller active transcript";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var messages = List.copyOf(state.messages());

        if (messages.isEmpty()) {
            renderer.printLine("No conversation to compact.");
            return;
        }

        try {
            var result = context.services().commandCompact().compactManual(messages, normalizeInstructions(args));
            if (!result.wasCompacted()) {
                if (result.originalMessageCount() == 0 || result.compactedMessages().isEmpty()) {
                    renderer.printLine("No conversation to compact.");
                } else {
                    renderer.printLine("Not enough conversation history to compact yet.");
                }
                return;
            }

            var updatedMessages = result.compactedMessages();
            var updatedEnvelopes = context.sessionStore().rewrapMessages(state, updatedMessages);
            context.sessionStore().replaceTranscript(state, updatedEnvelopes);
            context.bootstrapState().update(current -> current
                .withMessages(updatedMessages)
                .withSessionRuntime(current.sessionRuntime().replaceTranscript(updatedEnvelopes))
            );

            renderer.printLine(buildSuccessMessage(messages.size(), updatedMessages));
        } catch (RuntimeException exception) {
            renderer.printLine("Compaction failed: " + exception.getMessage());
        }
    }

    private String normalizeInstructions(String args) {
        if (args == null) {
            return null;
        }
        var trimmed = args.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String buildSuccessMessage(int originalCount, List<Message> compactedMessages) {
        int reducedBy = Math.max(0, originalCount - compactedMessages.size());
        return "Compaction complete. Reduced conversation history by " + reducedBy + " messages.";
    }
}
