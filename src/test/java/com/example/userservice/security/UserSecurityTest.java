package com.example.userservice.security;
import com.example.userservice.config.SecurityConfig;
import com.example.userservice.core.audit.AuditLog;
import com.example.userservice.core.exception.ApiExceptionHandler;
import com.example.userservice.modules.user.controller.UserController;
import com.example.userservice.modules.user.service.UserService;
import com.example.userservice.modules.user.service.UserRegistrationService;
import com.example.userservice.modules.user.controller.UserRegistrationController;
import org.springframework.http.MediaType;
import com.example.userservice.modules.user.entity.*;
import com.example.userservice.modules.user.repository.UserRepository;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import org.springframework.security.oauth2.jwt.*;

import org.springframework.test.web.servlet.MockMvc;
import java.time.Instant;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig(UserSecurityTest.TestConfig.class)
@org.springframework.test.context.TestPropertySource(properties={"app.security.audience=user-service-api", "app.security.cors-origins=https://frontend"})
class UserSecurityTest {
    MockMvc mvc;
    UserService service; UserRegistrationService registration; UserRepository users; JwtDecoder decoder;
    @org.springframework.beans.factory.annotation.Autowired
    org.springframework.web.context.WebApplicationContext context;
    @org.springframework.boot.test.context.TestConfiguration(proxyBeanMethods=false)
    @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class, UserController.class, UserRegistrationController.class, AuditLog.class, ApiExceptionHandler.class})
    static class TestConfig {
        @Bean UserRegistrationService registrationService() { return mock(UserRegistrationService.class); }
        @Bean UserService userService() { return mock(UserService.class); }
        @Bean UserRepository userRepository() { return mock(UserRepository.class); }
        @Bean JwtDecoder jwtDecoder() { return mock(JwtDecoder.class); }
    }
    void startContext() {
        registration=context.getBean(UserRegistrationService.class); service=context.getBean(UserService.class); users=context.getBean(UserRepository.class); decoder=context.getBean(JwtDecoder.class);
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }
    private final UUID id=UUID.randomUUID();
    @BeforeEach void tokens() {
        startContext();
        reset(service,registration,users,decoder);
        when(decoder.decode("user-token")).thenReturn(jwt("USER")); when(decoder.decode("admin-token")).thenReturn(jwt("ADMIN"));
        when(users.findByKeycloakUserId("own-subject")).thenReturn(Optional.empty());
    }
    private Jwt jwt(String role) { return Jwt.withTokenValue(role).header("alg", "RS256").subject("own-subject")
        .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("realm_access",Map.of("roles",List.of(role))).build(); }
    @Test void missingTokenIs401() throws Exception { mvc.perform(get("/api/v1/users")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED")); }
    @Test void invalidTokenIs401() throws Exception {
        when(decoder.decode("invalid")).thenThrow(new BadJwtException("Invalid signature or expiry"));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
    }
    @Test void userCannotReadAnotherUserOrList() throws Exception {
        mvc.perform(get("/api/v1/users/"+id).header("Authorization","Bearer user-token")).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/users").header("Authorization","Bearer user-token")).andExpect(status().isForbidden());
        verifyNoInteractions(service);
    }
    @Test void userCannotCreateOrChangeStatusOrDelete() throws Exception {
        for (var request : List.of(post("/api/v1/users"), patch("/api/v1/users/"+id+"/status"), delete("/api/v1/users/"+id)))
            mvc.perform(request.header("Authorization","Bearer user-token")).andExpect(status().isForbidden());
    }
    @Test void selfLookupUsesVerifiedSubject() throws Exception {
        var user = new User(id,"own-subject","Name","a@example.com",null,null,UserStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH);
        when(service.me("own-subject")).thenReturn(user);
        mvc.perform(get("/api/v1/users/me").param("userId",UUID.randomUUID().toString()).header("Authorization","Bearer user-token"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").value(id.toString()));
        verify(service).me("own-subject");
    }
    @Test void disabledProfileRejectsStillValidAccessToken() throws Exception {
        when(users.findByKeycloakUserId("own-subject")).thenReturn(Optional.of(new User(id,"own-subject","Name","a@example.com",null,null,UserStatus.SUSPENDED,Instant.EPOCH,Instant.EPOCH)));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer user-token")).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }
    @Test void adminCanReadProfile() throws Exception {
        when(service.get(id)).thenReturn(new User(id,"some-subject","Name","a@example.com",null,null,UserStatus.ACTIVE,Instant.EPOCH,Instant.EPOCH));
        mvc.perform(get("/api/v1/users/"+id).header("Authorization","Bearer admin-token")).andExpect(status().isOk());
    }

    @Test void anonymousRegistrationIsAllowedAndNeverReturnsIdentityOrPassword() throws Exception {
        var key=UUID.randomUUID();
        mvc.perform(post("/api/v1/users/register").header("Idempotency-Key",key)
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"New User","email":"new@example.com","password":"Example-Password-42!"}
                """))
            .andExpect(status().isCreated()).andExpect(header().string("Cache-Control","no-store"))
            .andExpect(jsonPath("$.data.nextAction").value("SIGN_IN"))
            .andExpect(jsonPath("$.data.password").doesNotExist()).andExpect(jsonPath("$.data.id").doesNotExist());
        verify(registration).register(eq(key),argThat(c -> c.profile().email().equals("new@example.com")),anyString());
    }
    @Test void registrationValidatesPasswordAndIdempotencyHeaderBeforeCallingService() throws Exception {
        mvc.perform(post("/api/v1/users/register").header("Idempotency-Key",UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"New User","email":"new@example.com","password":"short"}
                """))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/users/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(registration);
    }
    @Test void registrationQuotaReturns429WithRetryAfter() throws Exception {
        doThrow(new com.example.userservice.core.exception.ServiceException(429,"REGISTRATION_RATE_LIMITED","Too many attempts"))
            .when(registration).register(any(),any(),anyString());
        mvc.perform(post("/api/v1/users/register").header("Idempotency-Key",UUID.randomUUID())
            .contentType(MediaType.APPLICATION_JSON).content("""
                {"fullName":"New User","email":"new@example.com","password":"Example-Password-42!"}
                """))
            .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","3600"));
    }
    @Test void metricsRequireAdministratorAuthority() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isUnauthorized());
        mvc.perform(get("/actuator/metrics").header("Authorization","Bearer user-token")).andExpect(status().isForbidden());
    }
}
