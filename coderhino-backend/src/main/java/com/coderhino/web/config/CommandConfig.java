package com.coderhino.web.config;

import com.coderhino.commands.CommandRegistry;
import com.coderhino.services.ServiceRegistry;
import com.coderhino.state.AppState;
import com.coderhino.state.BootstrapState;
import com.coderhino.state.SessionRuntime;
import com.coderhino.types.PermissionMode;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.file.Path;
import java.util.List;

@Configuration
public class CommandConfig {

    @Bean
    public CommandRegistry commandRegistry() {
        return CommandRegistry.createDefault();
    }

    @Bean
    public ServiceRegistry serviceRegistry() {
        return ServiceRegistry.createDefault(Path.of("").toAbsolutePath().normalize());
    }

    @Bean
    public BootstrapState bootstrapState() {
        var cwd = Path.of("").toAbsolutePath().normalize().toString();
        return new BootstrapState(new AppState(
            false,
            null,
            cwd,
            false,
            true,
            PermissionMode.BYPASS,
            0.0,
            new SessionRuntime(SessionRuntime.create().sessionId(), null, null, List.of(), List.of(), List.of()),
            List.of()
        ));
    }
}
