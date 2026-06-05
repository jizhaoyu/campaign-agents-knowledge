package com.enterprise.agentplatform.common.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class SpaForwardController {

    @GetMapping({
            "/",
            "/dashboard",
            "/knowledge",
            "/chat",
            "/tickets",
            "/approvals",
            "/ai-config",
            "/users",
            "/sessions",
            "/audits"
    })
    public String forwardWorkspaceRoutes() {
        return "forward:/index.html";
    }
}
