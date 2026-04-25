package com.coderhino.permissions;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

public final class DenialTracker {

    public enum DenialState {
        ALLOWED,
        SOFT_DENY,
        HARD_DENY
    }

    public record TrackerState(DenialState state, int denialCount) {
    }

    private final int softDenialThreshold;
    private final int hardDenialThreshold;
    private final Map<String, AtomicReference<TrackerState>> toolStates;
    private final AtomicReference<TrackerState> globalState;

    public DenialTracker(int softDenialThreshold, int hardDenialThreshold) {
        if (softDenialThreshold < 1) softDenialThreshold = 3;
        if (hardDenialThreshold <= softDenialThreshold) hardDenialThreshold = softDenialThreshold + 2;
        this.softDenialThreshold = softDenialThreshold;
        this.hardDenialThreshold = hardDenialThreshold;
        this.toolStates = new ConcurrentHashMap<>();
        this.globalState = new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0));
    }

    public static DenialTracker createDefault() {
        return new DenialTracker(3, 5);
    }

    public DenialState recordDenial(String toolName) {
        DenialState newState = computeNextState(toolName, DenialState.SOFT_DENY);
        updateState(toolName, newState);
        updateGlobalState(newState);
        return getState(toolName);
    }

    public DenialState recordHardDenial(String toolName) {
        DenialState newState = DenialState.HARD_DENY;
        updateState(toolName, newState);
        updateGlobalState(DenialState.HARD_DENY);
        return DenialState.HARD_DENY;
    }

    public DenialState getState(String toolName) {
        return toolStates.getOrDefault(toolName, new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0))).get().state();
    }

    public TrackerState getTrackerState(String toolName) {
        return toolStates.getOrDefault(toolName, new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0))).get();
    }

    public DenialState getGlobalState() {
        return globalState.get().state();
    }

    public TrackerState getGlobalTrackerState() {
        return globalState.get();
    }

    public void reset(String toolName) {
        toolStates.put(toolName, new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0)));
    }

    public void resetAll() {
        toolStates.clear();
        globalState.set(new TrackerState(DenialState.ALLOWED, 0));
    }

    private DenialState computeNextState(String toolName, DenialState denialType) {
        TrackerState current = toolStates.getOrDefault(toolName, new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0))).get();
        int newCount = current.denialCount() + 1;

        if (current.state() == DenialState.HARD_DENY) {
            return DenialState.HARD_DENY;
        }
        if (newCount >= hardDenialThreshold) {
            return DenialState.HARD_DENY;
        }
        if (newCount >= softDenialThreshold) {
            return DenialState.HARD_DENY;
        }
        return denialType;
    }

    private void updateState(String toolName, DenialState newState) {
        TrackerState current = toolStates.getOrDefault(toolName, new AtomicReference<>(new TrackerState(DenialState.ALLOWED, 0))).get();
        int newCount = current.denialCount() + 1;
        toolStates.put(toolName, new AtomicReference<>(new TrackerState(newState, newCount)));
    }

    private void updateGlobalState(DenialState denialType) {
        globalState.updateAndGet(current -> {
            int newCount = current.denialCount() + 1;
            DenialState newState;
            if (current.state() == DenialState.HARD_DENY) {
                newState = DenialState.HARD_DENY;
            } else if (newCount >= hardDenialThreshold) {
                newState = DenialState.HARD_DENY;
            } else if (newCount >= softDenialThreshold) {
                newState = DenialState.SOFT_DENY;
            } else {
                newState = denialType;
            }
            return new TrackerState(newState, newCount);
        });
    }

    public int getSoftDenialThreshold() {
        return softDenialThreshold;
    }

    public int getHardDenialThreshold() {
        return hardDenialThreshold;
    }
}