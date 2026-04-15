package com.coderhino.permissions;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Classifies bash commands into safety categories:
 * - SAFE: read-only, informational operations — auto-approved in AUTO mode
 * - CONFIRM: write, network, or potentially impactful operations — prompt user
 * - DENY: destructive or irreversible operations — always blocked
 *
 * Classification mirrors the TypeScript bashPermissions.ts logic.
 */
public final class BashCommandClassifier {

    /** Classification result for a command. */
    public enum Classification {
        /**
         * Safe, read-only operations. Auto-approved in AUTO mode without prompt.
         * Examples: ls, cat, grep, git status, mvn test
         */
        SAFE,

        /**
         * Potentially impactful operations requiring user confirmation.
         * Examples: write redirects, running scripts, network downloads
         */
        CONFIRM,

        /**
         * Destructive, irreversible, or dangerous operations. Always blocked.
         * Examples: rm -rf /, chmod 777 /, fork bombs, disk formatting
         */
        DENY
    }

    // -------------------------------------------------------------------------
    // DENY patterns — checked first, block immediately regardless of mode
    // -------------------------------------------------------------------------

    /** DENY: commands or patterns that are always blocked */
    private static final List<Pattern> DENY_PATTERNS = List.of(
        // Fork bomb
        Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:"),
        Pattern.compile(":\\(\\)\\{:\\|:\\&\\};:"),

        // Destructive rm targeting root or home
        Pattern.compile("rm\\s+(-[a-zA-Z]*r[a-zA-Z]*f|(-[a-zA-Z]*f[a-zA-Z]*r))\\s+/"),
        Pattern.compile("rm\\s+(-[a-zA-Z]*r[a-zA-Z]*)\\s+/\\s*$"),
        Pattern.compile("rm\\s+-rf\\s+~"),
        Pattern.compile("rm\\s+-rf\\s+\\$HOME"),
        Pattern.compile("rm\\s+-rf\\s+\\$\\{HOME\\}"),

        // sudo rm (any rm via sudo is dangerous)
        Pattern.compile("sudo\\s+rm\\s"),

        // Disk-level operations
        Pattern.compile("\\bdd\\s+.*if="),
        Pattern.compile("\\bmkfs\\b"),
        Pattern.compile("\\bfdisk\\b"),
        Pattern.compile("\\bparted\\b"),
        Pattern.compile("\\bformat\\s+[a-zA-Z]:"),

        // chmod/chown on root
        Pattern.compile("chmod\\s+777\\s+/"),
        Pattern.compile("chmod\\s+-R\\s+777\\s+/"),
        Pattern.compile("chown\\s+.*\\s+/\\s*$"),
        Pattern.compile("chown\\s+-R\\s+"),

        // System-level destructive operations
        Pattern.compile("\\bsudo\\s+su\\b"),
        Pattern.compile("\\binit\\s+[06]\\b"),
        Pattern.compile("\\breboot\\b"),
        Pattern.compile("\\bshutdown\\b"),
        Pattern.compile("\\bhalt\\b"),
        Pattern.compile("\\bpoweroff\\b"),

        // Pipe to shell — executing arbitrary code
        Pattern.compile("\\|\\s*(ba)?sh\\b"),
        Pattern.compile("\\|\\s*python[23]?\\b"),
        Pattern.compile("\\|\\s*ruby\\b"),
        Pattern.compile("\\|\\s*perl\\b"),
        Pattern.compile("\\|\\s*node\\b"),

        // Overwrite critical system files
        Pattern.compile(">\\s*/etc/passwd"),
        Pattern.compile(">\\s*/etc/shadow"),
        Pattern.compile(">\\s*/etc/sudoers"),
        Pattern.compile(">\\s*/boot/"),

        // Execute remote code
        Pattern.compile("curl\\s+.*\\|\\s*(ba)?sh"),
        Pattern.compile("wget\\s+.*\\|\\s*(ba)?sh"),
        Pattern.compile("curl\\s+.*\\|\\s*python"),
        Pattern.compile("wget\\s+.*\\|\\s*python"),

        // Base64 decode and pipe to shell
        Pattern.compile("base64\\s+(-d|--decode).*\\|\\s*(ba)?sh"),

        // /dev/null overwrite trick (no-op but good practice to flag)
        Pattern.compile(">\\s*/dev/sda")
    );

    // -------------------------------------------------------------------------
    // SAFE patterns — read-only or clearly benign operations
    // -------------------------------------------------------------------------

