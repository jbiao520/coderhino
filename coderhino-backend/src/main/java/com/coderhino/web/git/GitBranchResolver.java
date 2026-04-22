package com.coderhino.web.git;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

public final class GitBranchResolver {

    private static final Logger log = LoggerFactory.getLogger(GitBranchResolver.class);
    private static final String UNKNOWN_BRANCH = "unknown";

    private GitBranchResolver() {
    }

    public static String resolve(Path cwd) {
        try {
            var process = new ProcessBuilder("git", "rev-parse", "--abbrev-ref", "HEAD")
                .directory(cwd.toFile())
                .start();
            var output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && !output.isBlank()) {
                return output;
            }
        } catch (Exception e) {
            log.debug("Could not determine git branch for {}", cwd, e);
        }
        return UNKNOWN_BRANCH;
    }
}
