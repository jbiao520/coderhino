package com.coderhino.commands;

import com.coderhino.cli.TerminalRenderer;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionStore;
import com.coderhino.tools.runtime.CommandServices;

import java.io.PrintStream;

public record CommandContext(
    BootstrapState bootstrapState,
    CommandRegistry registry,
    SessionStore sessionStore,
    CommandServices services,
    PromptCommandExecutor promptExecutor,
    TerminalRenderer renderer,
    PrintStream out,
    PrintStream err
) {
}
