package com.coderhino.web.terminal;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Path;

@Component
public class PtyTerminalProcessFactory implements TerminalProcessFactory {

    @Override
    public TerminalProcess create(Path cwd) throws IOException {
        return new PtyTerminalProcess(cwd);
    }
}
