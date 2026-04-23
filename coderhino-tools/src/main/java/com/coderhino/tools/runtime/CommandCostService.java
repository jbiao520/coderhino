package com.coderhino.tools.runtime;

import java.util.Map;

public interface CommandCostService {
    void reset();

    Map<String, ModelUsage> allModelUsage();

    record ModelUsage(long inputTokens, long outputTokens, double costUsd) {
    }
}
