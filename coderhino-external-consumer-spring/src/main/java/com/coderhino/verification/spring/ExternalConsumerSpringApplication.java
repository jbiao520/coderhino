package com.coderhino.verification.spring;

import com.coderhino.agent.CoderhinoAgent;
import com.coderhino.verification.examples.spring.HardcodedCredentialProviderConfiguration;
import com.coderhino.verification.spring.chat.ChatAgentConfiguration;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@SpringBootApplication(proxyBeanMethods = false)
@Import({
    ChatAgentConfiguration.class,
    HardcodedCredentialProviderConfiguration.ProviderBeanConfiguration.class
})
public final class ExternalConsumerSpringApplication {

    private ExternalConsumerSpringApplication() {
    }

    public static void main(String[] args) {
        SpringApplication.run(ExternalConsumerSpringApplication.class, args);
    }

    @Bean
    ApplicationRunner startupProbe(CoderhinoAgent agent) {
        return args -> {
            System.out.println(
                "CoderhinoAgent ready via coderhino-agent-spring using "
                    + agent.config().modelClient().getClass().getSimpleName()
            );
        };
    }
}
