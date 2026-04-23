package com.coderhino.commands;

import com.coderhino.tools.runtime.ToolCommand;
import com.coderhino.tools.runtime.ToolCommandRegistry;

import com.coderhino.commands.builtin.AddDirCommand;
import com.coderhino.commands.builtin.AgentsCommand;
import com.coderhino.commands.builtin.AutofixPrCommand;
import com.coderhino.commands.builtin.BranchCommand;
import com.coderhino.commands.builtin.BugCommand;
import com.coderhino.commands.builtin.ClearCommand;
import com.coderhino.commands.builtin.CommitCommand;
import com.coderhino.commands.builtin.CommitPushPrCommand;
import com.coderhino.commands.builtin.CompactCommand;
import com.coderhino.commands.builtin.ConfigCommand;
import com.coderhino.commands.builtin.ContextCommand;
import com.coderhino.commands.builtin.CopyCommand;
import com.coderhino.commands.builtin.CostCommand;
import com.coderhino.commands.builtin.DesktopCommand;
import com.coderhino.commands.builtin.DiffCommand;
import com.coderhino.commands.builtin.DoctorCommand;
import com.coderhino.commands.builtin.EffortCommand;
import com.coderhino.commands.builtin.EnvCommand;
import com.coderhino.commands.builtin.ExitCommand;
import com.coderhino.commands.builtin.ExportCommand;
import com.coderhino.commands.builtin.FeedbackCommand;
import com.coderhino.commands.builtin.FilesCommand;
import com.coderhino.commands.builtin.HelpCommand;
import com.coderhino.commands.builtin.HooksCommand;
import com.coderhino.commands.builtin.InitCommand;
import com.coderhino.commands.builtin.InsightsCommand;
import com.coderhino.commands.builtin.KeybindingsCommand;
import com.coderhino.commands.builtin.LspCommand;
import com.coderhino.commands.builtin.McpCommand;
import com.coderhino.commands.builtin.MemoryCommand;
import com.coderhino.commands.builtin.MobileCommand;
import com.coderhino.commands.builtin.ModelCommand;
import com.coderhino.commands.builtin.PermissionsCommand;
import com.coderhino.commands.builtin.PlanCommand;
import com.coderhino.commands.builtin.PluginCommand;
import com.coderhino.commands.builtin.PrCommentsCommand;
import com.coderhino.commands.builtin.ReadCommand;
import com.coderhino.commands.builtin.ReleaseNotesCommand;
import com.coderhino.commands.builtin.RenameCommand;
import com.coderhino.commands.builtin.ResumeCommand;
import com.coderhino.commands.builtin.ReviewCommand;
import com.coderhino.commands.builtin.RewindCommand;
import com.coderhino.commands.builtin.SecurityReviewCommand;
import com.coderhino.commands.builtin.SessionCommand;
import com.coderhino.commands.builtin.ShareCommand;
import com.coderhino.commands.builtin.SkillsCommand;
import com.coderhino.commands.builtin.StatusCommand;
import com.coderhino.commands.builtin.SummaryCommand;
import com.coderhino.commands.builtin.TaskCommand;
import com.coderhino.commands.builtin.TasksCommand;
import com.coderhino.commands.builtin.TerminalSetupCommand;
import com.coderhino.commands.builtin.ThemeCommand;
import com.coderhino.commands.builtin.UltraplanCommand;
import com.coderhino.commands.builtin.UsageCommand;
import com.coderhino.commands.builtin.VerboseCommand;
import com.coderhino.commands.builtin.VersionCommand;
import com.coderhino.commands.builtin.VimCommand;
import com.coderhino.commands.builtin.VoiceCommand;

