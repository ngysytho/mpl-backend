package com.example.userservice.core.security;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.*;
public class KeycloakRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private final String resourceClient;
    public KeycloakRolesConverter(String resourceClient) { this.resourceClient=resourceClient; }
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        add(jwt.getClaim("realm_access"), authorities);
        Object resources = jwt.getClaim("resource_access");
        if (resources instanceof Map<?, ?> map) add(map.get(resourceClient), authorities);
        return authorities;
    }
    private void add(Object access, Set<GrantedAuthority> authorities) {
        if (access instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> roles)
            roles.stream().filter(r -> "ADMIN".equals(r) || "USER".equals(r))
                 .forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r)));
    }
}
