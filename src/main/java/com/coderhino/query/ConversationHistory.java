package com.coderhino.query;

import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;

import java.util.ArrayList;
import java.util.List;

final class ConversationHistory {
    ConversationHistory() {
    }

    record CurrentTurn(String visibleUserInput, String rawUserInput) {
        CurrentTurn {
            if (visibleUserInput == null || rawUserInput == null) {
                throw new IllegalArgumentException("Current turn inputs must be non-null");
            }
        }
    }

    ArrayList<Message> build(BootstrapState bootstrapState, CurrentTurn currentTurn) {
        var history = new ArrayList<Message>();
        var messages = bootstrapState.get().messages();
        history.addAll(messages);

        if (history.isEmpty()) {
            history.add(new Message.UserMessage(currentTurn.rawUserInput()));
            return history;
        }

        var lastMessage = history.get(history.size() - 1);
        if (lastMessage instanceof Message.UserMessage userMessage
            && userMessage.content().equals(currentTurn.visibleUserInput())) {
            history.set(history.size() - 1, new Message.UserMessage(currentTurn.rawUserInput()));
            return history;
        }

        history.add(new Message.UserMessage(currentTurn.rawUserInput()));
        return history;
    }

    static List<Message> withAssistantToolUse(List<Message> history, Message.AssistantToolUseMessage toolUseMessage, Message.ToolResultMessage toolResultMessage) {
        var updated = new ArrayList<>(history);
        updated.add(toolUseMessage);
        updated.add(toolResultMessage);
        return updated;
    }
}
