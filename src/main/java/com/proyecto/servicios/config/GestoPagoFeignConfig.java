package com.proyecto.servicios.config;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GestoPagoFeignConfig {

    @Value("${gestopago.auth.api-key:}")
    private String apiKey;

    @Bean
    public RequestInterceptor gestoPagoApiKeyInterceptor() {
        return requestTemplate -> {
            if (apiKey != null && !apiKey.isBlank() && !requestTemplate.headers().containsKey("X-API-Key")) {
                requestTemplate.header("X-API-Key", apiKey);
            }
        };
    }
}

