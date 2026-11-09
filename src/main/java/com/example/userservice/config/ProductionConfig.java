package com.example.userservice.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import java.net.URI;
import java.util.Arrays;

@Configuration(proxyBeanMethods = false)
@Profile("prod")
public class ProductionConfig {
    @Bean static BeanFactoryPostProcessor productionTransportGuard(Environment env) {
        return factory -> {
            requireHttps(env.getRequiredProperty("app.security.issuer"));
            Arrays.stream(env.getRequiredProperty("app.security.cors-origins").split(",")).forEach(ProductionConfig::requireHttps);
        };
    }
    private static void requireHttps(String value) {
        var uri = URI.create(value.strip());
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null || value.contains("*"))
            throw new IllegalStateException("Production issuer and frontend origins must use explicit HTTPS URLs");
    }
}
