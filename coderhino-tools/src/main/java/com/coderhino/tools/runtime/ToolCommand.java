package com.coderhino.tools.runtime;

import java.util.List;

public interface ToolCommand {
    String name();

    String description();

    boolean includeInModelContext();

    default boolean promptBacked() {
        return false;
    }

    default String prompt(String args) {
        throw new UnsupportedOperationException("Command is not prompt-backed: " + name());
    }

    default List<String> allowedTools() {
        return List.of();
    }
}
