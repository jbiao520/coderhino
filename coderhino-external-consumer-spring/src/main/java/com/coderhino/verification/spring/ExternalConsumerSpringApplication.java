package com.coderhino.verification.spring;

import com.coderhino.agent.CoderhinoAgent;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(proxyBeanMethods = false)
public final class ExternalConsumerSpringApplication {

    private ExternalConsumerSpringApplication() {
    }

    public static void main(String[] args) {
        new SpringApplicationBuilder(ExternalConsumerSpringApplication.class)
            .web(WebApplicationType.NONE)
            .run(args);
    }

    @Bean
    ApplicationRunner startupProbe(CoderhinoAgent agent) {
        CoderhinoAgent.AgentResult agentResult = agent.run("explain the current repo");
        System.out.println(agentResult.finalText());
        return args -> System.out.println(
            "CoderhinoAgent ready via coderhino-agent-spring using "
                + agent.config().modelClient().getClass().getSimpleName()
        );
    }
}
