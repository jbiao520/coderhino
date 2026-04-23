package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.types.Message;

import java.util.LinkedHashSet;

public final class FilesCommand implements CommandDefinition {
    @Override
    public String name() {
        return "files";
    }

    @Override
    public String description() {
        return "List all files referenced or modified in the current session context";
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var state = context.bootstrapState().get();

        var filePaths = new LinkedHashSet<String>();
        for (var msg : state.sessionRuntime().transcript()) {
            var message = msg.message();
            if (message instanceof Message.AssistantToolUseMessage toolUse) {
                extractFilePaths(toolUse.content(), filePaths);
            }
            if (message instanceof Message.ToolResultMessage result) {
                extractFilePaths(result.content(), filePaths);
            }
        }

        if (filePaths.isEmpty()) {
            renderer.printLine("No files in context.");
            renderer.printLine("Files appear here when Claude reads or edits them during a session.");
        } else {
            renderer.printLine("Files in context (" + filePaths.size() + "):");
            for (var path : filePaths) {
                renderer.printLine("  " + path);
            }
        }
    }

    private static void extractFilePaths(String content, LinkedHashSet<String> paths) {
        if (content == null || content.isBlank()) {
            return;
        }
        for (var line : content.split("\\R", -1)) {
            var trimmed = line.trim();
            if (trimmed.startsWith("/") || trimmed.startsWith("./") || trimmed.startsWith("~/")) {
                var candidate = trimmed.split("\\s+")[0];
                if (looksLikeFilePath(candidate)) {
                    paths.add(candidate);
                }
            }
            var jsonPathStart = content.indexOf("\"path\":");
            if (jsonPathStart >= 0) {
                var after = content.substring(jsonPathStart + 7).trim();
                if (after.startsWith("\"")) {
                    var end = after.indexOf('"', 1);
                    if (end > 1) {
                        var candidate = after.substring(1, end);
                        if (looksLikeFilePath(candidate)) {
                            paths.add(candidate);
                        }
                    }
                }
                break;
            }
        }
    }

    private static boolean looksLikeFilePath(String s) {
        return s.contains("/") && !s.contains(" ") && s.length() > 2 && !s.startsWith("//");
    }
}
