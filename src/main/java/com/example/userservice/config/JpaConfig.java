package com.example.userservice.config;
import org.springframework.context.annotation.*;
import java.time.Clock;
@Configuration(proxyBeanMethods = false)
public class JpaConfig {
    @Bean Clock clock() { return Clock.systemUTC(); }
}
