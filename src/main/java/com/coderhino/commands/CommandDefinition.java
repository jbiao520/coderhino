package com.coderhino.commands;

import java.util.List;

public interface CommandDefinition {
    String name();

    String description();

    default List<String> aliases() {
        return List.of();
    }

    default boolean hidden() {
        return false;
    }

    default boolean webCompatible() {
        return true;
    }

    default boolean includeInModelContext() {
        return true;
    }

    default boolean userInvocable() {
        return true;
    }

    void execute(CommandContext context, String args);
}
