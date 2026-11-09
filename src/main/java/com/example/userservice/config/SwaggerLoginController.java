package com.example.userservice.config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@Controller
@ConditionalOnProperty(name="app.security.docs-enabled", havingValue="true")
public class SwaggerLoginController {
    @GetMapping("/swagger-ui/index.html")
    public String index() { return "redirect:/docs/index.html"; }
    @ResponseBody @GetMapping("/docs/config")
    public Map<String,String> config(@Value("${app.security.swagger-client-id:user-service-frontend}") String clientId) {
        return Map.of("clientId", clientId);
    }
}
