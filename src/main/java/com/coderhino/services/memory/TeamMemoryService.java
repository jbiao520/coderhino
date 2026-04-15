package com.coderhino.services.memory;

import java.util.List;

public interface TeamMemoryService {

    void share(String sessionId, List<String> facts, String teamId);

    List<String> recall(String teamId);

    void sync(String teamId);
}
