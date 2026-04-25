package com.coderhino.query;

public record PromptAssemblyResult(
    String defaultSystemPrompt,
    String userContext,
    String systemContext,
    String systemPrompt
) {
}
