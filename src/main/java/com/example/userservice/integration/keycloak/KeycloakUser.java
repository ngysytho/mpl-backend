package com.example.userservice.integration.keycloak;
import com.example.userservice.modules.user.service.IdentityManagementPort.Identity;
import tools.jackson.databind.JsonNode;
import java.util.*;
public final class KeycloakUser {
    private KeycloakUser() {}
    public static Identity from(JsonNode node) {
        Map<String, List<String>> attributes = new HashMap<>();
        JsonNode attrs = node.path("attributes");
        for (String name : attrs.propertyNames()) {
            List<String> values = new ArrayList<>(); attrs.path(name).forEach(v -> values.add(v.asText()));
            attributes.put(name, List.copyOf(values));
        }
        return new Identity(node.path("id").asText(), node.path("username").asText(), node.path("email").asText(),
                            node.path("firstName").asText(), node.path("enabled").asBoolean(), Map.copyOf(attributes));
    }
}
