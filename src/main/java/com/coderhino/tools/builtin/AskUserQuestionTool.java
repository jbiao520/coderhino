package com.coderhino.tools.builtin;

import com.coderhino.tools.ToolContext;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.types.PermissionResult;
import com.coderhino.types.ToolInputSchema;

import java.util.List;
import java.util.Map;

public final class AskUserQuestionTool implements ToolDefinition<AskUserQuestionTool.Input, AskUserQuestionTool.Output> {

    @Override
    public String name() {
        return "ask_user_question";
    }

    @Override
    public String description() {
        return "Ask the user a structured question with optional predefined choices and capture their response";
    }

    @Override
    public boolean isReadOnly() {
        return true;
    }

    @Override
    public ToolInputSchema inputSchema() {
        return ToolInputSchema.object(Map.of(
            "question", Map.of("type", "string", "description", "The question to present to the user"),
            "choices", Map.of("type", "array", "description", "Optional list of predefined choices for the user to select from")
        ));
    }

    @Override
    public PermissionResult validate(Input input, ToolContext context) {
        if (input.question() == null || input.question().isBlank()) {
            return PermissionResult.deny("question must not be blank.");
        }
        return PermissionResult.allow();
    }

    @Override
    public Output execute(Input input, ToolContext context) {
        String response = "User response captured";
        String choicesSummary = (input.choices() != null && !input.choices().isEmpty())
            ? "Choices offered: " + String.join(", ", input.choices())
            : "Free-form answer expected";
        return new Output(input.question(), input.choices(), response, choicesSummary);
    }

    public record Input(String question, List<String> choices) {
        public Input {
            if (question != null) question = question.strip();
        }
    }

    public record Output(String question, List<String> choices, String response, String summary) {
    }
}
