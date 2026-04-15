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

public final class CommitPushPrCommand implements CommandDefinition {
    private static final long TIMEOUT_SECONDS = 30;
    private static final int DIFF_TRUNCATE_LINES = 200;
    private static final Pattern SECRET_FILE_PATTERN = Pattern.compile(
        ".*(\\.env|credentials|\\.pem|\\.key|secrets?|config.*\\.json|\\.aws|\\.gcp).*",
        Pattern.CASE_INSENSITIVE
    );

    @Override
    public String name() {
        return "commit-push-pr";
    }

    @Override
    public String description() {
        return "Commit all changes, push to origin, and open a pull request";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        var cwd = state.cwd();
        PrintStream out = context.out();

        var defaultBranch = detectDefaultBranch(cwd);

        var statusResult = runCommand(cwd, "git status");
        var diffHeadResult = runCommand(cwd, "git diff HEAD");
        var branchResult = runCommand(cwd, "git branch --show-current");
        var diffBaseResult = runCommand(cwd, "git diff " + defaultBranch + "...HEAD");
        var prViewResult = runCommand(cwd, "gh pr view --json number 2>/dev/null || true");

        var statusOutput = statusResult.stdout();
        var diffHeadOutput = truncateDiff(diffHeadResult.stdout());
        var branchOutput = branchResult.stdout().trim();
        var diffBaseOutput = truncateDiff(diffBaseResult.stdout());

        // Exit code 127 or stderr "command not found" means gh is not on PATH
        var prViewOutput = "";
        if (prViewResult.exitCode() != 127
                && !prViewResult.stderr().contains("command not found")) {
            prViewOutput = prViewResult.stdout();
        }

        List<String> secretFiles = detectSecretFiles(statusOutput);

        out.println("## Context");
        out.println();
        out.println("- `git status`: " + statusOutput);
        out.println("- `git diff HEAD`: " + diffHeadOutput);
        out.println("- `git branch --show-current`: " + branchOutput);
        out.println("- `git diff " + defaultBranch + "...HEAD`: " + diffBaseOutput);
        out.println("- `gh pr view --json number`: " + prViewOutput);
        out.println();

        if (!secretFiles.isEmpty()) {
            out.println("## WARNING: Potential secret files detected");
            out.println();
            for (var file : secretFiles) {
                out.println(file);
            }
            out.println("Consider reviewing these files before committing.");
            out.println();
        }

        out.println("## Git Safety Protocol");
        out.println();
        out.println("- NEVER update the git config");
        out.println("- NEVER run destructive/irreversible git commands (like push --force, hard reset, etc) unless the user explicitly requests them");
        out.println("- NEVER skip hooks (--no-verify, --no-gpg-sign, etc) unless the user explicitly requests it");
        out.println("- NEVER run force push to main/master, warn the user if they request it");
        out.println("- Do not commit files that likely contain secrets (.env, credentials.json, etc)");
        out.println("- Never use git commands with the -i flag (like git rebase -i or git add -i) since they require interactive input");
        out.println();

        out.println("## Your task");
        out.println();
        out.println("Analyze all changes that will be included in the pull request, making sure to look at all relevant commits (NOT just the latest commit, but ALL commits that will be included in the pull request from the git diff " + defaultBranch + "...HEAD output above).");
        out.println();
        out.println("Based on the above changes:");
        out.println("1. Create a new branch if on " + defaultBranch + " (use `whoami` for the branch name prefix, e.g., `username/feature-name`)");
        out.println("2. Create a single commit with an appropriate message using heredoc syntax:");
        out.println("```");
        out.println("git commit -m \"$(cat <<'EOF'");
        out.println("Commit message here.");
        out.println("EOF");
        out.println(")\"");
        out.println("```");
        out.println("3. Push the branch to origin");
        out.println("4. If a PR already exists for this branch (check the gh pr view output above), update the PR title and body using `gh pr edit`. Otherwise, create a pull request using `gh pr create` with heredoc syntax for the body:");
        out.println("```");
        out.println("gh pr create --title \"Short, descriptive title\" --body \"$(cat <<'EOF'");
        out.println("## Summary");
        out.println("<1-3 bullet points>");
        out.println();
        out.println("## Test plan");
        out.println("[Bulleted markdown checklist of TODOs for testing the pull request...]");
        out.println("EOF");
        out.println(")\"");
        out.println("```");
        out.println("   - IMPORTANT: Keep PR titles short (under 70 characters). Use the body for details.");
        out.println();
        out.println("You have the capability to call multiple tools in a single response. You MUST do all of the above in a single message.");
        out.println();
        out.println("Return the PR URL when you're done, so the user can see it.");

        if (args != null && !args.isBlank()) {
            out.println();
            out.println("## Additional instructions from user");
            out.println();
            out.println(args.trim());
        }
    }

    private String detectDefaultBranch(String cwd) {
        var result = runCommand(cwd, "git remote show origin");
        if (result.exitCode() == 0 && !result.stdout().isEmpty()) {
            var lines = result.stdout().split("\n");
            for (var line : lines) {
                var trimmed = line.trim();
                if (trimmed.startsWith("HEAD branch:")) {
                    var branch = trimmed.substring("HEAD branch:".length()).trim();
                    if (!branch.isEmpty() && !branch.equals("(unknown)")) {
                        return branch;
                    }
                }
            }
        }
        return "main";
    }

    private String truncateDiff(String output) {
        if (output == null || output.isEmpty()) {
            return output;
        }
        var lines = output.split("\n", -1);
        if (lines.length > DIFF_TRUNCATE_LINES) {
            var sb = new StringBuilder();
            for (var i = 0; i < DIFF_TRUNCATE_LINES; i++) {
                if (i > 0) sb.append("\n");
                sb.append(lines[i]);
            }
            sb.append("\n[... diff truncated ...]");
            return sb.toString();
        }
        return output;
    }

    private List<String> detectSecretFiles(String status) {
        var warnings = new ArrayList<String>();
        if (status == null || status.isEmpty()) {
            return warnings;
        }
        var lines = status.split("\n");
        for (var line : lines) {
            var parts = line.trim().split("\\s+", 2);
            if (parts.length >= 2) {
                var filename = parts[1];
                if (SECRET_FILE_PATTERN.matcher(filename).matches()) {
                    warnings.add(filename);
                }
            }
        }
        return warnings;
    }

    private CommandResult runCommand(String cwd, String command) {
        try {
            var process = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(java.nio.file.Path.of(cwd).toFile())
                .start();

            var finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return new CommandResult("", "timed out", -1);
            }

            try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                var stdout = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
                var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right);
                return new CommandResult(stdout, stderr, process.exitValue());
            }
        } catch (Exception e) {
            return new CommandResult("", e.getMessage(), -1);
        }
    }

    private record CommandResult(String stdout, String stderr, int exitCode) {}
}
