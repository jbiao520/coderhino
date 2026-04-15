package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class DoctorCommand implements CommandDefinition {
    @Override
    public String name() {
        return "doctor";
    }

    @Override
    public String description() {
        return "Run environment diagnostics";
    }

    @Override
    public void execute(CommandContext context, String args) {
        PrintStream out = context.out();

        out.println("=== Environment Diagnostics ===");

        printSection(out, "Java", checkTool("java", "-version"));
        printSection(out, "Maven", checkTool("mvn", "-version"));
        printSection(out, "Git", checkTool("git", "--version"));
        printSection(out, "ripgrep", checkTool("rg", "--version"));

        printMcpStatus(context, out);
        printLspStatus(context, out);
        printConfigDirStatus(context, out);
    }

    private void printSection(PrintStream out, String name, ToolCheck result) {
        out.printf("%s: available=%s", name, result.available());
        if (result.available() && !result.version().isEmpty()) {
            out.printf(" version=\"%s\"", result.version());
        }
        if (!result.error().isEmpty()) {
            out.printf(" error=\"%s\"", result.error());
        }
        out.println();
    }

    private void printMcpStatus(CommandContext context, PrintStream out) {
        out.println("MCP servers:");
        var definitions = context.services().mcp().definitions();
        if (definitions.isEmpty()) {
            out.println("  (none registered)");
        } else {
            for (var def : definitions) {
                var conn = context.services().mcp().connections().stream()
                    .filter(c -> c.serverName().equals(def.name()))
                    .findFirst();
                var statusMessage = conn.map(c -> c.statusMessage()).orElse("unknown");
                var connected = conn.map(c -> c.connected() ? "connected" : "disconnected").orElse("disconnected");
                out.printf("  %s enabled=%s status=%s connected=%s%n",
                    def.name(), def.enabled(), statusMessage, connected);
            }
        }
    }

    private void printLspStatus(CommandContext context, PrintStream out) {
        out.println("LSP servers:");
        var definitions = context.services().lsp().definitions();
        if (definitions.isEmpty()) {
            out.println("  (none registered)");
        } else {
            for (var def : definitions) {
                var conn = context.services().lsp().connections().stream()
                    .filter(c -> c.language().equals(def.language()))
                    .findFirst();
                var statusMessage = conn.map(c -> c.statusMessage()).orElse("unknown");
                var connected = conn.map(c -> c.connected() ? "connected" : "disconnected").orElse("disconnected");
                out.printf("  %s enabled=%s status=%s connected=%s%n",
                    def.language(), def.enabled(), statusMessage, connected);
            }
        }
    }

    private void printConfigDirStatus(CommandContext context, PrintStream out) {
        var cwd = Path.of(context.bootstrapState().get().cwd());
        var configDir = cwd.resolve(".claudecode");
        var exists = configDir.toFile().exists();
        out.printf("Config directory: path=%s exists=%s%n", configDir, exists);
    }

    private ToolCheck checkTool(String command, String... args) {
        try {
            List<String> commandList = new ArrayList<>();
            commandList.add(command);
            for (var arg : args) {
                commandList.add(arg);
            }
            var pb = new ProcessBuilder(commandList);
            pb.redirectErrorStream(true);
            var process = pb.start();
            StringBuilder version = new StringBuilder();
            try (var reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (version.length() == 0) {
                        version.append(line.trim());
                    }
                }
            }
            var exitCode = process.waitFor();
            if (exitCode == 0) {
                return new ToolCheck(true, version.toString(), "");
            } else {
                return new ToolCheck(false, "", "exit code " + exitCode);
            }
        } catch (Exception e) {
            return new ToolCheck(false, "", e.getClass().getSimpleName() + ": " + e.getMessage());
        }
    }

    private record ToolCheck(boolean available, String version, String error) {
    }
}
