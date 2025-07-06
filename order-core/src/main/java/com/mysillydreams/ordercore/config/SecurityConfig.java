package com.mysillydreams.ordercore.config;

import org.keycloak.adapters.KeycloakConfigResolver;
import org.keycloak.adapters.springboot.KeycloakSpringBootConfigResolver;
import org.keycloak.adapters.springsecurity.KeycloakSecurityComponents;
import org.keycloak.adapters.springsecurity.config.KeycloakWebSecurityConfigurerAdapter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true) // Enables @RolesAllowed for future use (e.g., InternalOrderController)
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class) // Required for Keycloak
public class SecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    /**
     * Configure HttpSecurity.
     * For Order-Core, if it primarily acts as a backend service with no user-facing HTTP endpoints
     * other than potentially actuator endpoints or a secured internal API, the rules will differ
     * from a typical front-facing API.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http); // It's important to call super.configure(http) first.

        http
            .csrf().disable() // Disable CSRF, common for non-browser clients
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS) // Stateless sessions
            .and()
            .authorizeRequests()
                // Secure actuator endpoints - customize as needed
                .antMatchers("/actuator/health", "/actuator/info").permitAll()
                .antMatchers("/actuator/**").hasRole("ADMIN") // Example: secure other actuator endpoints
                // .antMatchers("/internal/orders/**").hasRole("ORDER_ADMIN") // For future InternalOrderController
                .anyRequest().denyAll(); // Default deny if no HTTP endpoints are meant to be public
                                         // Or .anyRequest().authenticated() if all other paths require some auth.
                                         // If Order-Core has NO HTTP endpoints (only Kafka), this could be .anyRequest().permitAll()
                                         // and rely on network policies, or simply .anyRequest().denyAll() to be safe.
                                         // Given an optional InternalOrderController, let's deny by default for now.
    }

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication manager.
     */
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(keycloakAuthenticationProvider());
    }

    /**
     * Defines the session authentication strategy.
     * For bearer-only applications (like backend services), RegisterSessionAuthenticationStrategy is used.
     */
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    /**
     * Use Keycloak Spring Boot adapter integration.
     * This allows Keycloak configuration to be loaded from application.yml/properties.
     */
    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    // Optional: Configure GrantedAuthoritiesMapper if roles need prefixing (e.g., "ROLE_")
    // @Autowired
    // public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
    //     KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
    //     keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(new SimpleAuthorityMapper());
    //     auth.authenticationProvider(keycloakAuthenticationProvider);
    // }
}
