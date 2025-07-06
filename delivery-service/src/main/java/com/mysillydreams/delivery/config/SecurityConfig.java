package com.mysillydreams.delivery.config;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakSecurityComponents;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
@EnableWebSecurity
// EnableGlobalMethodSecurity allows @PreAuthorize, @PostAuthorize, @Secured, @RolesAllowed
// jsr250Enabled = true enables @RolesAllowed
@EnableGlobalMethodSecurity(prePostEnabled = true, jsr250Enabled = true)
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class)
public class SecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    /**
     * Configure HttpSecurity for Delivery Service.
     * Secures delivery assignment endpoints and actuator endpoints.
     * Allows WebSocket upgrade requests.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);

        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeRequests()
                // Allow WebSocket handshake requests (path configured in WebSocketConfig is /delivery-updates/gps)
                // Permitting all for WS path, actual auth might happen at WebSocket connection/message level if needed.
                .antMatchers("/delivery-updates/gps/**").permitAll()

                // Secure delivery assignment management endpoints
                // These are typically called by couriers (mobile app) or internal systems.
                .antMatchers(HttpMethod.POST, "/delivery/assignments/*/arrive-pickup").hasRole("DELIVERY")
                .antMatchers(HttpMethod.POST, "/delivery/assignments/*/pickup-photo").hasRole("DELIVERY")
                .antMatchers(HttpMethod.POST, "/delivery/assignments/*/gps").hasRole("DELIVERY") // GPS updates from courier app
                .antMatchers(HttpMethod.POST, "/delivery/assignments/*/arrive-dropoff").hasRole("DELIVERY")
                .antMatchers(HttpMethod.POST, "/delivery/assignments/*/deliver").hasRole("DELIVERY")
                // Add other assignment related endpoints if any (e.g., GET for courier to see their assignments)
                // .antMatchers(HttpMethod.GET, "/delivery/assignments/my").hasRole("DELIVERY")

                // Secure actuator endpoints
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                .antMatchers("/actuator/**").hasRole("ADMIN") // Or a more specific "OPS_ADMIN" role

                // Any other request to this service must be authenticated (e.g., if there are other internal APIs)
                // If no other APIs, this could be denyAll().
                .anyRequest().authenticated();
    }

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication manager.
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        // For Keycloak, roles are typically not prefixed with "ROLE_".
        // SimpleAuthorityMapper can add the prefix if hasRole("DELIVERY") is used.
        // Or, use hasAuthority("DELIVERY") or hasAuthority("ROLE_DELIVERY") depending on Keycloak setup and mapper.
        // If Keycloak roles are "delivery-role", "admin-role", then hasRole("delivery-role") might not work
        // without a mapper. hasAuthority("delivery-role") would.
        // Let's assume roles from Keycloak are "DELIVERY", "ADMIN".
        // For hasRole("DELIVERY") to work directly, SimpleAuthorityMapper is often needed to add "ROLE_" prefix.
        // Or, ensure Keycloak roles are "ROLE_DELIVERY", "ROLE_ADMIN".
        // For this example, let's add the SimpleAuthorityMapper.
        var keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        var authoritiesMapper = new SimpleAuthorityMapper();
        authoritiesMapper.setConvertToUpperCase(true); // Optional: convert roles to uppercase
        // authoritiesMapper.setPrefix("ROLE_"); // This is the key part for hasRole()
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(authoritiesMapper);
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        // Uses Spring Boot configuration resolver (application.yml/properties)
        return new KeycloakSpringBootConfigResolver();
    }
}
