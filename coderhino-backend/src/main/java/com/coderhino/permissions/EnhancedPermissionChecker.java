package com.coderhino.permissions;

import com.coderhino.permissions.BashCommandClassifier.Classification;
import com.coderhino.permissions.PermissionAuditLog.Decision;
import com.coderhino.types.PermissionMode;
import com.coderhino.types.PermissionResult;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class EnhancedPermissionChecker {

    private static final Set<String> SAFE_BASH_COMMANDS = Set.of(
        "cat", "head", "tail", "grep", "find", "ls", "stat", "file", "wc", "diff",
        "sort", "uniq", "awk", "sed", "pwd", "echo", "printf"
    );

    private static final Set<String> DANGEROUS_BASH_PATTERNS = Set.of(
        "rm -rf", "rm -r ", "rm -f", "mkfs", "dd of=", "> /dev/",
        "chmod 777", "chown", "curl ", "wget ", "sudo su", "init 6",
        "reboot", "shutdown", "halt", "poweroff"
    );

    private static final Set<String> DESTRUCTIVE_PATH_PATTERNS = Set.of(
        ".git", ".svn"
    );

    private static final Set<String> PROJECT_CRITICAL_PATHS = Set.of(
        "/etc/", "/usr/lib/", "/bin/", "/sbin/", "/boot/", "/sys/", "/proc/", "/dev/",
        ".ssh", ".aws"
    );

    private static final int DEFAULT_CIRCUIT_BREAKER_THRESHOLD = 3;

    private final BashCommandClassifier classifier;
    private final PermissionAuditLog auditLog;
    private final int circuitBreakerThreshold;

    private final AtomicInteger consecutiveRiskyCount = new AtomicInteger(0);
    private final AtomicReference<PermissionMode> effectiveAutoMode =
            new AtomicReference<>(PermissionMode.AUTO);

    public EnhancedPermissionChecker() {
        this(DEFAULT_CIRCUIT_BREAKER_THRESHOLD);
    }

    public EnhancedPermissionChecker(int circuitBreakerThreshold) {
        this.circuitBreakerThreshold = circuitBreakerThreshold;
        this.classifier = new BashCommandClassifier();
        this.auditLog = new PermissionAuditLog();
    }

    public PermissionResult resolve(PermissionMode mode, PermissionResult requested) {
        return resolveWithContext(mode, requested, null, null);
    }

    public PermissionResult resolveWithContext(
            PermissionMode mode,
            PermissionResult requested,
            String toolName,
            Object input) {

        if (mode == PermissionMode.BYPASS) {
            return PermissionResult.allow();
        }

        if (requested instanceof PermissionResult.Deny deny) {
            return deny;
        }

        if (requested instanceof PermissionResult.Ask ask) {
            return applyModeBehavior(mode, ask, toolName, input);
        }

        return PermissionResult.allow();
    }

    private PermissionResult applyModeBehavior(
            PermissionMode mode,
            PermissionResult.Ask ask,
            String toolName,
            Object input) {

        return switch (mode) {
            case DEFAULT, PLAN -> ask;
            case AUTO -> evaluateForAutoMode(ask, toolName, input);
            case DONT_ASK -> evaluateForDontAskMode(ask, toolName, input);
            case ACCEPT_EDITS -> evaluateForAcceptEditsMode(ask, toolName, input);
            case BYPASS -> PermissionResult.allow();
        };
    }

    private PermissionResult evaluateForAutoMode(
            PermissionResult.Ask ask,
            String toolName,
            Object input) {

        if (isReadOnlyTool(toolName)) {
            auditLog.record(toolName, null, Classification.SAFE, PermissionMode.AUTO,
                    Decision.ALLOWED, "read-only tool");
            return PermissionResult.allow();
        }

        String command = extractCommand(input);

        if (command != null && "bash".equals(toolName)) {
            Classification cls = classifier.classify(command);

            switch (cls) {
                case DENY -> {
                    auditLog.record(toolName, command, Classification.DENY, PermissionMode.AUTO,
                            Decision.DENIED, "destructive command blocked: " + command);
                    consecutiveRiskyCount.incrementAndGet();
                    checkAndTriggerCircuitBreaker();
                    return PermissionResult.deny(
                            "Auto mode blocked destructive command: " + command);
                }
                case SAFE -> {
                    auditLog.record(toolName, command, Classification.SAFE, PermissionMode.AUTO,
                            Decision.ALLOWED, "safe command auto-approved");
                    consecutiveRiskyCount.set(0);
                    return PermissionResult.allow();
                }
                case CONFIRM -> {
                    int riskyCount = consecutiveRiskyCount.incrementAndGet();
                    if (riskyCount >= circuitBreakerThreshold) {
                        effectiveAutoMode.set(PermissionMode.DEFAULT);
                        auditLog.record(toolName, command, Classification.CONFIRM, PermissionMode.AUTO,
                                Decision.ESCALATED,
                                "circuit-breaker triggered after " + riskyCount + " risky commands — demoted to DEFAULT");
                        return ask;
                    }
                    auditLog.record(toolName, command, Classification.CONFIRM, PermissionMode.AUTO,
                            Decision.ESCALATED, "risky command escalated for confirmation (" + riskyCount + "/" + circuitBreakerThreshold + ")");
                    return ask;
                }
            }
        }

        if (isDangerousOperation(toolName, input)) {
            auditLog.record(toolName, command, Classification.DENY, PermissionMode.AUTO,
                    Decision.DENIED, "dangerous operation blocked: " + ask.reason());
            consecutiveRiskyCount.incrementAndGet();
            checkAndTriggerCircuitBreaker();
            return PermissionResult.deny("Auto mode denied potentially dangerous operation: " + ask.reason());
        }

        if (isSafeBashCommand(input)) {
            auditLog.record(toolName, command, Classification.SAFE, PermissionMode.AUTO,
                    Decision.ALLOWED, "safe bash command auto-approved");
            consecutiveRiskyCount.set(0);
            return PermissionResult.allow();
        }

        if (isSafePath(toolName, input)) {
            auditLog.record(toolName, command, Classification.SAFE, PermissionMode.AUTO,
                    Decision.ALLOWED, "safe path auto-approved");
            return PermissionResult.allow();
        }

        int riskyCount = consecutiveRiskyCount.incrementAndGet();
        if (riskyCount >= circuitBreakerThreshold) {
            effectiveAutoMode.set(PermissionMode.DEFAULT);
            auditLog.record(toolName, command, Classification.CONFIRM, PermissionMode.AUTO,
                    Decision.ESCALATED,
                    "circuit-breaker triggered after " + riskyCount + " risky commands");
        } else {
            auditLog.record(toolName, command, Classification.CONFIRM, PermissionMode.AUTO,
                    Decision.ESCALATED, "escalated for confirmation");
        }
        return ask;
    }

    private void checkAndTriggerCircuitBreaker() {
        if (consecutiveRiskyCount.get() >= circuitBreakerThreshold) {
            effectiveAutoMode.set(PermissionMode.DEFAULT);
        }
    }

    private PermissionResult evaluateForDontAskMode(
            PermissionResult.Ask ask,
            String toolName,
            Object input) {

        if (isReadOnlyTool(toolName)) {
            return PermissionResult.allow();
        }

        String command = extractCommand(input);
        if (command != null && "bash".equals(toolName)) {
            Classification cls = classifier.classify(command);
            if (cls == Classification.DENY) {
                return PermissionResult.deny("DONT_ASK mode blocked destructive command: " + command);
            }
        }

        if (isDangerousOperation(toolName, input)) {
            return PermissionResult.deny("DONT_ASK mode requires operation to be clearly safe: " + ask.reason());
        }

        if (isReadOnlyBashCommand(input)) {
            return PermissionResult.allow();
        }

        if (isSafePath(toolName, input) && !isDestructiveOperation(toolName, input)) {
            return PermissionResult.allow();
        }

        return PermissionResult.deny("DONT_ASK mode requires operation to be clearly safe: " + ask.reason());
    }

    private PermissionResult evaluateForAcceptEditsMode(
            PermissionResult.Ask ask,
            String toolName,
            Object input) {

        if (isReadOnlyTool(toolName)) {
            return PermissionResult.allow();
        }

        if (isFileEditTool(toolName) && !isDestructiveOperation(toolName, input)) {
            return PermissionResult.allow();
        }

        if (isDangerousOperation(toolName, input)) {
            return ask;
        }

        return ask;
    }

    public boolean isAutoModeDemoted() {
        return effectiveAutoMode.get() == PermissionMode.DEFAULT;
    }

    public PermissionMode getEffectiveAutoMode() {
        return effectiveAutoMode.get();
    }

    public int getConsecutiveRiskyCount() {
        return consecutiveRiskyCount.get();
    }

    public void resetCircuitBreaker() {
        consecutiveRiskyCount.set(0);
        effectiveAutoMode.set(PermissionMode.AUTO);
    }

    public PermissionAuditLog getAuditLog() {
        return auditLog;
    }

    public BashCommandClassifier getClassifier() {
        return classifier;
    }

    public boolean isReadOnlyTool(String toolName) {
        return switch (toolName) {
            case "read_file", "glob", "grep", "web_fetch", "web_search" -> true;
            default -> false;
        };
    }

    public boolean isReadOnlyBashCommand(Object input) {
        if (input == null) return false;
        String command = extractCommand(input);
        if (command == null) return false;
        String firstWord = command.trim().split("\\s+")[0].toLowerCase();
        return SAFE_BASH_COMMANDS.contains(firstWord) && !isDangerousBashCommand(command);
    }

    public boolean isSafeBashCommand(Object input) {
        if (input == null) return false;
        String command = extractCommand(input);
        if (command == null) return false;
        String firstWord = command.trim().split("\\s+")[0].toLowerCase();
        if (!SAFE_BASH_COMMANDS.contains(firstWord)) {
            return false;
        }
        return !isDangerousBashCommand(command);
    }

    private boolean isDangerousBashCommand(String command) {
        if (command == null) return false;
        String lower = command.toLowerCase();
        for (String pattern : DANGEROUS_BASH_PATTERNS) {
            if (lower.contains(pattern)) {
                return true;
            }
        }
        if (lower.contains("| sh") || lower.contains("| bash") || lower.contains("| python") || lower.contains("| ruby") || lower.contains("| perl")) {
            return true;
        }
        return false;
    }

    public boolean isSafePath(String toolName, Object input) {
        String path = extractPath(input);
        if (path == null) return false;
        Path p = Path.of(path).normalize();
        String pathStr = p.toString();
        for (String critical : PROJECT_CRITICAL_PATHS) {
            if (pathStr.contains(critical)) {
                return false;
            }
        }
        for (String destructive : DESTRUCTIVE_PATH_PATTERNS) {
            if (pathStr.contains(destructive)) {
                return false;
            }
        }
        return true;
    }

    public boolean isDangerousOperation(String toolName, Object input) {
        if ("bash".equals(toolName)) {
            String command = extractCommand(input);
            if (command != null && isDangerousBashCommand(command)) {
                return true;
            }
        }
        if (isProjectCriticalPath(input)) {
            return true;
        }
        return false;
    }

    public boolean isDestructiveOperation(String toolName, Object input) {
        String path = extractPath(input);
        if (path == null) return false;
        Path p = Path.of(path).normalize();
        String pathStr = p.toString();
        for (String destructive : DESTRUCTIVE_PATH_PATTERNS) {
            if (pathStr.contains(destructive)) {
                return true;
            }
        }
        return false;
    }

    public boolean isProjectCriticalPath(Object input) {
        String path = extractPath(input);
        if (path == null) return false;
        for (String critical : PROJECT_CRITICAL_PATHS) {
            if (path.contains(critical)) {
                return true;
            }
        }
        return false;
    }

    private String extractCommand(Object input) {
        if (input == null) return null;

        if (input instanceof BashToolInput bt) return bt.command();
        if (input instanceof FileToolInput ft) return ft.path();
        if (input instanceof EditToolInput et) return et.path();

        try {
            var cmdMethod = input.getClass().getMethod("command");
            Object result = cmdMethod.invoke(input);
            return result instanceof String s ? s : null;
        } catch (Exception ignored) {
        }

        if (input instanceof String s) {
            return s;
        }
        return null;
    }

    private String extractPath(Object input) {
        if (input == null) return null;

        if (input instanceof FileToolInput ft) return ft.path();
        if (input instanceof EditToolInput et) return et.path();
        if (input instanceof BashToolInput bt) return bt.command();

        try {
            var pathMethod = input.getClass().getMethod("path");
            Object result = pathMethod.invoke(input);
            return result instanceof String s ? s : null;
        } catch (Exception ignored) {
        }

        return extractCommand(input);
    }

    private boolean isFileEditTool(String toolName) {
        return "edit_file".equals(toolName) || "write_file".equals(toolName);
    }

    public record BashToolInput(String command, Integer timeoutSeconds) {
    }

    public record FileToolInput(String path, String content) {
    }

    public record EditToolInput(String path, String oldText, String newText) {
    }
}
