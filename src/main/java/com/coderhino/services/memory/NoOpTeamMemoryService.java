package com.coderhino.services.memory;

import java.util.List;

public final class NoOpTeamMemoryService implements TeamMemoryService {

    @Override
    public void share(String sessionId, List<String> facts, String teamId) {
    }

    @Override
    public List<String> recall(String teamId) {
        return List.of();
    }

    @Override
    public void sync(String teamId) {
    }
}
