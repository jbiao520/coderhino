package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.util.Map;
import java.util.TreeMap;

public final class EnvCommand implements CommandDefinition {
    @Override
    public String name() {
        return "env";
    }

    @Override
    public String description() {
        return "Show environment variables and system properties";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var sub = args == null ? "" : args.trim();

        if (sub.isEmpty() || sub.equals("all")) {
            renderer.printLine("Environment Variables");
            renderer.printLine("=====================");

            var envVars = new TreeMap<>(System.getenv());
            for (var entry : envVars.entrySet()) {
                var key = entry.getKey();
                var value = maskSensitive(key, entry.getValue());
                renderer.printLine("  " + key + "=" + value);
            }

            renderer.printLine("");
            renderer.printLine("Java System Properties");
            renderer.printLine("=======================");
            var props = new TreeMap<>(System.getProperties());
            for (var entry : props.entrySet()) {
                renderer.printLine("  " + entry.getKey() + "=" + entry.getValue());
            }
        } else if (sub.startsWith("get ")) {
            var key = sub.substring(4).trim();
            var value = System.getenv(key);
            if (value == null) {
                value = System.getProperty(key);
            }
            if (value != null) {
                renderer.printLine(key + "=" + maskSensitive(key, value));
            } else {
                renderer.printLine("Not found: " + key);
            }
        } else {
            renderer.printLine("Usage: /env [all|get <key>]");
            renderer.printLine("  all       - Show all environment variables and properties (default)");
            renderer.printLine("  get <key> - Get a specific variable or property value");
        }
    }

    private static String maskSensitive(String key, String value) {
        var lower = key.toLowerCase();
        if (lower.contains("key") || lower.contains("token") || lower.contains("secret")
            || lower.contains("password") || lower.contains("auth")) {
            if (value.length() > 8) {
                return value.substring(0, 4) + "..." + value.substring(value.length() - 4);
            }
            return "****";
        }
        return value;
    }
}
