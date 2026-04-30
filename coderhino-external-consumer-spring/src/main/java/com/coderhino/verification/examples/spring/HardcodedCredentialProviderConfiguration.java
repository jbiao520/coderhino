package com.coderhino.verification.examples.spring;

import com.coderhino.agent.spring.CoderhinoAgentCredentialProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public final class HardcodedCredentialProviderConfiguration {
    public static final String EXAMPLE_API_KEY = "sk-omTdJ1LhU2qcqU3SqsZeN7_7oqdUOVawJuXbfD-akkuwZz6R217eJmrhZ9GtBt-k27oys6zcY7tVNo";

    private HardcodedCredentialProviderConfiguration() {
    }

    @Configuration(proxyBeanMethods = false)
    public static class ProviderBeanConfiguration {
        @Bean
        CoderhinoAgentCredentialProvider coderhinoAgentCredentialProvider() {
            return () -> EXAMPLE_API_KEY;
        }
    }
}
