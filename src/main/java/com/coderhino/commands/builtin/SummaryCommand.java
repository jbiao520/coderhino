package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.services.summary.FileChangeSummary;
import com.coderhino.services.summary.FileChangeSummaryFormatter;
import com.coderhino.services.summary.SessionEndSummary;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SummaryCommand implements CommandDefinition {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "summary";
    }

    @Override
    public String description() {
        return "Show a summary of the current conversation and file changes";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var messages = state.messages();
        var transcript = state.sessionRuntime().transcript();

        renderer.printLine("Conversation Summary");
        renderer.printLine("====================");
        renderer.printLine("  Session:   " + state.sessionRuntime().sessionId());
        if (state.sessionRuntime().customTitle() != null) {
            renderer.printLine("  Title:     " + state.sessionRuntime().customTitle());
        }
        renderer.printLine("  Model:     " + state.model());
        renderer.printLine("");

        if (messages.isEmpty() && transcript.isEmpty()) {
            renderer.printLine("  (No messages yet)");
        } else {
            int userMsgs = 0, assistantMsgs = 0, toolMsgs = 0;
            for (var msg : messages) {
                var type = msg.type();
                if ("user".equals(type)) userMsgs++;
                else if ("assistant".equals(type)) assistantMsgs++;
                else if ("tool".equals(type) || "tool_result".equals(type)) toolMsgs++;
                else assistantMsgs++;
            }

            renderer.printLine("  Message breakdown:");
            renderer.printLine("    User messages:      " + userMsgs);
            renderer.printLine("    Assistant messages:  " + assistantMsgs);
            renderer.printLine("    Tool results:       " + toolMsgs);
            renderer.printLine("    Total messages:     " + messages.size());
            renderer.printLine("    Transcript entries: " + transcript.size());
            renderer.printLine("");

            if (!transcript.isEmpty()) {
                var first = transcript.get(0);
                renderer.printLine("  First message (" + FORMATTER.format(first.timestamp()) + "):");
                renderer.printLine("    " + truncate(first.message().content(), 80));

                if (transcript.size() > 1) {
                    var last = transcript.get(transcript.size() - 1);
                    renderer.printLine("  Last message (" + FORMATTER.format(last.timestamp()) + "):");
                    renderer.printLine("    " + truncate(last.message().content(), 80));
                }
            } else if (!messages.isEmpty()) {
                renderer.printLine("  First message:");
                renderer.printLine("    " + truncate(messages.get(0).content(), 80));
                if (messages.size() > 1) {
                    renderer.printLine("  Last message:");
                    renderer.printLine("    " + truncate(messages.get(messages.size() - 1).content(), 80));
                }
            }
        }

        renderer.printLine("");
        renderer.printLine("  Total cost: $" + String.format("%.4f", state.totalCostUsd()));
        renderer.printLine("  Tool uses:  " + state.totalToolUses());

        var sessionId = state.sessionRuntime().sessionId();
        var summaryService = new SessionEndSummary(context.services().fileChangeTracker());
        var fileSummary = summaryService.buildSummary(sessionId);
        if (fileSummary.totalChanges() > 0) {
            renderer.printLine("");
            renderer.printLine(FileChangeSummaryFormatter.format(fileSummary));
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        var flat = text.replace('\n', ' ').replace('\r', ' ');
        if (flat.length() <= maxLen) return flat;
        return flat.substring(0, maxLen) + "...";
    }
}
