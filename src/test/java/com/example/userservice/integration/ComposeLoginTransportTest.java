package com.example.userservice.integration;
import org.junit.jupiter.api.Test;
import java.net.URI;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
class ComposeLoginTransportTest {
    @Test void secureLoopbackCookiesRemainRestrictedToLoginPathAndHost() {
        assertEquals("AUTH_SESSION_ID=fixture", ComposeSmokeIT.loginCookies(List.of(
            "AUTH_SESSION_ID=fixture; Path=/realms/user-service/; Secure; HttpOnly",
            "foreign=value; Domain=example.com; Path=/", "wrong=value; Path=/admin",
            "prefix=value; Path=/realms/user", "expired=value; Max-Age=0; Path=/"),
            URI.create("http://localhost:8081/realms/user-service/login-actions/authenticate")));
        assertThrows(IllegalArgumentException.class, () -> ComposeSmokeIT.loginCookies(List.of(), URI.create("https://example.com/login")));
    }
    @Test void formAndRedirectOriginIncludesSchemeHostAndPort() {
        URI expected=URI.create("http://localhost:8081");
        assertTrue(ComposeSmokeIT.sameOrigin(expected,URI.create("http://localhost:8081/login")));
        for (String url : List.of("https://localhost:8081/login","http://localhost:8082/login","http://example.com:8081/login"))
            assertFalse(ComposeSmokeIT.sameOrigin(expected,URI.create(url)));
    }
}
