package com.coderhino.verification.spring.chat;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.tools.ToolDefinition;
import com.coderhino.tools.ToolRegistry;
import com.coderhino.tools.builtin.*;
import com.coderhino.tools.runtime.ToolCommandRegistry;
import com.coderhino.verification.examples.spring.OrderQueryTool;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;

@Configuration(proxyBeanMethods = false)
public class ChatAgentConfiguration {

    @Bean
    ToolRegistry coderhinoToolRegistry() {
//        return ToolRegistry.createDefault();
        var tools = java.util.List.<ToolDefinition<?, ?>>of(
                new AgentTool(),
                new AskUserQuestionTool(),
                new BashTool(),
                new BriefTool(),
                new ConfigTool(),
                new CronCreateTool(),
                new CronDeleteTool(),
                new CronListTool(),
                new EnterPlanModeTool(),
                new EnterWorktreeTool(),
                new ExitPlanModeTool(),
                new ExitWorktreeTool(),
                new FileReadTool(),
                new FileWriteTool(),
                new FileEditTool(),
                new NotebookEditTool(),
                new GlobTool(),
                new GrepTool(),
                new LspTool(),
                new ListMcpResourcesTool(),
                new MCPTool(),
                new ReadMcpResourceTool(),
                new REPLTool(),
                new RemoteTriggerTool(),
                new SendMessageTool(),
                new SkillTool(),
                new TaskCreateTool(),
                new TaskGetTool(),
                new TaskListTool(),
                new TaskOutputTool(),
                new TaskStopTool(),
                new TaskUpdateTool(),
                new TeamCreateTool(),
                new TeamDeleteTool(),
                new TodoCreateTool(),
                new TodoWriteTool(),
                new WebFetchTool(),
                new OrderQueryTool()
        );

        var preliminary = new ToolRegistry(tools);
        var allTools = new java.util.ArrayList<>(tools);
        allTools.add(new SleepTool());
        allTools.add(new SyntheticOutputTool());
        allTools.add(new ToolSearchTool(preliminary));
        return new ToolRegistry(allTools);
    }

    @Bean
    CommandRegistry coderhinoCommandRegistry() {
        return CommandRegistry.createDefault(Path.of("").toAbsolutePath().normalize());
    }

    @Bean
    ToolCommandRegistry coderhinoToolCommandRegistry(CommandRegistry coderhinoCommandRegistry) {
        return coderhinoCommandRegistry.asToolCommandRegistry();
    }
}
