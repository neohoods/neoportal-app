package com.neohoods.portal.platform.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
@ConditionalOnProperty(
    name = {"neohoods.portal.matrix.enabled"},
    havingValue = "true",
    matchIfMissing = false
)
public class MatrixBotConfig {

    @Value("${neohoods.portal.matrix.disabled:false}")
    private boolean disabled;

    @Bean
    public RestTemplate matrixRestTemplate() {
        return new RestTemplate();
    }

    /**
     * Check if Matrix bot is actually enabled (enabled=true AND disabled=false)
     */
    public boolean isMatrixBotEnabled() {
        return !disabled;
    }
}






