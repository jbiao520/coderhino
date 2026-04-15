package com.coderhino.commands;

import com.coderhino.cli.ConsoleRenderer;
import com.coderhino.cli.TerminalRenderer;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.services.ServiceRegistry;

import java.io.PrintStream;

public record CommandContext(
    BootstrapState bootstrapState,
    CommandRegistry registry,
    SessionStore sessionStore,
    ServiceRegistry services,
    PromptCommandExecutor promptExecutor,
    PrintStream out,
    PrintStream err
) {
    public TerminalRenderer renderer() {
        return new ConsoleRenderer(out, err);
    }
}
