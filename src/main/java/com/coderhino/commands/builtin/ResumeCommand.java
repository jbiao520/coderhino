package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.UUID;

public final class ResumeCommand implements CommandDefinition {
    @Override
    public String name() {
        return "resume";
    }

    @Override
    public String description() {
        return "Resume a persisted session by UUID or list recent sessions";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var cwd = context.bootstrapState().get().cwd();
        if (args == null || args.isBlank()) {
            var sessions = context.sessionStore().listSessions(cwd);
            if (sessions.isEmpty()) {
                context.out().println("No saved sessions found.");
                return;
            }
            sessions.forEach(session -> context.out().printf("%s %s messages=%d title=%s prompt=%s%n",
                session.sessionId(),
                session.updatedAt(),
                session.messageCount(),
                session.customTitle(),
                session.firstPrompt()));
            return;
        }

        var sessionId = UUID.fromString(args.trim());
        var runtime = context.sessionStore().loadSession(sessionId, cwd);
        context.bootstrapState().update(state -> state.clearMessages().withSessionRuntime(runtime));
        runtime.transcript().forEach(envelope -> context.bootstrapState().addMessage(envelope.message()));
        context.out().printf("Resumed session %s with %d messages.%n", sessionId, runtime.transcript().size());
    }
}
