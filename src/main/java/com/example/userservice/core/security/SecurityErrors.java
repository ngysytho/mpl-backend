package com.example.userservice.core.security;
import jakarta.servlet.http.*;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Map;
public final class SecurityErrors {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private SecurityErrors() {}
    public static void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        if (status == 401) response.setHeader("WWW-Authenticate", "Bearer");
        response.getWriter().write(JSON.writeValueAsString(Map.of("type", "urn:mpl:error:" + code.toLowerCase(java.util.Locale.ROOT).replace('_','-'),
            "title", status == 401 ? "Unauthorized" : status == 403 ? "Forbidden" : "Service unavailable",
            "status", status, "code", code, "detail", "Request could not be authorized",
            "traceId", String.valueOf(request.getAttribute("traceId")))));
    }
}
