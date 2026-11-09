package com.example.userservice.config;
import com.example.userservice.core.security.*;
import com.example.userservice.modules.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.*;
import java.util.List;
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {
    @Bean SecurityFilterChain security(HttpSecurity http, UserRepository users, @Value("${app.security.audience}") String audience,
                                      @Value("${app.security.docs-enabled:false}") boolean docsEnabled) throws Exception {
        var converter = new JwtAuthenticationConverter(); converter.setJwtGrantedAuthoritiesConverter(new KeycloakRolesConverter(audience));
        http.csrf(csrf -> csrf.disable()).cors(cors -> {}).sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .requestCache(cache -> cache.disable()).formLogin(form -> form.disable()).httpBasic(basic -> basic.disable())
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health", "/actuator/health/liveness", "/actuator/health/readiness").permitAll();
                auth.requestMatchers("/actuator/metrics", "/actuator/metrics/**").hasRole("ADMIN");
                if (docsEnabled) auth.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/docs/**").permitAll();
                auth.requestMatchers(HttpMethod.POST, "/api/v1/users/register").permitAll();
                auth.requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                    .requestMatchers(HttpMethod.PATCH, "/api/v1/users/me").authenticated()
                    .requestMatchers("/api/v1/users", "/api/v1/users/**").hasRole("ADMIN").anyRequest().denyAll();
            })
            .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint((req,res,e) -> SecurityErrors.write(req,res,401,"UNAUTHORIZED"))
                .accessDeniedHandler((req,res,e) -> SecurityErrors.write(req,res,403,"FORBIDDEN")))
            .exceptionHandling(e -> e.authenticationEntryPoint((req,res,x) -> SecurityErrors.write(req,res,401,"UNAUTHORIZED"))
                .accessDeniedHandler((req,res,x) -> SecurityErrors.write(req,res,403,"FORBIDDEN")))
            .addFilterAfter(new ActiveUserFilter(users), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
    @Bean CorsConfigurationSource cors(@Value("${app.security.cors-origins}") List<String> origins) {
        var config = new CorsConfiguration(); config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("X-Request-ID", "Location", "Retry-After")); config.setAllowCredentials(false); config.setMaxAge(3600L);
        var source = new UrlBasedCorsConfigurationSource(); source.registerCorsConfiguration("/api/**", config); return source;
    }
}
