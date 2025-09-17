package com.neohoods.portal.platform.config;

import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;
import reactor.core.publisher.Hooks;

@Configuration
public class ReactiveContextConfig {

    @PostConstruct
    public void init() {
        Hooks.enableAutomaticContextPropagation();
        System.out.println("DEBUG: ReactiveContextConfig - Automatic context propagation enabled");
    }
}
