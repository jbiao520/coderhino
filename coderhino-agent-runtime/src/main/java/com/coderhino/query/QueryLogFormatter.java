package com.coderhino.query;

import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;

import java.util.Map;

final class QueryLogFormatter {
    private static final int MAX_PREVIEW_LENGTH = 160;

    private QueryLogFormatter() {
    }

    static String sessionId(BootstrapState bootstrapState) {
        return sessionId(bootstrapState.get());
    }

    static String sessionId(AppState state) {
        if (state == null || state.sessionRuntime() == null || state.sessionRuntime().sessionId() == null) {
            return "unknown";
        }
        return state.sessionRuntime().sessionId().toString();
    }

    static String cwd(BootstrapState bootstrapState) {
        return cwd(bootstrapState.get());
    }

    static String cwd(AppState state) {
        if (state == null || state.cwd() == null || state.cwd().isBlank()) {
            return "unknown";
        }
        return state.cwd();
    }

    static String summarizeUserInput(String input) {
        return "len=" + lengthOf(input);
    }

    static String summarizeArguments(Map<String, Object> arguments) {
        if (arguments == null || arguments.isEmpty()) {
            return "count=0";
        }
        return "count=" + arguments.size() + ", keys=" + arguments.keySet().stream().sorted().toList();
    }

    static String summarizeResult(String result) {
        return "len=" + lengthOf(result);
    }

    static String summarizeUsage(ModelResponse.Usage usage) {
        if (usage == null) {
            return "input=0, output=0, cacheWrite=0, cacheRead=0";
        }
        return "input=%d, output=%d, cacheWrite=%d, cacheRead=%d"
            .formatted(usage.inputTokens(), usage.outputTokens(), usage.cacheCreationTokens(), usage.cacheReadTokens());
    }

    static String summarizeContent(String content) {
        if (content == null) {
            return "len=0";
        }
        return "len=%d, preview=\"%s\"".formatted(content.length(), abbreviate(singleLine(content)));
    }

    private static int lengthOf(String value) {
        return value == null ? 0 : value.length();
    }

    private static String singleLine(String value) {
        return value.replaceAll("\\s+", " ").trim();
    }

    private static String abbreviate(String value) {
        if (value.length() <= MAX_PREVIEW_LENGTH) {
            return value;
        }
        return value.substring(0, MAX_PREVIEW_LENGTH - 3) + "...";
    }
}
