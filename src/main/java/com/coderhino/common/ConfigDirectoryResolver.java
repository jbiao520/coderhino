package com.coderhino.common;

import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigDirectoryResolver {

    private static final String NEW_DIR = ".coderhino";

    private ConfigDirectoryResolver() {
    }

    public static Path resolveConfigSubdir(String subdir) {
        var home = Path.of(System.getProperty("user.home"));
        return home.resolve(NEW_DIR).resolve(subdir);
    }

    public static Path resolveConfigDir() {
        var home = Path.of(System.getProperty("user.home"));
        return home.resolve(NEW_DIR);
    }
}
