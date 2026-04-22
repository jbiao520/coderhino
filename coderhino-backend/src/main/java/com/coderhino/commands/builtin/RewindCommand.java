package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class RewindCommand implements CommandDefinition {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "rewind";
    }

    @Override
    public String description() {
        return "Navigate session history; list or rewind to a previous conversation point";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty() || sub.equals("list")) {
            var transcript = state.sessionRuntime().transcript();
            if (transcript.isEmpty()) {
                renderer.printLine("No session history yet.");
            } else {
                renderer.printLine("Session history (" + transcript.size() + " messages):");
                int idx = 0;
                for (var envelope : transcript) {
                    var ts = FORMATTER.format(envelope.timestamp());
                    var msgType = envelope.message().type();
                    var preview = truncate(envelope.message().content(), 60);
                    renderer.printLine("  [" + idx + "] " + ts + " " + msgType + ": " + preview);
                    idx++;
                }
            }
        } else if (sub.startsWith("show ")) {
            var idxStr = sub.substring(5).trim();
            try {
                int idx = Integer.parseInt(idxStr);
                var transcript = state.sessionRuntime().transcript();
                if (idx < 0 || idx >= transcript.size()) {
                    renderer.printLine("Index out of range: " + idx + " (session has " + transcript.size() + " messages)");
                    return;
                }
                var envelope = transcript.get(idx);
                renderer.printLine("Message [" + idx + "]:");
                renderer.printLine("  timestamp: " + FORMATTER.format(envelope.timestamp()));
                renderer.printLine("  type:      " + envelope.message().type());
                renderer.printLine("  uuid:      " + envelope.uuid());
                renderer.printLine("  content:");
                for (var line : envelope.message().content().split("\\R", -1)) {
                    renderer.printLine("    " + line);
                }
            } catch (NumberFormatException e) {
                renderer.printLine("Invalid index: " + idxStr + ". Use a numeric index from /rewind list.");
            }
        } else if (sub.startsWith("jump ")) {
            var idxStr = sub.substring(5).trim();
            try {
                int idx = Integer.parseInt(idxStr);
                var transcript = state.sessionRuntime().transcript();
                if (idx < 0 || idx >= transcript.size()) {
                    renderer.printLine("Index out of range: " + idx + " (session has " + transcript.size() + " messages)");
                    return;
                }
                var truncated = transcript.subList(0, idx);
                context.bootstrapState().update(s ->
                    s.withSessionRuntime(s.sessionRuntime().replaceTranscript(truncated))
                      .clearMessages()
                );
                renderer.printLine("Rewound to message [" + idx + "]. Session now has " + idx + " messages.");
                renderer.printLine("Note: future messages will branch from this point.");
            } catch (NumberFormatException e) {
                renderer.printLine("Invalid index: " + idxStr + ". Use a numeric index from /rewind list.");
            }
        } else {
            renderer.printLine("Usage: /rewind [list|show <idx>|jump <idx>]");
            renderer.printLine("  list       - List session history with timestamps (default)");
            renderer.printLine("  show <idx> - Show full content of message at index");
            renderer.printLine("  jump <idx> - Rewind session to message at index (truncates history)");
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "(null)";
        var flat = text.replace('\n', ' ').replace('\r', ' ');
        if (flat.length() <= maxLen) return flat;
        return flat.substring(0, maxLen) + "...";
    }
}
