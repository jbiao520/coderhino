package com.coderhino.types;

/**
 * Permission modes controlling how tool permissions are resolved.
 * Each mode represents a different strategy for handling permission requests.
 */
public enum PermissionMode {
    /** Normal operation with user prompts for sensitive operations. */
    DEFAULT,

    /** Plan mode with restricted operations and limited permissions. */
    PLAN,

    /** Bypass all permission checks - allow everything. */
    BYPASS,

    /** Auto mode - automatically approve based on internal heuristics. */
    AUTO,

    /** Don't ask mode - never prompt, auto-deny if not clearly safe. */
    DONT_ASK,

    /** Accept edits mode - auto-accept file edits but ask for destructive operations. */
    ACCEPT_EDITS
}
