package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;
import com.coderhino.services.voice.VoiceService;

public final class VoiceCommand implements CommandDefinition {

    @Override
    public String name() {
        return "voice";
    }

    @Override
    public String description() {
        return "Manage voice mode (enable/disable/status)";
    }

    @Override
    public boolean webCompatible() {
        return false;
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var sub = args == null ? "" : args.trim();
        var voice = context.services().voiceService();

        if (sub.isEmpty() || sub.equals("status")) {
            renderer.printLine("Voice mode: " + (voice.isEnabled() ? "ENABLED" : "DISABLED"));
            renderer.printLine("Current mode: " + voice.currentMode());
            renderer.printLine("Usage: /voice [enable|disable|status]");
        } else if (sub.equals("enable")) {
            voice.enable();
            renderer.printLine("Voice mode enabled. Mode: " + voice.currentMode());
        } else if (sub.equals("disable")) {
            voice.disable();
            renderer.printLine("Voice mode disabled.");
        } else {
            renderer.printLine("Unknown voice subcommand: " + sub);
            renderer.printLine("Usage: /voice [enable|disable|status]");
        }
    }
}
