package com.coderhino.query;

import com.coderhino.context.ContextSnapshot;

import java.util.ArrayList;

final class PromptAssembler {
    PromptAssemblyResult assemble(ContextSnapshot snapshot, String customSystemPrompt, String appendSystemPrompt) {
        var systemContext = snapshot.systemContext();
        var userContext = snapshot.userContext();
        var defaultSystemPrompt = joinNonBlank(systemContext, userContext);

        var systemPrompt = hasText(customSystemPrompt) ? customSystemPrompt : defaultSystemPrompt;
        if (hasText(appendSystemPrompt)) {
            systemPrompt = joinNonBlank(systemPrompt, appendSystemPrompt);
        }

        return new PromptAssemblyResult(defaultSystemPrompt, userContext, systemContext, systemPrompt);
    }

    private String joinNonBlank(String first, String second) {
        var parts = new ArrayList<String>();
        if (hasText(first)) {
            parts.add(first);
        }
        if (hasText(second)) {
            parts.add(second);
        }
        return String.join(System.lineSeparator() + System.lineSeparator(), parts);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
