package com.coderhino.hooks;

public enum HookEvent {
    BEFORE_TOOL_USE("beforeToolUse"),
    AFTER_TOOL_USE("afterToolUse"),
    BEFORE_COMMAND("beforeCommand"),
    AFTER_COMMAND("afterCommand");

    private final String jsonKey;

    HookEvent(String jsonKey) { this.jsonKey = jsonKey; }

    public String jsonKey() { return jsonKey; }

    public static HookEvent fromJsonKey(String key) {
        for (HookEvent e : values()) {
            if (e.jsonKey.equals(key)) return e;
        }
        throw new IllegalArgumentException("Unknown hook event key: " + key);
    }
}