import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class CommandRegistry {
    private final Map<String, CommandDefinition> commands;
    private final List<CommandDefinition> definitions;

    public CommandRegistry(Collection<? extends CommandDefinition> definitions) {
        this.commands = new LinkedHashMap<>();
        this.definitions = List.copyOf(new LinkedHashSet<>(definitions));
        this.definitions.forEach(definition -> {
            register(definition.name(), definition);
            definition.aliases().forEach(alias -> register(alias, definition));
        });
    }

    public static CommandRegistry createDefault() {
        return createDefault(Path.of("").toAbsolutePath().normalize());
    }

    public static CommandRegistry createDefault(Path cwd) {
        var builtIns = builtInDefinitions();
        var reservedNames = reservedNames(builtIns);
        var markdownDefinitions = new MarkdownPromptLoader().load(cwd).stream()
            .map(MarkdownCommandDefinition::new)
            .filter(definition -> !reservedNames.contains(definition.name()))
            .toList();
        var merged = new java.util.ArrayList<CommandDefinition>(builtIns.size() + markdownDefinitions.size());
        merged.addAll(builtIns);
        merged.addAll(markdownDefinitions);
        return new CommandRegistry(merged);
    }

    private static List<CommandDefinition> builtInDefinitions() {
        return List.of(
            new AddDirCommand(),
            new AgentsCommand(),
            new AutofixPrCommand(),
            new BranchCommand(),
            new BugCommand(),
            new ClearCommand(),
            new CommitCommand(),
            new CommitPushPrCommand(),
            new CompactCommand(),
            new ConfigCommand(),
            new ContextCommand(),
            new CopyCommand(),
            new CostCommand(),
            new DesktopCommand(),
            new DiffCommand(),
            new DoctorCommand(),
            new EffortCommand(),
            new EnvCommand(),
            new ExitCommand(),
            new ExportCommand(),
            new FeedbackCommand(),
            new FilesCommand(),
            new HelpCommand(),
            new HooksCommand(),
            new InitCommand(),
            new InsightsCommand(),
            new KeybindingsCommand(),
            new LspCommand(),
            new McpCommand(),
            new MemoryCommand(),
            new MobileCommand(),
            new ModelCommand(),
            new PermissionsCommand(),
            new PlanCommand(),
            new PluginCommand(),
            new PrCommentsCommand(),
            new ReadCommand(),
            new ReleaseNotesCommand(),
            new RenameCommand(),
            new ResumeCommand(),
            new ReviewCommand(),
            new RewindCommand(),
            new SecurityReviewCommand(),
            new SessionCommand(),
            new ShareCommand(),
            new SkillsCommand(),
            new StatusCommand(),
            new SummaryCommand(),
            new TaskCommand(),
            new TasksCommand(),
            new TerminalSetupCommand(),
            new ThemeCommand(),
            new UltraplanCommand(),
            new UsageCommand(),
            new VerboseCommand(),
            new VersionCommand(),
            new VimCommand(),
            new VoiceCommand()
        );
    }

    public Optional<CommandDefinition> find(String name) {
        return Optional.ofNullable(commands.get(name));
    }

    public ToolCommandRegistry asToolCommandRegistry() {
        return name -> find(name).map(CommandAdapter::new);
    }

    public Collection<CommandDefinition> all() {
        return definitions;
    }

    private void register(String name, CommandDefinition definition) {
        var previous = commands.putIfAbsent(name, definition);
        if (previous != null && previous != definition) {
            throw new IllegalArgumentException("Duplicate command registration for name or alias: " + name);
        }
    }

    private static Set<String> reservedNames(List<CommandDefinition> definitions) {
        Set<String> reserved = new HashSet<>();
        for (CommandDefinition definition : definitions) {
            reserved.add(definition.name());
            reserved.addAll(definition.aliases());
        }
        return reserved;
    }

    private record CommandAdapter(CommandDefinition definition) implements ToolCommand {
        @Override
        public String name() {
            return definition.name();
        }

        @Override
        public String description() {
            return definition.description();
        }

        @Override
        public boolean includeInModelContext() {
            return definition.includeInModelContext();
        }

        @Override
        public boolean promptBacked() {
            return definition instanceof PromptBackedCommand;
        }

        @Override
        public String prompt(String args) {
            return ((PromptBackedCommand) definition).prompt(args);
        }

        @Override
        public List<String> allowedTools() {
            return definition instanceof PromptBackedCommand promptBacked
                ? promptBacked.allowedTools()
                : ToolCommand.super.allowedTools();
        }
    }
}
