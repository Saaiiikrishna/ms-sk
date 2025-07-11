package com.mysillydreams.catalogservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri:#{null}}")
    private String jwkSetUri; // Read from application properties, e.g., Keycloak's JWKS URI

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // In-memory users for local development and testing ONLY
    // This bean will only be created if the "local-dev" or "test" profile is active
    // AND jwkSetUri is not configured (signaling a real IdP setup).
    @Bean
    @Profile({"local-dev", "test"})
    public UserDetailsService inMemoryUserDetailsService(PasswordEncoder passwordEncoder) {
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            // If JWK URI is set, we assume JWT auth is primary, so don't create in-memory users
            // Or, allow both for mixed environments if desired, but typically one auth method is primary.
            return null; // Or an empty UserDetailsService
        }
        UserDetails user = User.builder()
                .username("user")
                .password(passwordEncoder.encode("password"))
                .roles("USER")
                .build();
        UserDetails catalogManager = User.builder()
                .username("manager")
                .password(passwordEncoder.encode("password"))
                .roles("CATALOG_MANAGER", "INVENTORY_MANAGER")
                .build();
        UserDetails admin = User.builder()
                .username("admin")
                .password(passwordEncoder.encode("password"))
                .roles("ADMIN", "CATALOG_MANAGER", "INVENTORY_MANAGER", "USER")
                .build();
        return new InMemoryUserDetailsManager(user, catalogManager, admin);
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(authz -> authz
                .requestMatchers(HttpMethod.GET, "/api/v1/categories/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/items/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/pricing/items/**/price-detail").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/pricing/items/**/bulk-rules").permitAll()
                .requestMatchers("/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                .requestMatchers("/actuator/**").hasRole("ADMIN") // Or more specific actuator role
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().authenticated()
            )
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));

        // Configure either JWT resource server or HTTP Basic based on jwkSetUri presence
        if (jwkSetUri != null && !jwkSetUri.isEmpty()) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));
            // For custom JWT claim to authority mapping, configure here:
            // .jwtAuthenticationConverter(jwtAuthenticationConverter())
            // Ensure roles are prefixed with "ROLE_" by default or customize authority prefix.
            // Default is "SCOPE_" for scopes. For roles from Keycloak, often need a custom converter.
        } else {
            // Fallback to HTTP Basic if no JWT config (e.g., for local-dev without Keycloak)
            // This relies on the inMemoryUserDetailsService being active.
            http.httpBasic(Customizer.withDefaults());
        }

        return http.build();
    }

    // Bean for JwtDecoder if jwkSetUri is configured
    // This will only be created if jwkSetUri is present.
    @Bean
    @Profile("!local-dev & !test") // Example: Don't create for local-dev/test if they use basic auth primarily
    public JwtDecoder jwtDecoder() {
        if (jwkSetUri == null || jwkSetUri.isEmpty()) {
            // This scenario should ideally not happen if profiles are managed correctly.
            // Or, if JWT is optional, return null or a no-op decoder.
            // For now, assume if this bean is attempted to be created, jwkSetUri must be valid.
            throw new IllegalStateException("JWK Set URI is not configured for JWT decoding. Please set spring.security.oauth2.resourceserver.jwt.jwk-set-uri");
        }
        return NimbusJwtDecoder.withJwkSetUri(this.jwkSetUri).build();
    }

    // Example of a custom JWT Authentication Converter (if needed for roles from Keycloak)
    /*
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter grantedAuthoritiesConverter = new JwtGrantedAuthoritiesConverter();
        // Configure to read roles from a specific claim, e.g., "realm_access.roles"
        // By default, it looks for "scope" or "scp" claims.
        // For Keycloak roles, often they are in a nested claim like "realm_access" -> "roles"
        // or "resource_access" -> "your-client-id" -> "roles".
        // You might need a custom converter to extract these and add "ROLE_" prefix.
        // See Spring Security docs for JwtAuthenticationConverter and custom converters.

        // Simple example if roles are flat in a "roles" claim (add "ROLE_" prefix):
        // grantedAuthoritiesConverter.setAuthoritiesClaimName("roles");
        // grantedAuthoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter jwtConverter = new JwtAuthenticationConverter();
        jwtConverter.setJwtGrantedAuthoritiesConverter(grantedAuthoritiesConverter);
        return jwtConverter;
    }
    */
    }
}
