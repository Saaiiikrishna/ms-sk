package com.mysillydreams.inventoryapi.config;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakSecurityComponents;
import org.keycloak.adapters.springsecurity.authentication.KeycloakAuthenticationProvider;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
@EnableWebSecurity
// @EnableGlobalMethodSecurity(prePostEnabled = true) // Keep if @PreAuthorize is used elsewhere, but controller rules are now central
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class) // Ensure Keycloak components are scanned
public class KeycloakConfig extends KeycloakWebSecurityConfigurerAdapter {

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication manager.
     * Adds a SimpleAuthorityMapper to avoid prefixing roles with ROLE_ automatically.
     */
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        // By default, Spring Security prefixes roles with ROLE_.
        // Keycloak roles are typically defined without this prefix.
        // SimpleAuthorityMapper ensures that roles are not prefixed.
        SimpleAuthorityMapper grantedAuthorityMapper = new SimpleAuthorityMapper();
        // grantedAuthorityMapper.setPrefix(""); // Uncomment if roles from Keycloak should be used as-is without any prefix.
                                              // Or, ensure Keycloak roles are INVENTORY, ORDER_CORE not ROLE_INVENTORY, ROLE_ORDER_CORE
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(grantedAuthorityMapper);
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    /**
     * Defines the session authentication strategy.
     * For bearer-only applications like APIs, this might be less critical
     * than for UI applications, but it's standard Keycloak setup.
     */
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        // For bearer-only, NullAuthenticatedSessionStrategy can be used if sessions are not desired.
        // However, RegisterSessionAuthenticationStrategy is the default provided by Keycloak adapter.
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    /**
     * Use KeycloakSpringBootConfigResolver to allow Keycloak to resolve configuration from Spring Boot properties.
     */
    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    /**
     * Configures access rules for HTTP endpoints.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http); // Important: calls parent configuration including Keycloak setup
        http
            .csrf().disable() // Disable CSRF for stateless REST APIs
            .authorizeRequests()
                // Rule for GET /inventory/**
                .antMatchers(HttpMethod.GET, "/inventory/**")
                    .hasAnyRole("INVENTORY", "ORDER_CORE") // Roles as defined in Keycloak, without ROLE_ prefix
                // Rule for /inventory/adjust and /inventory/reserve
                .antMatchers("/inventory/adjust", "/inventory/reserve")
                    .hasRole("INVENTORY") // Role as defined in Keycloak
                // Actuator endpoints: permit all for simplicity in this context
                // In production, these should be secured appropriately
                .antMatchers("/actuator/**").permitAll()
                // Default rule: any other request must be authenticated
                .anyRequest().authenticated();
    }
}
