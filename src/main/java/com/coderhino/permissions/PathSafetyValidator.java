package com.coderhino.permissions;

import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

public final class PathSafetyValidator {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
            Pattern.compile("(^|/)(\\.git)(/|$)"),
            Pattern.compile("(^|/)(\\.ssh)(/|$)"),
            Pattern.compile("(^|/)(\\.aws)(/|$)"),
            Pattern.compile("(^|/)(\\.kube)(/|$)"),
            Pattern.compile("(^|/)(\\.docker)(/|$)"),
            Pattern.compile("(^|/)(\\.gnupg)(/|$)")
    );

    private static final List<Pattern> CONFIG_FILE_PATTERNS = List.of(
            Pattern.compile("(^|/)(known_hosts|authorized_keys|config)$"),
            Pattern.compile("(^|/)(\\.env|\\.env\\.[^/]*)$"),
            Pattern.compile("(^|/)(id_rsa|id_dsa|id_ecdsa|id_ed25519)(\\\\?\\.[^/]*)?$")
    );

    private final Path repoRoot;

    public PathSafetyValidator(Path repoRoot) {
        this.repoRoot = repoRoot != null ? repoRoot.toAbsolutePath().normalize() : null;
    }

    public boolean isPathUnsafe(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String normalized = normalizePath(rawPath);

        for (Pattern pattern : DANGEROUS_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean isConfigFile(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        String normalized = normalizePath(rawPath);

        for (Pattern pattern : CONFIG_FILE_PATTERNS) {
            if (pattern.matcher(normalized).find()) {
                return true;
            }
        }
        return false;
    }

    public boolean isWithinRepo(String rawPath) {
        if (repoRoot == null) {
            return true;
        }
        if (rawPath == null || rawPath.isBlank()) {
            return false;
        }
        try {
            Path target = Path.of(rawPath).toAbsolutePath().normalize();
            if (target.startsWith(repoRoot)) {
                return true;
            }
            if (!Path.of(rawPath).isAbsolute()) {
                Path resolved = repoRoot.resolve(rawPath).normalize();
                return resolved.startsWith(repoRoot);
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isSafeForWrite(String rawPath) {
        return !isPathUnsafe(rawPath) && !isConfigFile(rawPath);
    }

    private String normalizePath(String path) {
        StringBuilder result = new StringBuilder(path.length());
        for (int i = 0; i < path.length(); i++) {
            char c = path.charAt(i);
            if (c == '\\' && i + 1 < path.length() && path.charAt(i + 1) == '.') {
                result.append('\\');
            } else if (c == '\\') {
                result.append('/');
            } else {
                result.append(c);
            }
        }
        return result.toString();
    }

    public static PathSafetyValidator forCurrentDirectory() {
        String cwd = System.getProperty("user.dir");
        return new PathSafetyValidator(Path.of(cwd));
    }
}