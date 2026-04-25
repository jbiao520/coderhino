package com.coderhino.tools.runtime;

public interface CommandVoiceService {
    void enable();

    void disable();

    boolean isEnabled();

    String currentMode();
}
