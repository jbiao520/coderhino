package com.coderhino.tools.runtime;

import java.util.Optional;

public interface ToolCommandRegistry {
    Optional<ToolCommand> find(String name);
}
