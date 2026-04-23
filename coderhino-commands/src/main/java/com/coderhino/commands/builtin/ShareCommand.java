package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.types.Message;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ShareCommand implements CommandDefinition {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "share";
    }

    @Override
    public String description() {
        return "Format and share the current session transcript";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        if (sub.equals("transcript") || sub.isEmpty()) {
            var transcript = state.sessionRuntime().transcript();
            if (transcript.isEmpty()) {
                renderer.printLine("Nothing to share — session has no messages yet.");
                return;
            }

            var sessionId = state.sessionRuntime().sessionId();
            var title = state.sessionRuntime().customTitle();
            renderer.printLine("--- Session Transcript ---");
            renderer.printLine("Session ID: " + sessionId);
            if (title != null) {
                renderer.printLine("Title:      " + title);
            }
            renderer.printLine("Messages:   " + transcript.size());
            renderer.printLine("Model:      " + state.model());
            renderer.printLine("Cost:       $" + String.format("%.6f", state.totalCostUsd()));
            renderer.printLine("---");
            for (var envelope : transcript) {
                var ts = FORMATTER.format(envelope.timestamp());
                var msg = envelope.message();
                var role = msg.type().toUpperCase().replace('_', ' ');
                renderer.printLine("[" + ts + "] " + role + ":");
                for (var line : msg.content().split("\\R", -1)) {
                    renderer.printLine("  " + line);
                }
                renderer.printLine("");
            }
            renderer.printLine("--- End of Transcript ---");

        } else if (sub.startsWith("note ")) {
            var note = sub.substring(5).trim();
            renderer.printLine("Session shared with note: " + note);
            renderer.printLine("Session ID: " + state.sessionRuntime().sessionId());
            renderer.printLine("Messages:   " + state.sessionRuntime().transcript().size());
        } else {
            renderer.printLine("Usage: /share [transcript|note <message>]");
            renderer.printLine("  transcript    - Print full session transcript (default)");
            renderer.printLine("  note <msg>    - Share with an attached note");
        }
    }
}
