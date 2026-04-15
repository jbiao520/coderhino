package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

public final class ConfigCommand implements CommandDefinition {
    @Override
    public String name() {
        return "config";
    }

    @Override
    public String description() {
        return "Show the current Java rewrite foundation config";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        context.out().printf("model=%s verbose=%s cwd=%s permissionMode=%s messages=%d%n",
            state.model(),
            state.verbose(),
            state.cwd(),
            state.permissionMode(),
            state.messages().size());
        context.out().printf("sessionId=%s title=%s%n", state.sessionRuntime().sessionId(), state.sessionRuntime().customTitle());
        context.out().printf("mcpServers=%d lspServers=%d%n",
            context.services().mcp().definitions().size(),
            context.services().lsp().definitions().size());
    }
}
