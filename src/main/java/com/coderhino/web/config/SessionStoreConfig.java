package com.coderhino.web.config;

import com.coderhino.state.SessionStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SessionStoreConfig {

    @Bean
    public SessionStore sessionStore() {
        return new SessionStore();
    }
}
