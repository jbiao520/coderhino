package com.coderhino.permissions;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class BashCommandClassifier {

    public enum Classification {
        SAFE,
        CONFIRM,
        DENY
    }

    private static final List<Pattern> DENY_PATTERNS = List.of(
        Pattern.compile(":\\(\\)\\s*\\{\\s*:\\|:"),
        Pattern.compile(":\\(\\)\\{:\\|:\\&\\};:"),
        Pattern.compile("rm\\s+(-[a-zA-Z]*r[a-zA-Z]*f|(-[a-zA-Z]*f[a-zA-Z]*r))\\s+/"),
        Pattern.compile("rm\\s+(-[a-zA-Z]*r[a-zA-Z]*)\\s+/\\s*$"),
        Pattern.compile("rm\\s+-rf\\s+~"),
        Pattern.compile("rm\\s+-rf\\s+\\$HOME"),
        Pattern.compile("rm\\s+-rf\\s+\\$\\{HOME\\}"),
        Pattern.compile("sudo\\s+rm\\s"),
        Pattern.compile("\\bdd\\s+.*if="),
        Pattern.compile("\\bmkfs\\b"),
        Pattern.compile("\\bfdisk\\b"),
        Pattern.compile("\\bparted\\b"),
        Pattern.compile("\\bformat\\s+[a-zA-Z]:"),
        Pattern.compile("chmod\\s+777\\s+/"),
        Pattern.compile("chmod\\s+-R\\s+777\\s+/"),
        Pattern.compile("chown\\s+.*\\s+/\\s*$"),
        Pattern.compile("chown\\s+-R\\s+"),
        Pattern.compile("\\bsudo\\s+su\\b"),
        Pattern.compile("\\binit\\s+[06]\\b"),
        Pattern.compile("\\breboot\\b"),
        Pattern.compile("\\bshutdown\\b"),
        Pattern.compile("\\bhalt\\b"),
        Pattern.compile("\\bpoweroff\\b"),
        Pattern.compile("\\|\\s*(ba)?sh\\b"),
        Pattern.compile("\\|\\s*python[23]?\\b"),
        Pattern.compile("\\|\\s*ruby\\b"),
        Pattern.compile("\\|\\s*perl\\b"),
        Pattern.compile("\\|\\s*node\\b"),
        Pattern.compile(">\\s*/etc/passwd"),
        Pattern.compile(">\\s*/etc/shadow"),
        Pattern.compile(">\\s*/etc/sudoers"),
        Pattern.compile(">\\s*/boot/"),
        Pattern.compile("curl\\s+.*\\|\\s*(ba)?sh"),
        Pattern.compile("wget\\s+.*\\|\\s*(ba)?sh"),
        Pattern.compile("curl\\s+.*\\|\\s*python"),
        Pattern.compile("wget\\s+.*\\|\\s*python"),
        Pattern.compile("base64\\s+(-d|--decode).*\\|\\s*(ba)?sh"),
        Pattern.compile(">\\s*/dev/sda")
    );

    private static final Set<String> SAFE_FIRST_TOKENS = Set.of(
        "cat", "head", "tail", "less", "more", "file", "stat", "wc",
        "ls", "dir", "pwd", "find", "locate",
        "grep", "egrep", "fgrep", "rg", "ag",
        "sort", "uniq", "diff", "comm", "join", "cut", "paste",
        "awk", "sed",
        "echo", "printf", "print",
        "which", "whereis", "type", "man", "info", "help",
        "uname", "hostname", "whoami", "id", "groups", "env", "printenv",
        "date", "cal", "uptime", "w", "who", "last",
        "df", "du", "free", "top", "htop", "ps", "pstree",
        "lsof", "netstat", "ss", "ifconfig", "ip",
        "lscpu", "lsblk", "lspci", "lsusb",
        "mvn", "gradle", "ant",
        "java", "python3", "python", "node", "ruby", "perl",
        "git",
        "npm", "yarn", "pnpm", "pip", "pip3", "gem", "cargo", "go"
    );

    private static final List<Pattern> SAFE_COMMAND_PATTERNS = List.of(
        Pattern.compile("^git\\s+(status|log|diff|show|branch|tag|remote|fetch|stash\\s+list|ls-files|ls-remote|describe|rev-parse|rev-list|shortlog|reflog|blame|check-ignore|config\\s+--list|config\\s+-l|submodule\\s+status)"),
        Pattern.compile("^mvn\\s+(test|compile|verify|package|install|validate|help|dependency:|site:|generate-sources)"),
        Pattern.compile("^npm\\s+(ls|list|info|view|search|audit|outdated|doctor)"),
        Pattern.compile("^yarn\\s+(list|info|why|audit|outdated)"),
        Pattern.compile("^pip[23]?\\s+(list|show|check|freeze)"),
        Pattern.compile("^sed(?!\\s+-i)"),
        Pattern.compile("^awk\\s"),
        Pattern.compile("^java\\s+(--version|-version|\\-cp|\\-jar|-Dtest)"),
        Pattern.compile("^python[23]?\\s+(-c|-m|-V|--version)"),
        Pattern.compile("^node\\s+(-e|-p|-v|--version)")
    );

    private static final List<Pattern> CONFIRM_PATTERNS = List.of(
        Pattern.compile(">>?\\s+\\S"),
        Pattern.compile("\\bcurl\\b"),
        Pattern.compile("\\bwget\\b"),
        Pattern.compile("\\bscp\\b"),
        Pattern.compile("\\brsync\\b"),
        Pattern.compile("^git\\s+(commit|push|pull|merge|rebase|reset|checkout\\s+-[bB]|branch\\s+-[dD]|tag\\s+-d|stash\\s+(pop|drop|clear)|clean|apply|cherry-pick|revert|am|fetch\\s+.*--tags)"),
        Pattern.compile("^npm\\s+(install|uninstall|update|publish|run|exec|ci)"),
        Pattern.compile("^yarn\\s+(add|remove|install|upgrade|publish|run)"),
        Pattern.compile("^pip[23]?\\s+(install|uninstall|download)"),
        Pattern.compile("^\\./"),
        Pattern.compile("^sh\\s+"),
        Pattern.compile("^bash\\s+"),
        Pattern.compile("^zsh\\s+"),
        Pattern.compile("^python[23]?\\s+\\S+\\.py"),
        Pattern.compile("^node\\s+\\S+\\.js"),
        Pattern.compile("^sed\\s+.*-i"),
        Pattern.compile("\\brm\\s+"),
        Pattern.compile("\\b(mv|cp)\\s+"),
        Pattern.compile("\\bchmod\\s+"),
        Pattern.compile("\\bchown\\s+"),
        Pattern.compile("\\bkill\\b"),
        Pattern.compile("\\bkillall\\b"),
        Pattern.compile("\\bpkill\\b"),
        Pattern.compile("\\bsystemctl\\b"),
        Pattern.compile("\\bservice\\b"),
        Pattern.compile("\\bcrontab\\b"),
        Pattern.compile("^docker\\s+(run|start|stop|rm|rmi|pull|push|build|exec)"),
        Pattern.compile("\\b(mysql|psql|sqlite3|mongosh)\\b"),
        Pattern.compile("^make\\b"),
        Pattern.compile("^sudo\\b"),
        Pattern.compile("\\|\\s*(tee|dd|xargs|install)\\b")
    );

    public Classification classify(String command) {
        if (command == null || command.isBlank()) {
            return Classification.SAFE;
        }
        String trimmed = command.trim();
        String lower = trimmed.toLowerCase();

        for (Pattern deny : DENY_PATTERNS) {
            if (deny.matcher(lower).find()) {
                return Classification.DENY;
            }
        }

        for (Pattern confirm : CONFIRM_PATTERNS) {
            if (confirm.matcher(lower).find()) {
                return Classification.CONFIRM;
            }
        }

        for (Pattern safe : SAFE_COMMAND_PATTERNS) {
            if (safe.matcher(lower).find()) {
                return Classification.SAFE;
            }
        }

        String firstToken = extractFirstToken(trimmed).toLowerCase();
        if (SAFE_FIRST_TOKENS.contains(firstToken)) {
            return Classification.SAFE;
        }

        return Classification.CONFIRM;
    }

    public boolean isDeny(String command) {
        return classify(command) == Classification.DENY;
    }

    public boolean isSafe(String command) {
        return classify(command) == Classification.SAFE;
    }

    public boolean isConfirm(String command) {
        return classify(command) == Classification.CONFIRM;
    }

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

    private static String extractFirstToken(String command) {
        if (command == null || command.isBlank()) return "";
        String[] parts = command.trim().split("\\s+", 2);
        return parts[0];
    }
}
