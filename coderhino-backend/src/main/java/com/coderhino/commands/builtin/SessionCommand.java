package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class SessionCommand implements CommandDefinition {
    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    @Override
    public String name() {
        return "session";
    }

    @Override
    public String description() {
        return "Manage conversation sessions: list, save, load, delete";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        if (args == null || args.isBlank()) {
            renderer.printLine("Usage: /session <list|save|load|delete|show> [name|id]");
            return;
        }
        var parts = args.trim().split("\\s+", 2);
        var sub = parts[0];
        var arg = parts.length > 1 ? parts[1] : "";
        var state = context.bootstrapState().get();
        var store = context.sessionStore();

        switch (sub) {
            case "list" -> {
                var sessions = store.listSessions(state.cwd());
                if (sessions.isEmpty()) {
                    renderer.printLine("No saved sessions.");
                } else {
                    renderer.printLine("Saved sessions (" + sessions.size() + "):");
                    for (var s : sessions) {
                        var ts = FORMATTER.format(s.updatedAt());
                        var title = s.customTitle() != null ? s.customTitle()
                            : (s.firstPrompt() != null ? truncate(s.firstPrompt(), 40) : "(no title)");
                        renderer.printLine("  " + s.sessionId() + "  [" + ts + "]  " + s.messageCount() + " msgs  " + title);
                    }
                }
            }
            case "show" -> {
                if (arg.isBlank()) {
                    var runtime = state.sessionRuntime();
                    renderer.printLine("Current session:");
                    renderer.printLine("  id:       " + runtime.sessionId());
                    renderer.printLine("  title:    " + (runtime.customTitle() != null ? runtime.customTitle() : "(none)"));
                    renderer.printLine("  messages: " + runtime.transcript().size());
                } else {
                    try {
                        var sessionId = UUID.fromString(arg);
                        var runtime = store.loadSession(sessionId, state.cwd());
                        renderer.printLine("Session " + sessionId + ":");
                        renderer.printLine("  title:    " + (runtime.customTitle() != null ? runtime.customTitle() : "(none)"));
                        renderer.printLine("  messages: " + runtime.transcript().size());
                    } catch (IllegalArgumentException e) {
                        renderer.printLine("Invalid session UUID: " + arg);
                    }
                }
            }
            case "save" -> {
                var title = arg.isBlank() ? null : arg;
                if (title != null) {
                    store.saveCustomTitle(state, title);
                    context.bootstrapState().update(s ->
                        s.withSessionRuntime(s.sessionRuntime().withCustomTitle(title)));
                    renderer.printLine("Session title saved: " + title);
                } else {
                    renderer.printLine("Session is auto-saved. Use /session save <title> to set a custom title.");
                }
            }
            case "load" -> {
                if (arg.isBlank()) {
                    renderer.printLine("Usage: /session load <session-id>");
                    return;
                }
                try {
                    var sessionId = UUID.fromString(arg);
                    if (!store.sessionExists(sessionId, state.cwd())) {
                        renderer.printLine("Session not found: " + sessionId);
                        return;
                    }
                    var runtime = store.loadSession(sessionId, state.cwd());
                    context.bootstrapState().update(s -> s.withSessionRuntime(runtime).clearMessages());
                    renderer.printLine("Loaded session " + sessionId + " (" + runtime.transcript().size() + " messages)");
                    if (runtime.customTitle() != null) {
                        renderer.printLine("  Title: " + runtime.customTitle());
                    }
                } catch (IllegalArgumentException e) {
                    renderer.printLine("Invalid session UUID: " + arg);
                }
            }
            case "delete" -> {
                if (arg.isBlank()) {
                    renderer.printLine("Usage: /session delete <session-id>");
                    return;
                }
                try {
                    var sessionId = UUID.fromString(arg);
                    if (!store.sessionExists(sessionId, state.cwd())) {
                        renderer.printLine("Session not found: " + sessionId);
                        return;
                    }
                    store.deleteSession(sessionId, state.cwd());
                    renderer.printLine("Session deleted: " + sessionId);
                } catch (IllegalArgumentException e) {
                    renderer.printLine("Invalid session UUID: " + arg);
                }
            }
            default -> renderer.printLine("Unknown session sub-command: " + sub
                + ". Try: list, show, save, load, delete");
        }
    }

    private static String truncate(String text, int maxLen) {
        if (text == null) return "";
        var flat = text.replace('\n', ' ');
        return flat.length() <= maxLen ? flat : flat.substring(0, maxLen) + "...";
    }
}
