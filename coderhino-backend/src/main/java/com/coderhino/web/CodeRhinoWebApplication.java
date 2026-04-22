package com.coderhino.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.event.EventListener;

@SpringBootApplication(
    scanBasePackages = "com.coderhino.web",
    exclude = {
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        HibernateJpaAutoConfiguration.class
    }
)
public class CodeRhinoWebApplication {

    private static final Logger log = LoggerFactory.getLogger(CodeRhinoWebApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(CodeRhinoWebApplication.class, args);
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (event.getApplicationContext() instanceof ServletWebServerApplicationContext ctx) {
            int port = ctx.getWebServer().getPort();
            String address = ctx.getEnvironment().getProperty("server.address", "127.0.0.1");
            log.info("Code Rhino Web UI started on http://{}:{}", address, port);
        }
    }
}
