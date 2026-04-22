package com.coderhino.query;

import com.coderhino.state.BootstrapState;
import com.coderhino.types.Message;

final class ResponsePersistence {
    void persist(BootstrapState bootstrapState, Message.AssistantMessage assistantMessage) {
        bootstrapState.addMessage(assistantMessage);
    }
}
