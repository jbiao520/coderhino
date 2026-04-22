package com.coderhino.context;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public final class ContextCollector {
    private static final int MAX_STATUS_CHARS = 2000;

    public ContextSnapshot collect(Path cwd) {
        var systemFuture = CompletableFuture.supplyAsync(() -> buildSystemContext(cwd));
        var userFuture = CompletableFuture.supplyAsync(() -> buildUserContext(cwd));
        return new ContextSnapshot(systemFuture.join(), userFuture.join());
    }

    private String buildSystemContext(Path cwd) {
        var sections = new ArrayList<String>();

        var envInfo = buildEnvironmentInfo(cwd);
        if (!envInfo.isBlank()) {
            sections.add(envInfo);
        }

        var gitContext = buildGitStatusContext(cwd);
        if (!gitContext.isBlank()) {
            sections.add(gitContext);
        }

        return String.join(System.lineSeparator() + System.lineSeparator(), sections);
    }

    private String buildEnvironmentInfo(Path cwd) {
        var lines = new ArrayList<String>();
        lines.add("Working directory: " + cwd.toAbsolutePath().normalize());

        var osName = System.getProperty("os.name", "");
        var osArch = System.getProperty("os.arch", "");
        if (!osName.isBlank()) {
            lines.add("Platform: " + osName + " (" + osArch + ")");
        }

        var userName = System.getProperty("user.name", "");
        if (!userName.isBlank()) {
            lines.add("User: " + userName);
        }

        var shell = System.getenv("SHELL");
        if (shell != null && !shell.isBlank()) {
            lines.add("Shell: " + shell);
        }

        return String.join(System.lineSeparator(), lines);
    }

    private String buildUserContext(Path cwd) {
        var sections = new ArrayList<String>();
        var claudeMd = loadClaudeMdContext(cwd);
        if (!claudeMd.isBlank()) {
            sections.add(claudeMd);
        }
        sections.add("Today's date is " + LocalDate.now() + ".");
        return String.join(System.lineSeparator() + System.lineSeparator(), sections);
    }

    private String buildGitStatusContext(Path cwd) {
        try {
            if (!isGitRepository(cwd)) {
                return "";
            }

            var branch = run(cwd, "git", "rev-parse", "--abbrev-ref", "HEAD");
            var defaultBranch = firstNonBlank(
                run(cwd, "git", "symbolic-ref", "refs/remotes/origin/HEAD"),
                "origin/HEAD"
            );
            if (defaultBranch.startsWith("refs/remotes/origin/")) {
                defaultBranch = defaultBranch.substring("refs/remotes/origin/".length());
            }
            var status = run(cwd, "git", "status", "--short");
            var recentCommits = run(cwd, "git", "log", "--oneline", "-n", "5");
            var userName = run(cwd, "git", "config", "user.name");

            if (branch.isBlank()) {
                return "";
            }

            return List.of(
                    "This is the git status at the start of the conversation. Note that this status is a snapshot in time, and will not update during the conversation.",
                    "Current branch: " + branch,
                    "Main branch (you will usually use this for PRs): " + (defaultBranch.isBlank() ? "unknown" : defaultBranch),
                    userName.isBlank() ? null : "Git user: " + userName,
                    "Status:\n" + truncateStatus(status),
                    recentCommits.isBlank() ? null : "Recent commits:\n" + recentCommits
                ).stream()
                .filter(value -> value != null && !value.isBlank())
                .collect(Collectors.joining(System.lineSeparator() + System.lineSeparator()));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String run(Path cwd, String... command) throws IOException, InterruptedException {
        var process = new ProcessBuilder(command)
            .directory(cwd.toFile())
            .start();
        var stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        var exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException(stderr.isBlank() ? "Command failed with exit code %d".formatted(exitCode) : stderr);
        }
        return output;
    }

    private boolean isGitRepository(Path cwd) {
        try {
            run(cwd, "git", "rev-parse", "--is-inside-work-tree");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private String loadClaudeMdContext(Path cwd) {
        var sections = new ArrayList<String>();
        for (Path candidate : List.of(cwd.resolve("CLAUDE.md"), cwd.resolve(".claude").resolve("CLAUDE.md"))) {
            if (!Files.exists(candidate)) {
                continue;
            }
            try {
                var content = Files.readString(candidate, StandardCharsets.UTF_8).trim();
                if (!content.isBlank()) {
                    sections.add(content);
                }
            } catch (IOException ignored) {
            }
        }
        return String.join(System.lineSeparator() + System.lineSeparator(), sections);
    }

    private String truncateStatus(String status) {
        if (status == null || status.isBlank()) {
            return "(clean)";
        }
        if (status.length() <= MAX_STATUS_CHARS) {
            return status;
        }
        return status.substring(0, MAX_STATUS_CHARS)
            + "\n... (truncated because it exceeds 2k characters. If you need more information, run \"git status\" using BashTool)";
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
