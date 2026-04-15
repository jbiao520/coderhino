package com.coderhino.web.terminal;

import java.io.IOException;
import java.nio.file.Path;

@FunctionalInterface
public interface TerminalProcessFactory {

    TerminalProcess create(Path cwd) throws IOException;
}
