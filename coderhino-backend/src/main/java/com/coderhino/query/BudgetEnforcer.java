package com.coderhino.query;

final class BudgetEnforcer {
    private static final double COST_PER_INPUT_TOKEN = 0.000003;
    private static final double COST_PER_OUTPUT_TOKEN = 0.000015;

    private final double maxBudgetUsd;

    BudgetEnforcer(double maxBudgetUsd) {
        this.maxBudgetUsd = maxBudgetUsd;
    }

    boolean isDisabled() {
        return maxBudgetUsd <= 0.0;
    }

    boolean isBudgetExceeded(UsageAccumulator accumulated) {
        if (isDisabled()) return false;
        double estimatedCost = accumulated.inputTokens() * COST_PER_INPUT_TOKEN
            + accumulated.outputTokens() * COST_PER_OUTPUT_TOKEN;
        return estimatedCost >= maxBudgetUsd;
    }

    double maxBudgetUsd() {
        return maxBudgetUsd;
    }
}
