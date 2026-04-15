package com.coderhino.services.proactive;

public final class NoOpProactiveService implements ProactiveService {

    @Override
    public void enable() {
    }

    @Override
    public void disable() {
    }

    @Override
    public boolean isEnabled() {
        return false;
    }

    @Override
    public String scheduleWork(Runnable work, long delayMs) {
        return "noop-job";
    }

    @Override
    public String invokeBrief(String briefDescription) {
        return "brief:noop";
    }

    @Override
    public void shutdown() {
    }
}
