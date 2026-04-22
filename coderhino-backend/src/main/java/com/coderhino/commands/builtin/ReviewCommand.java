package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

public final class ReviewCommand implements CommandDefinition {
    private static final long TIMEOUT_SECONDS = 30;

    @Override
    public String name() {
        return "review";
    }

    @Override
    public String description() {
        return "Review open pull requests or a specific PR";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var state = context.bootstrapState().get();
        var cwd = state.cwd();
        var out = context.out();
        var err = context.err();

        if (args == null || args.isBlank()) {
            listOpenPullRequests(context, cwd, out, err);
        } else {
            reviewPullRequest(context, cwd, args.trim(), out, err);
        }
    }

    private void listOpenPullRequests(CommandContext context, String cwd, java.io.PrintStream out, java.io.PrintStream err) {
        try {
            var process = new ProcessBuilder("/bin/zsh", "-lc", "gh pr list --json number,title,state,url,author")
                .directory(java.nio.file.Path.of(cwd).toFile())
                .start();

            var finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                err.println("gh pr list timed out after " + TIMEOUT_SECONDS + " seconds");
                return;
            }

            try (var stdoutReader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
                 var stderrReader = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                var stdout = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
                var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);

                if (process.exitValue() == 0 && !stdout.isEmpty()) {
                    printPullRequestList(out, stdout);
                } else if (!stderr.isEmpty() && (stderr.contains("gh: command not found") || stderr.contains("not installed"))) {
                    printGhNotAvailableFallback(out);
                } else if (!stderr.isEmpty()) {
                    err.println("gh pr list error: " + stderr);
                } else {
                    out.println("No open pull requests found.");
                }
            }
        } catch (Exception e) {
            printGhNotAvailableFallback(out);
        }
    }

    private void printPullRequestList(java.io.PrintStream out, String jsonOutput) {
        out.println("Open Pull Requests:");
        out.println("=".repeat(60));

        // Simple JSON parsing for gh pr list output
        // Format: [{"number":1,"title":"...","state":"OPEN","url":"...","author":{"login":"..."}}]
        var lines = jsonOutput.split("\n");
        for (var line : lines) {
            line = line.trim();
            if (line.startsWith("{")) {
                var number = extractJsonField(line, "number");
                var title = extractJsonField(line, "title");
                var url = extractJsonField(line, "url");
                var author = extractJsonField(line, "author");
                out.printf("PR #%s: %s%n", number, title);
                out.printf("  Author: %s | URL: %s%n", author, url);
                out.println("-".repeat(60));
            }
        }
    }

    private String extractJsonField(String json, String field) {
        // Simple regex-based JSON field extraction
        var pattern = "\"" + field + "\"\\s*:\\s*";
        var matcher = java.util.regex.Pattern.compile(pattern);
        var m = matcher.matcher(json);
        if (m.find()) {
            var start = m.end();
            if (start < json.length()) {
                var c = json.charAt(start);
                if (c == '"') {
                    // String value
                    var innerQuote = json.indexOf('"', start + 1);
                    var comma = json.indexOf(',', start + 1);
                    var closeBrace = json.indexOf('}', start + 1);
                    var end = minNonNegative(innerQuote, comma, closeBrace);
                    if (end > start + 1) {
                        return json.substring(start + 1, end);
                    }
                } else if (Character.isDigit(c)) {
                    // Numeric value
                    var end = start;
                    while (end < json.length() && (Character.isDigit(json.charAt(end)) || json.charAt(end) == '.')) {
                        end++;
                    }
                    return json.substring(start, end);
                }
            }
        }
        return "unknown";
    }

    private int minNonNegative(Integer... values) {
        int min = Integer.MAX_VALUE;
        for (var v : values) {
            if (v >= 0 && v < min) {
                min = v;
            }
        }
        return min == Integer.MAX_VALUE ? -1 : min;
    }

    private void printGhNotAvailableFallback(java.io.PrintStream out) {
        out.println("GitHub CLI (gh) is not available.");
        out.println("To use /review, please install the GitHub CLI:");
        out.println("  macOS: brew install gh");
        out.println("  Linux: sudo apt install gh");
        out.println("  Windows: winget install GitHub.cli");
        out.println("");
        out.println("Alternatively, provide a PR number: /review <pr-number>");
    }

    private void reviewPullRequest(CommandContext context, String cwd, String prNumber, java.io.PrintStream out, java.io.PrintStream err) {
        // Validate PR number
        int prNum;
        try {
            prNum = Integer.parseInt(prNumber);
            if (prNum <= 0) {
                err.println("Invalid PR number: " + prNumber + ". Must be a positive integer.");
                return;
            }
        } catch (NumberFormatException e) {
            err.println("Invalid PR number: " + prNumber + ". Must be a positive integer.");
            return;
        }

        try {
            // Fetch PR view and diff in parallel would be ideal, but ProcessBuilder is sequential
            // First get PR info
            var viewProcess = new ProcessBuilder("/bin/zsh", "-lc",
                    "gh pr view " + prNum + " --json number,title,state,url,author,body,additions,deletions,changedFiles")
                .directory(java.nio.file.Path.of(cwd).toFile())
                .start();

            var viewFinished = viewProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!viewFinished) {
                viewProcess.destroyForcibly();
                err.println("gh pr view timed out after " + TIMEOUT_SECONDS + " seconds");
                return;
            }

            String viewOutput = "";
            try (var stdoutReader = new BufferedReader(new InputStreamReader(viewProcess.getInputStream(), StandardCharsets.UTF_8));
                 var stderrReader = new BufferedReader(new InputStreamReader(viewProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                viewOutput = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
                var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);

                if (viewProcess.exitValue() != 0) {
                    if (stderr.contains("gh: command not found") || stderr.contains("not installed")) {
                        err.println("GitHub CLI (gh) is not available. Cannot review PR #" + prNum);
                        return;
                    }
                    err.println("Failed to fetch PR #" + prNum + ": " + stderr);
                    return;
                }
            }

            // Now get the diff
            var diffProcess = new ProcessBuilder("/bin/zsh", "-lc", "gh pr diff " + prNum)
                .directory(java.nio.file.Path.of(cwd).toFile())
                .start();

            var diffFinished = diffProcess.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!diffFinished) {
                diffProcess.destroyForcibly();
                err.println("gh pr diff timed out after " + TIMEOUT_SECONDS + " seconds");
                return;
            }

            String diffOutput = "";
            try (var stdoutReader = new BufferedReader(new InputStreamReader(diffProcess.getInputStream(), StandardCharsets.UTF_8));
                 var stderrReader = new BufferedReader(new InputStreamReader(diffProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                diffOutput = stdoutReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);
                var stderr = stderrReader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + System.lineSeparator() + right);

                if (diffProcess.exitValue() != 0 && !stderr.isEmpty()) {
                    err.println("gh pr diff error: " + stderr);
                    // Continue anyway - diff is supplementary
                }
            }

            // Print structured review scaffold
            printReviewScaffold(out, viewOutput, diffOutput, prNum);

        } catch (Exception e) {
            err.println("Failed to review PR #" + prNum + ": " + e.getMessage());
        }
    }

    private void printReviewScaffold(java.io.PrintStream out, String prViewJson, String diffOutput, int prNum) {
        var number = extractJsonField(prViewJson, "number");
        var title = extractJsonField(prViewJson, "title");
        var state = extractJsonField(prViewJson, "state");
        var url = extractJsonField(prViewJson, "url");
        var author = extractJsonField(prViewJson, "author");
        var body = extractJsonField(prViewJson, "body");
        var additions = extractJsonField(prViewJson, "additions");
        var deletions = extractJsonField(prViewJson, "deletions");
        var changedFiles = extractJsonField(prViewJson, "changedFiles");

        // Truncate body if too long
        if (body.length() > 500) {
            body = body.substring(0, 500) + "...";
        }

        out.println("=".repeat(70));
        out.printf("PR REVIEW #%s: %s%n", number, title);
        out.println("=".repeat(70));
        out.printf("State: %s | Author: %s%n", state, author);
        out.printf("URL: %s%n", url);
        out.println("-".repeat(70));

        out.println("## Overview");
        out.println("-".repeat(70));
        out.printf("Files changed: %s | +%s / -%s lines%n", changedFiles, additions, deletions);
        if (!body.isEmpty() && !body.equals("unknown")) {
            out.println("Description:");
            out.println(body);
        } else {
            out.println("(No description provided)");
        }
        out.println();

        out.println("## Risks");
        out.println("-".repeat(70));
        out.println("- [ ] Breaking changes to existing APIs or contracts");
        out.println("- [ ] New external dependencies or network calls");
        out.println("- [ ] Security implications (user input, authentication, data handling)");
        out.println("- [ ] Database migrations or schema changes");
        out.println("- [ ] Potential for data loss");
        out.println();

        out.println("## Correctness");
        out.println("-".repeat(70));
        out.println("- [ ] Logic errors or off-by-one bugs");
        out.println("- [ ] Error handling for edge cases");
        out.println("- [ ] Input validation and sanitization");
        out.println("- [ ] Concurrency issues (if applicable)");
        out.println("- [ ] Proper error messages and logging");
        out.println();

        out.println("## Performance");
        out.println("-".repeat(70));
        out.println("- [ ] No N+1 query patterns or unnecessary iterations");
        out.println("- [ ] Appropriate use of caching");
        out.println("- [ ] No memory leaks or unbounded growth");
        out.println("- [ ] Efficient data structures and algorithms");
        out.println();

        out.println("## Tests");
        out.println("-".repeat(70));
        out.println("- [ ] Unit tests cover core logic paths");
        out.println("- [ ] Integration tests verify end-to-end behavior");
        out.println("- [ ] Edge cases and error conditions are tested");
        out.println("- [ ] Test coverage is adequate");
        out.println();

        out.println("## Security");
        out.println("-".repeat(70));
        out.println("- [ ] No hardcoded secrets or credentials");
        out.println("- [ ] Proper input sanitization to prevent injection attacks");
        out.println("- [ ] Authentication/authorization correctly enforced");
        out.println("- [ ] Sensitive data is not logged or exposed");
        out.println();

        out.println("## Diff Summary");
        out.println("-".repeat(70));

        if (diffOutput.isEmpty()) {
            out.println("(No diff available)");
        } else {
            // Print a preview of the diff (first 50 lines)
            var diffLines = diffOutput.split("\n");
            var previewLines = Math.min(diffLines.length, 50);
            for (int i = 0; i < previewLines; i++) {
                out.println(diffLines[i]);
            }
            if (diffLines.length > 50) {
                out.println("... (" + (diffLines.length - 50) + " more lines)");
            }
        }

        out.println();
        out.println("=".repeat(70));
        out.println("Review checklist: inspect each section above and mark items as [x] if verified");
        out.println("=".repeat(70));
    }
}