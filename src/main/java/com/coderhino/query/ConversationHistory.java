package com.coderhino.query;

import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;

import java.util.ArrayList;
import java.util.List;

final class ConversationHistory {
    ConversationHistory() {
    }

    ArrayList<Message> build(BootstrapState bootstrapState, String userInput) {
        var history = new ArrayList<Message>();
        var messages = bootstrapState.get().messages();
        var alreadyPresentAsLatestUserMessage = !messages.isEmpty()
            && messages.get(messages.size() - 1) instanceof Message.UserMessage userMessage
            && userMessage.content().equals(userInput);
        history.addAll(messages);
        if (!alreadyPresentAsLatestUserMessage) {
            history.add(new Message.UserMessage(userInput));
        }
        return history;
    }

    static List<Message> withAssistantToolUse(List<Message> history, Message.AssistantToolUseMessage toolUseMessage, Message.ToolResultMessage toolResultMessage) {
        var updated = new ArrayList<>(history);
        updated.add(toolUseMessage);
        updated.add(toolResultMessage);
        return updated;
    }
}
