package com.coderhino.services.trigger;

import java.util.Map;
import java.util.function.Consumer;

public final class NoOpRemoteTriggerService implements RemoteTriggerService {

    @Override
    public void registerHandler(String eventType, Consumer<Map<String, Object>> handler) {
    }

    @Override
    public void dispatch(String eventType, Map<String, Object> payload) {
    }

    @Override
    public boolean isRegistered(String eventType) {
        return false;
    }

    @Override
    public void unregisterHandler(String eventType) {
    }
}
