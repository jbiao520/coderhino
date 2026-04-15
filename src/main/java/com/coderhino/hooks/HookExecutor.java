package com.coderhino.hooks;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

public final class HookExecutor {

    private final HookConfig config;

    public HookExecutor(HookConfig config) {
        this.config = config;
    }

    public List<HookExecutionResult> fire(HookEvent event, String subject) {
        List<HookEntry> entries = config.forEvent(event);
        List<HookExecutionResult> results = new ArrayList<>();

        for (HookEntry entry : entries) {
            if (!matches(entry.pattern(), subject)) {
                continue;
            }
            results.add(execute(entry));
        }

        return results;
    }

    private boolean matches(String pattern, String subject) {
        if (pattern == null || pattern.isEmpty()) {
            return true;
        }
        return Pattern.matches(pattern, subject);
    }

    private HookExecutionResult execute(HookEntry entry) {
        try {
            var pb = new ProcessBuilder("/bin/sh", "-c", entry.command());
            pb.redirectErrorStream(false);
            var process = pb.start();

            var stdoutRef = new AtomicReference<>("");
            var stderrRef = new AtomicReference<>("");

            var stdoutThread = new Thread(() -> {
                try {
                    stdoutRef.set(new String(process.getInputStream().readAllBytes()));
                } catch (Exception ignored) {}
            });
            var stderrThread = new Thread(() -> {
                try {
                    stderrRef.set(new String(process.getErrorStream().readAllBytes()));
                } catch (Exception ignored) {}
            });
            stdoutThread.start();
            stderrThread.start();

            boolean done = process.waitFor(10, TimeUnit.SECONDS);

            stdoutThread.join(1000);
            stderrThread.join(1000);

            if (!done) {
                process.destroyForcibly();
                return new HookExecutionResult(entry, -1, stdoutRef.get(), stderrRef.get(), true);
            }

            return new HookExecutionResult(entry, process.exitValue(), stdoutRef.get(), stderrRef.get(), false);

        } catch (Exception e) {
            System.err.println("[hooks] Failed to execute hook command: " + entry.command() + " — " + e.getMessage());
            return new HookExecutionResult(entry, -1, "", e.getMessage(), false);
        }
    }

    public record HookExecutionResult(HookEntry entry, int exitCode, String stdout, String stderr, boolean timedOut) {
        public boolean succeeded() {
            return !timedOut && exitCode == 0;
        }
    }
}