    /**
     * Commands whose first token is inherently read-only.
     * These are auto-approved if no dangerous pattern overlays them.
     */
    private static final Set<String> SAFE_FIRST_TOKENS = Set.of(
        // File inspection
        "cat", "head", "tail", "less", "more", "file", "stat", "wc",
        // Listing / navigation
        "ls", "dir", "pwd", "find", "locate",
        // Search
        "grep", "egrep", "fgrep", "rg", "ag",
        // Text processing (read-only modes)
        "sort", "uniq", "diff", "comm", "join", "cut", "paste",
        "awk", "sed",
        // Output
        "echo", "printf", "print",
        // System info
        "which", "whereis", "type", "man", "info", "help",
        "uname", "hostname", "whoami", "id", "groups", "env", "printenv",
        "date", "cal", "uptime", "w", "who", "last",
        "df", "du", "free", "top", "htop", "ps", "pstree",
        "lsof", "netstat", "ss", "ifconfig", "ip",
        "lscpu", "lsblk", "lspci", "lsusb",
        // Build / test (read-only invocations)
        "mvn", "gradle", "ant",
        "java", "python3", "python", "node", "ruby", "perl",
        // Version check
        // Git (read-only operations)
        "git",
        // Package managers (install is confirm, but list/show is safe)
        "npm", "yarn", "pnpm", "pip", "pip3", "gem", "cargo", "go"
    );

    /**
     * For "safe first tokens", these sub-commands or flags make the overall
     * command safe (e.g., git status, git log, npm ls).
     * If the token IS in SAFE_FIRST_TOKENS but NOT in these sub-safe patterns,
     * we further analyze for CONFIRM.
     */
    private static final List<Pattern> SAFE_COMMAND_PATTERNS = List.of(
        // Git read-only operations
        Pattern.compile("^git\\s+(status|log|diff|show|branch|tag|remote|fetch|stash\\s+list|ls-files|ls-remote|describe|rev-parse|rev-list|shortlog|reflog|blame|check-ignore|config\\s+--list|config\\s+-l|submodule\\s+status)"),
        // Maven build/test operations (read or compile/test but not deploy)
        Pattern.compile("^mvn\\s+(test|compile|verify|package|install|validate|help|dependency:|site:|generate-sources)"),
        // npm/yarn listing and info
        Pattern.compile("^npm\\s+(ls|list|info|view|search|audit|outdated|doctor)"),
        Pattern.compile("^yarn\\s+(list|info|why|audit|outdated)"),
        // pip listing
        Pattern.compile("^pip[23]?\\s+(list|show|check|freeze)"),
        // Safe sed (without -i in-place edit)
        Pattern.compile("^sed(?!\\s+-i)"),
        // Safe awk
        Pattern.compile("^awk\\s"),
        // Java version / run
        Pattern.compile("^java\\s+(--version|-version|\\-cp|\\-jar|-Dtest)"),
        // python / node version
        Pattern.compile("^python[23]?\\s+(-c|-m|-V|--version)"),
        Pattern.compile("^node\\s+(-e|-p|-v|--version)")
    );

    // -------------------------------------------------------------------------
    // CONFIRM patterns — require user approval
    // -------------------------------------------------------------------------

