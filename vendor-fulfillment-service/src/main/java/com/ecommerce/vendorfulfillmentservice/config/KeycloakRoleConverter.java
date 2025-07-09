package com.ecommerce.vendorfulfillmentservice.config;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class KeycloakRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter defaultGrantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();

    private static final String REALM_ACCESS_CLAIM = "realm_access";
    private static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    private static final String ROLES_CLAIM = "roles";
    private static final String ROLE_PREFIX = "ROLE_";

    // Optionally, configure a specific client ID if you want to use client-specific roles
    // @Value("${keycloak.resource-client-id}") // Example: load from properties
    // private String resourceClientId;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = Stream.concat(
                defaultGrantedAuthoritiesConverter.convert(jwt).stream(),
                extractKeycloakRoles(jwt).stream()
        ).collect(Collectors.toSet()); // Use Set to avoid duplicates

        return new JwtAuthenticationToken(jwt, authorities);
    }

    private Collection<GrantedAuthority> extractKeycloakRoles(Jwt jwt) {
        Map<String, Object> claims = jwt.getClaims();

        // 1. Try Realm Access roles
        if (claims.containsKey(REALM_ACCESS_CLAIM)) {
            Map<String, Object> realmAccess = (Map<String, Object>) claims.get(REALM_ACCESS_CLAIM);
            if (realmAccess != null && realmAccess.containsKey(ROLES_CLAIM)) {
                Collection<String> roleNames = (Collection<String>) realmAccess.get(ROLES_CLAIM);
                if (roleNames != null) {
                    return roleNames.stream()
                            .map(roleName -> new SimpleGrantedAuthority(ROLE_PREFIX + roleName.toUpperCase()))
                            .collect(Collectors.toList());
                }
            }
        }

        // 2. Try Resource Access roles (client-specific roles)
        // This part can be enabled and configured if client-specific roles are used.
        /*
        if (resourceClientId != null && claims.containsKey(RESOURCE_ACCESS_CLAIM)) {
            Map<String, Object> resourceAccess = (Map<String, Object>) claims.get(RESOURCE_ACCESS_CLAIM);
            if (resourceAccess.containsKey(resourceClientId)) {
                Map<String, Object> clientAccess = (Map<String, Object>) resourceAccess.get(resourceClientId);
                if (clientAccess != null && clientAccess.containsKey(ROLES_CLAIM)) {
                    Collection<String> roleNames = (Collection<String>) clientAccess.get(ROLES_CLAIM);
                    if (roleNames != null) {
                        return roleNames.stream()
                                .map(roleName -> new SimpleGrantedAuthority(ROLE_PREFIX + roleName.toUpperCase()))
                                .collect(Collectors.toList());
                    }
                }
            }
        }
        */

        return Collections.emptyList();
    }
}
