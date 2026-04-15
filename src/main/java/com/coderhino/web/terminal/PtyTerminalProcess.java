package com.coderhino.web.terminal;

import com.pty4j.PtyProcess;
import com.pty4j.PtyProcessBuilder;
import com.pty4j.WinSize;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

final class PtyTerminalProcess implements TerminalProcess {

    private final PtyProcess process;
    private final AtomicBoolean started = new AtomicBoolean(false);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    PtyTerminalProcess(Path cwd) throws IOException {
        var normalizedCwd = cwd.toAbsolutePath().normalize();
        var environment = new HashMap<>(System.getenv());
        var shell = resolveShell(environment);
        environment.putIfAbsent("TERM", "xterm-256color");
        var command = buildCommand(shell);
        this.process = new PtyProcessBuilder(command)
            .setDirectory(normalizedCwd.toString())
            .setEnvironment(environment)
            .setInitialColumns(120)
            .setInitialRows(36)
            .start();
    }

    @Override
    public void start(TerminalListener listener) {
        if (!started.compareAndSet(false, true)) {
            return;
        }

        var readerThread = new Thread(() -> {
            try (var input = process.getInputStream()) {
                var buffer = new byte[4096];
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    if (read == 0) {
                        continue;
                    }
                    listener.onOutput(new String(buffer, 0, read, StandardCharsets.UTF_8));
                }
            } catch (IOException e) {
                if (!closed.get()) {
                    listener.onError(e);
                }
            }
        }, "terminal-pty-reader-" + Integer.toHexString(System.identityHashCode(this)));
        readerThread.setDaemon(true);
        readerThread.start();

        var waiterThread = new Thread(() -> {
            try {
                var exitCode = process.waitFor();
                listener.onExit(exitCode);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (!closed.get()) {
                    listener.onError(e);
                }
            }
        }, "terminal-pty-waiter-" + Integer.toHexString(System.identityHashCode(this)));
        waiterThread.setDaemon(true);
        waiterThread.start();
    }

    @Override
    public void write(String data) throws IOException {
        var bytes = data.getBytes(StandardCharsets.UTF_8);
        synchronized (process.getOutputStream()) {
            process.getOutputStream().write(bytes);
            process.getOutputStream().flush();
        }
    }

    @Override
    public void resize(int cols, int rows) throws IOException {
        if (cols <= 0 || rows <= 0) {
            return;
        }
        process.setWinSize(new WinSize(cols, rows));
    }

    @Override
    public boolean isAlive() {
        return process.isAlive();
    }

    @Override
    public void close() {
        closed.set(true);
        process.destroy();
    }

    private static String resolveShell(Map<String, String> environment) {
        var configuredShell = environment.get("SHELL");
        if (configuredShell != null && !configuredShell.isBlank() && Files.isExecutable(Path.of(configuredShell))) {
            return configuredShell;
        }
        if (isWindows()) {
            return environment.getOrDefault("COMSPEC", "cmd.exe");
        }
        if (Files.isExecutable(Path.of("/bin/zsh"))) {
            return "/bin/zsh";
        }
        if (Files.isExecutable(Path.of("/bin/bash"))) {
            return "/bin/bash";
        }
        return "/bin/sh";
    }

    private static String[] buildCommand(String shell) {
        if (shell.endsWith("cmd.exe")) {
            return new String[]{shell};
        }

        var command = new ArrayList<String>();
        command.add(shell);

        var fileName = Path.of(shell).getFileName();
        var executableName = fileName != null ? fileName.toString() : shell;
        if ("bash".equals(executableName)) {
            command.add("--login");
            command.add("-i");
        } else if ("zsh".equals(executableName)) {
            command.add("-il");
        } else {
            command.add("-i");
        }

        return command.toArray(String[]::new);
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().contains("win");
    }
}