    private static final List<Pattern> CONFIRM_PATTERNS = List.of(
        // Write redirects
        Pattern.compile(">>?\\s+\\S"),
        // Input redirects reading from pipes to non-safe consumer
        // Network downloads (without pipe to shell — that's DENY)
        Pattern.compile("\\bcurl\\b"),
        Pattern.compile("\\bwget\\b"),
        Pattern.compile("\\bscp\\b"),
        Pattern.compile("\\brsync\\b"),
        // Git write operations
        Pattern.compile("^git\\s+(commit|push|pull|merge|rebase|reset|checkout\\s+-[bB]|branch\\s+-[dD]|tag\\s+-d|stash\\s+(pop|drop|clear)|clean|apply|cherry-pick|revert|am|fetch\\s+.*--tags)"),
        // npm install/uninstall/publish
        Pattern.compile("^npm\\s+(install|uninstall|update|publish|run|exec|ci)"),
        Pattern.compile("^yarn\\s+(add|remove|install|upgrade|publish|run)"),
        Pattern.compile("^pip[23]?\\s+(install|uninstall|download)"),
        // Script execution
        Pattern.compile("^\\./"),           // ./script.sh
        Pattern.compile("^sh\\s+"),
        Pattern.compile("^bash\\s+"),
        Pattern.compile("^zsh\\s+"),
        Pattern.compile("^python[23]?\\s+\\S+\\.py"),
        Pattern.compile("^node\\s+\\S+\\.js"),
        // sed with in-place edit
        Pattern.compile("^sed\\s+.*-i"),
        // rm without root target (root-targeting is DENY)
        Pattern.compile("\\brm\\s+"),
        // mv / cp to critical paths
        Pattern.compile("\\b(mv|cp)\\s+"),
        // chmod/chown non-root
        Pattern.compile("\\bchmod\\s+"),
        Pattern.compile("\\bchown\\s+"),
        // Process control
        Pattern.compile("\\bkill\\b"),
        Pattern.compile("\\bkillall\\b"),
        Pattern.compile("\\bpkill\\b"),
        // Service management
        Pattern.compile("\\bsystemctl\\b"),
        Pattern.compile("\\bservice\\b"),
        // Cron modification
        Pattern.compile("\\bcrontab\\b"),
        // Docker operations (non-read)
        Pattern.compile("^docker\\s+(run|start|stop|rm|rmi|pull|push|build|exec)"),
        // Database operations
        Pattern.compile("\\b(mysql|psql|sqlite3|mongosh)\\b"),
        // Make (can do anything)
        Pattern.compile("^make\\b"),
        // sudo (non-rm — rm is DENY)
        Pattern.compile("^sudo\\b"),
        // Pipes to non-shell consumers (safe consumer pipes already covered)
        Pattern.compile("\\|\\s*(tee|dd|xargs|install)\\b")
    );

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Classifies a raw bash command string.
     *
     * @param command the full bash command to classify
     * @return Classification enum value
     */
    public Classification classify(String command) {
        if (command == null || command.isBlank()) {
            return Classification.SAFE;
        }
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();

        // 1. Check DENY patterns first — always blocked
        for (Pattern deny : DENY_PATTERNS) {
            if (deny.matcher(lower).find()) {
                return Classification.DENY;
            }
        }

        // 2. Check CONFIRM patterns — require approval
        for (Pattern confirm : CONFIRM_PATTERNS) {
            if (confirm.matcher(lower).find()) {
                return Classification.CONFIRM;
            }
        }

        // 3. Check SAFE_COMMAND_PATTERNS (specific sub-commands of safe tools)
        for (Pattern safe : SAFE_COMMAND_PATTERNS) {
            if (safe.matcher(lower).find()) {
                return Classification.SAFE;
            }
        }

        // 4. Check if first token is in SAFE set — safe unless overridden above
        String firstToken = extractFirstToken(trimmed).toLowerCase();
        if (SAFE_FIRST_TOKENS.contains(firstToken)) {
            return Classification.SAFE;
        }

        // 5. Default to CONFIRM for unknown commands
        return Classification.CONFIRM;
    }

    /**
     * Convenience: returns true if the command is classified as DENY.
     */
    public boolean isDeny(String command) {
        return classify(command) == Classification.DENY;
    }

    /**
     * Convenience: returns true if the command is classified as SAFE.
     */
    public boolean isSafe(String command) {
        return classify(command) == Classification.SAFE;
    }

    /**
     * Convenience: returns true if the command requires confirmation.
     */
    public boolean isConfirm(String command) {
        return classify(command) == Classification.CONFIRM;
    }

    /**
     * Returns a human-readable explanation of the classification decision.
     */
    public String explain(String command) {
        if (command == null || command.isBlank()) {
            return "Empty command is SAFE";
        }
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();

        for (Pattern deny : DENY_PATTERNS) {
            if (deny.matcher(lower).find()) {
                return "DENY: command matches destructive pattern: " + deny.pattern();
            }
        }
        for (Pattern confirm : CONFIRM_PATTERNS) {
            if (confirm.matcher(lower).find()) {
                return "CONFIRM: command requires approval, matched pattern: " + confirm.pattern();
            }
        }
        for (Pattern safe : SAFE_COMMAND_PATTERNS) {
            if (safe.matcher(lower).find()) {
                return "SAFE: command matched safe sub-command pattern";
            }
        }
        String firstToken = extractFirstToken(trimmed).toLowerCase();
        if (SAFE_FIRST_TOKENS.contains(firstToken)) {
            return "SAFE: first token '" + firstToken + "' is in safe commands list";
        }
        return "CONFIRM: unknown command requires approval";
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String extractFirstToken(String command) {
        if (command == null || command.isBlank()) return "";
        String[] parts = command.trim().split("\\s+", 2);
        return parts[0];
    }
}
