package com.coderhino.commands.builtin;

import com.coderhino.commands.CommandContext;
import com.coderhino.commands.CommandDefinition;

import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class TerminalSetupCommand implements CommandDefinition {
    private static final Map<String, String> NATIVE_TERMINALS = Map.of(
        "ghostty", "Ghostty",
        "kitty", "Kitty",
        "iterm.app", "iTerm2",
        "wezterm", "WezTerm",
        "warpterminal", "Warp"
    );

    @Override
    public String name() {
        return "terminal-setup";
    }

    @Override
    public String description() {
        return detectTerminal().equals("apple_terminal")
            ? "Enable Option+Enter support for multi-line prompts"
            : "Install Shift+Enter guidance for multi-line prompts";
    }

    @Override
    public List<String> aliases() {
        return List.of("terminal");
    }

    @Override
    public boolean hidden() {
        return nativeTerminalDisplayName() != null;
    }

    @Override
    public boolean webCompatible() {
        return false;
    }

    @Override
    public void execute(CommandContext context, String args) {
        var renderer = context.renderer();
        var terminal = detectTerminal();
        var nativeDisplayName = nativeTerminalDisplayName();

        if (args != null && !args.isBlank() && !args.trim().equals("status")) {
            renderer.printLine("Usage: /terminal-setup [status]");
            return;
        }

        renderer.printLine("Terminal setup");
        renderer.printLine("Detected terminal: " + displayTerminalName(terminal));

        if (nativeDisplayName != null) {
            renderer.printLine("Shift+Enter is natively supported in " + nativeDisplayName + ".");
            renderer.printLine("No configuration is required.");
            return;
        }

        switch (terminal) {
            case "apple_terminal" -> {
                renderer.printLine("Open Terminal > Settings > Profiles > Keyboard.");
                renderer.printLine("Enable 'Use Option as Meta key' for your active profile.");
                renderer.printLine("Optional: disable the bell for a quieter multiline workflow.");
            }
            case "vscode", "cursor", "windsurf" -> {
                var editorName = switch (terminal) {
                    case "cursor" -> "Cursor";
                    case "windsurf" -> "Windsurf";
                    default -> "VS Code";
                };
                renderer.printLine("Add a terminal keybinding in " + editorName + ":");
                renderer.printLine("  {\"key\":\"shift+enter\",\"command\":\"workbench.action.terminal.sendSequence\",\"args\":{\"text\":\"\\u001b\\r\"},\"when\":\"terminalFocus\"}");
                renderer.printLine("Keybindings file: " + vscodeKeybindingsPath(editorName));
            }
            case "alacritty" -> {
                renderer.printLine("Add a key binding to Alacritty:");
                renderer.printLine("  - { key: Return, mods: Shift, chars: \"\\u001b\\r\" }");
                renderer.printLine("Config file: ~/.config/alacritty/alacritty.toml (or alacritty.yml)");
            }
            case "zed" -> {
                renderer.printLine("Add a terminal binding to Zed keymap.json:");
                renderer.printLine("  {\"context\":\"Terminal\",\"bindings\":{\"shift-enter\":\"text::InsertNewline\"}}");
                renderer.printLine("Keymap file: ~/.config/zed/keymap.json");
            }
            default -> {
                renderer.printLine("Automatic guidance is available for Apple Terminal, VS Code, Cursor, Windsurf, Alacritty, and Zed.");
                renderer.printLine("You can still enter multi-line prompts with backslash + Enter in this terminal.");
            }
        }
    }

    private static String detectTerminal() {
        var termProgram = env("TERM_PROGRAM");
        var term = env("TERM");

        if (termProgram.equals("apple_terminal")) {
            return "apple_terminal";
        }
        if (termProgram.equals("vscode") || termProgram.equals("cursor") || termProgram.equals("windsurf") || termProgram.equals("zed")) {
            return termProgram;
        }
        if (term.contains("alacritty")) {
            return "alacritty";
        }
        return termProgram.isBlank() ? term : termProgram;
    }

    private static String env(String name) {
        return System.getenv().getOrDefault(name, "").toLowerCase(Locale.ROOT);
    }

    private static String nativeTerminalDisplayName() {
        return NATIVE_TERMINALS.get(detectTerminal());
    }

    private static String displayTerminalName(String terminal) {
        if (terminal == null || terminal.isBlank()) {
            return "unknown";
        }
        if (terminal.equals("apple_terminal")) {
            return "Apple Terminal";
        }
        return NATIVE_TERMINALS.getOrDefault(terminal, terminal);
    }

    private static Path vscodeKeybindingsPath(String editorName) {
        var home = Path.of(System.getProperty("user.home"));
        return Path.of(home.toString(), "Library", "Application Support", editorName.equals("VS Code") ? "Code" : editorName, "User", "keybindings.json");
    }
}
