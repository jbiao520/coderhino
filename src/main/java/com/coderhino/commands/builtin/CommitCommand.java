package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class CommitCommand implements CommandDefinition {
    private static final long TIMEOUT_SECONDS = 30;
    private static final Pattern SECRET_FILE_PATTERN = Pattern.compile(
        ".*(\\.env|credentials|\\.pem|\\.key|secrets?|config.*\\.json|\\.aws|\\.gcp).*",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String name() {
        return "commit";
    }

    @Override
    public String description() {
        return "Review uncommitted changes and scaffold a commit message";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        var cwd = state.cwd();
        PrintStream out = context.out();
        PrintStream err = context.err();

        try {
            var statusResult = runGitCommand(cwd, "git status --short");
            var branchResult = runGitCommand(cwd, "git branch --show-current");
            var diffResult = runGitCommand(cwd, "git diff HEAD");
            var recentCommitsResult = runGitCommand(cwd, "git log --oneline -10");

            var status = statusResult.stdout();
            var branch = branchResult.stdout().trim();
            var diff = diffResult.stdout();
            var recentCommits = recentCommitsResult.stdout();

            List<String> secretWarnings = detectSecretFiles(status);

            if (status.isEmpty()) {
                out.println("No changes to commit.");
                out.println();
                printRecentCommitsSummary(out, recentCommits);
                return;
            }

            out.println("=== Commit Review Scaffold ===");
            out.println();
            out.println("Branch: " + (branch.isEmpty() ? "(detached)" : branch));
            out.println();

            if (!secretWarnings.isEmpty()) {
                out.println("WARNING: Potential secret files detected:");
                for (var warning : secretWarnings) {
                    out.println("  - " + warning);
                }
                out.println("  Consider reviewing these files before committing.");
                out.println();
            }

            out.println("Changes:");
            out.println(status);
            out.println();

            if (!diff.isEmpty()) {
                out.println("Diff (HEAD):");
                var diffLines = diff.split("\n");
                if (diffLines.length > 100) {
                    for (var line : diffLines) {
                        out.println(line);
                    }
                    out.println("... (diff truncated, showing first 100 lines)");
                } else {
                    out.println(diff);
                }
                out.println();
            }

            if (!recentCommits.isEmpty()) {
                out.println("Recent commits (for style reference):");
                printRecentCommitsSummary(out, recentCommits);
                out.println();
            }

            out.println("=== Suggested Commit Message Structure ===");
            out.println();
            out.println("Format: <type>(<scope>): <short description>");
            out.println();
            out.println("Types: feat, fix, docs, style, refactor, test, chore, perf, ci, build, revert");
            out.println();
            out.println("Example:");
            out.println("  feat(auth): add OAuth2 login support");
            out.println("  fix(api): resolve null pointer in user endpoint");
            out.println("  docs(readme): update installation instructions");
            out.println();
            out.println("To commit, reply with your commit message in this format:");
            out.println("  /commit <your message here>");
            out.println();
            out.println("Or use conventional commit format:");
            out.println("  /commit feat: add new feature");

        } catch (Exception e) {
            err.println("Failed to gather git context: " + e.getMessage());
        }
    }

    private List<String> detectSecretFiles(String status) {
        List<String> warnings = new ArrayList<>();
        if (status == null || status.isEmpty()) {
            return warnings;
        }
        var lines = status.split("\n");
        for (var line : lines) {
            var parts = line.split("\\s+", 2);
            if (parts.length >= 2) {
                var filename = parts[1];
                if (SECRET_FILE_PATTERN.matcher(filename).matches()) {
                    warnings.add(filename);
                }
            }
        }
        return warnings;
    }

    private void printRecentCommitsSummary(PrintStream out, String recentCommits) {
        if (recentCommits == null || recentCommits.isEmpty()) {
            return;
        }
        var lines = recentCommits.split("\n");
        var count = Math.min(5, lines.length);
        for (var i = 0; i < count; i++) {
            out.println("  " + lines[i]);
        }
    }

    private GitResult runGitCommand(String cwd, String command) throws Exception {
        var process = new ProcessBuilder("/bin/zsh", "-c", command)
            .directory(java.nio.file.Path.of(cwd).toFile())
            .start();

        var finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            return new GitResult("", "Git command timed out after " + TIMEOUT_SECONDS + " seconds", -1);
        }

        try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
            var stdout = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
            var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
            return new GitResult(stdout, stderr, process.exitValue());
        }
    }

    private record GitResult(String stdout, String stderr, int exitCode) {
    }
}