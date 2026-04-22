package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class DiffCommand implements CommandDefinition {
    private static final long TIMEOUT_SECONDS = 30;

    @Override
    public String name() {
        return "diff";
    }

    @Override
    public String description() {
        return "Show uncommitted changes or per-turn diffs";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        var cwd = state.cwd();
        var out = context.out();
        var err = context.err();

        String gitCommand;
        if (args == null || args.isEmpty() || "staged".equals(args)) {
            gitCommand = "git diff --cached";
        } else if ("unstaged".equals(args)) {
            gitCommand = "git diff";
        } else if ("head".equals(args)) {
            gitCommand = "git diff HEAD";
        } else if (args.startsWith("turn")) {
            gitCommand = "git log --oneline -1";
        } else {
            err.println("Unknown diff type: " + args);
            err.println("Usage: /diff [staged|unstaged|head|turn]");
            return;
        }

        try {
            var process = new ProcessBuilder("/bin/zsh", "-lc", gitCommand)
                .directory(java.nio.file.Path.of(cwd).toFile())
                .start();

            var finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                err.println("Git command timed out after " + TIMEOUT_SECONDS + " seconds");
                return;
            }

            try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                var stdout = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
                var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);

                if (!stdout.isEmpty()) {
                    out.println(stdout);
                }
                if (!stderr.isEmpty()) {
                    err.println(stderr);
                }

                if (process.exitValue() != 0 && stdout.isEmpty() && stderr.isEmpty()) {
                    err.println("Git command failed with exit code: " + process.exitValue());
                }
            }
        } catch (Exception e) {
            err.println("Failed to execute git command: " + e.getMessage());
        }
    }
}