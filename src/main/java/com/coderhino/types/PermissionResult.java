package com.coderhino.types;

public sealed interface PermissionResult permits PermissionResult.Allow, PermissionResult.Ask, PermissionResult.Deny {
    boolean allowed();

    record Allow() implements PermissionResult {
        @Override
        public boolean allowed() {
            return true;
        }
    }

    record Ask(String reason) implements PermissionResult {
        @Override
        public boolean allowed() {
            return false;
        }
    }

    record Deny(String reason) implements PermissionResult {
        @Override
        public boolean allowed() {
            return false;
        }
    }

    static Allow allow() {
        return new Allow();
    }

    static Ask ask(String reason) {
        return new Ask(reason);
    }

    static Deny deny(String reason) {
        return new Deny(reason);
    }
}
