package com.coderhino.query;

record ThinkingModeConfig(boolean enabled, int budgetTokens, boolean extendedThinking) {
    static final ThinkingModeConfig DISABLED = new ThinkingModeConfig(false, 0, false);
    static final int DEFAULT_BUDGET_TOKENS = 8000;

    static ThinkingModeConfig withBudget(int budgetTokens) {
        return new ThinkingModeConfig(true, budgetTokens, false);
    }

    static ThinkingModeConfig extended(int budgetTokens) {
        return new ThinkingModeConfig(true, budgetTokens, true);
    }
}
