package com.coderhino.web.config;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
public class SpaRoutingConfig {

    @RequestMapping("/")
    public String forwardRoot() {
        return "forward:/index.html";
    }

    @RequestMapping({
        "/{path:^(?!api$)[^.]*}",
        "/{path:^(?!api$)[^.]*}/{subpath:[^.]*}",
        "/{path:^(?!api$)[^.]*}/{subpath:[^.]*}/{tail:[^.]*}"
    })
    public String forwardSpaRoutes() {
        return "forward:/index.html";
    }
}
