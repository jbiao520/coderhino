package com.coderhino.query;

import com.coderhino.types.Message;

import java.util.List;

public record QueryRequest(
    List<Message> messages,
    String systemPrompt,
    String customSystemPrompt,
    String appendSystemPrompt,
    List<ToolSchema> tools
) {
}
