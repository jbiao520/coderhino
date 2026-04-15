package com.coderhino.query;

import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;

import java.util.List;
import java.util.Map;

public final class LocalEchoModelClient implements ModelClient {
    @Override
    public ModelResponse complete(BootstrapState bootstrapState, QueryRequest request) {
        List<Message> history = request.messages();
        if (history.isEmpty()) {
            return new ModelResponse.AssistantReply("No conversation history available.");
        }

        var lastMessage = history.get(history.size() - 1);
        if (lastMessage instanceof Message.ToolResultMessage toolResultMessage) {
            return new ModelResponse.AssistantReply(
                "Tool %s completed:%n%s".formatted(toolResultMessage.toolName(), toolResultMessage.content())
            );
        }

        if (lastMessage instanceof Message.UserMessage userMessage) {
            var content = userMessage.content();
            if (content.startsWith("tool bash ")) {
                return new ModelResponse.ToolRequest("bash", Map.of("command", content.substring("tool bash ".length()), "timeoutSeconds", 10), "local-bash-tool-use");
            }
            if (content.startsWith("tool read_file ")) {
                return new ModelResponse.ToolRequest("read_file", Map.of("path", content.substring("tool read_file ".length()), "offset", 1, "limit", 200), "local-read-file-tool-use");
            }
            if (content.startsWith("tool write_file ")) {
                var payload = content.substring("tool write_file ".length());
                var parts = payload.split("::", 2);
                if (parts.length == 2) {
                    return new ModelResponse.ToolRequest("write_file", Map.of("path", parts[0], "content", parts[1]), "local-write-file-tool-use");
                }
            }
            if (content.startsWith("tool edit_file ")) {
                var payload = content.substring("tool edit_file ".length());
                var parts = payload.split("::", 3);
                if (parts.length == 3) {
                    return new ModelResponse.ToolRequest("edit_file", Map.of("path", parts[0], "oldText", parts[1], "newText", parts[2]), "local-edit-file-tool-use");
                }
            }
            if (content.startsWith("tool glob ")) {
                return new ModelResponse.ToolRequest("glob", Map.of("pattern", content.substring("tool glob ".length())), "local-glob-tool-use");
            }
            if (content.startsWith("tool grep ")) {
                return new ModelResponse.ToolRequest("grep", Map.of("pattern", content.substring("tool grep ".length())), "local-grep-tool-use");
            }
            if (content.startsWith("tool web_fetch ")) {
                return new ModelResponse.ToolRequest("web_fetch", Map.of("url", content.substring("tool web_fetch ".length()), "format", "text"), "local-web-fetch-tool-use");
            }
            if (content.startsWith("tool web_search ")) {
                return new ModelResponse.ToolRequest("web_search", Map.of("query", content.substring("tool web_search ".length()), "limit", 5), "local-web-search-tool-use");
            }
            if (content.startsWith("tool lsp workspaceSymbol ")) {
                var payload = content.substring("tool lsp workspaceSymbol ".length());
                var parts = payload.split("::", 2);
                if (parts.length == 2) {
                    return new ModelResponse.ToolRequest("lsp", Map.of("operation", "workspaceSymbol", "language", parts[0], "query", parts[1]), "local-lsp-workspace-symbol-tool-use");
                }
            }
            if (content.startsWith("tool lsp documentSymbol ")) {
                var payload = content.substring("tool lsp documentSymbol ".length());
                var parts = payload.split("::", 2);
                if (parts.length == 2) {
                    return new ModelResponse.ToolRequest("lsp", Map.of("operation", "documentSymbol", "language", parts[0], "uri", parts[1]), "local-lsp-document-symbol-tool-use");
                }
            }
            if (content.startsWith("tool lsp goToDefinition ")) {
                var payload = content.substring("tool lsp goToDefinition ".length());
                var parts = payload.split("::", 4);
                if (parts.length == 4) {
                    return new ModelResponse.ToolRequest("lsp", Map.of("operation", "goToDefinition", "language", parts[0], "uri", parts[1], "line", Integer.parseInt(parts[2]), "character", Integer.parseInt(parts[3])), "local-lsp-definition-tool-use");
                }
            }
            if (content.startsWith("tool lsp hover ")) {
                var payload = content.substring("tool lsp hover ".length());
                var parts = payload.split("::", 4);
                if (parts.length == 4) {
                    return new ModelResponse.ToolRequest("lsp", Map.of("operation", "hover", "language", parts[0], "uri", parts[1], "line", Integer.parseInt(parts[2]), "character", Integer.parseInt(parts[3])), "local-lsp-hover-tool-use");
                }
            }

            return new ModelResponse.AssistantReply("Query engine skeleton response for: \"%s\".".formatted(content));
        }

        return new ModelResponse.AssistantReply("Unhandled message type in local model client.");
    }
}
