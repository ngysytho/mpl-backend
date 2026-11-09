package com.example.userservice.core.security;
import com.example.userservice.modules.user.repository.UserRepository;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
public class ActiveUserFilter extends OncePerRequestFilter {
    private final UserRepository users;
    public ActiveUserFilter(UserRepository users) { this.users=users; }
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
        if (request.getRequestURI().startsWith("/api/")) {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication instanceof JwtAuthenticationToken jwt) {
                try {
                    var user = users.findByKeycloakUserId(jwt.getToken().getSubject());
                    if (user.isPresent() && !user.get().status().identityEnabled()) {
                        SecurityErrors.write(request, response, 403, "ACCOUNT_DISABLED"); return;
                    }
                } catch (RuntimeException failure) {
                    SecurityErrors.write(request, response, 503, "AUTHORIZATION_UNAVAILABLE"); return;
                }
            }
        }
        chain.doFilter(request, response);
    }
}
