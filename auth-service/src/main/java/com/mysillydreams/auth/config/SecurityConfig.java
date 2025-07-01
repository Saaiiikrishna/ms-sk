package com.mysillydreams.auth.config;

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
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter; // For adding custom filter
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true, jsr250Enabled = true) // jsr250Enabled for @RolesAllowed if preferred
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class) // Necessary for Keycloak components
public class SecurityConfig extends KeycloakWebSecurityConfigurerAdapter {

    /**
     * Registers the KeycloakAuthenticationProvider with the authentication manager.
     * Sets a SimpleAuthorityMapper to ensure roles are prefixed with "ROLE_" if they aren't already.
     */
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        // SimpleAuthorityMapper to make sure roles are prefixed with ROLE_
        // Keycloak roles might not have this prefix by default.
        SimpleAuthorityMapper grantedAuthorityMapper = new SimpleAuthorityMapper();
        grantedAuthorityMapper.setPrefix("ROLE_");
        grantedAuthorityMapper.setConvertToUpperCase(true); // Convert roles to uppercase
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(grantedAuthorityMapper);
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }

    /**
     * Defines the session authentication strategy.
     * Uses RegisterSessionAuthenticationStrategy for public or confidential clients.
     */
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        // For bearer-only clients, NullAuthenticatedSessionStrategy is often used.
        // For services that might handle user sessions (even if primarily token-based),
        // RegisterSessionAuthenticationStrategy is appropriate.
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    /**
     * Provides Keycloak configuration resolver.
     * Reads configuration from application.yml/properties.
     */
    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    /**
     * Configures HTTP security rules.
     */
    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        http
            // Add custom filter for additional security headers
            .addFilterBefore(new AdditionalSecurityHeadersFilter(), BasicAuthenticationFilter.class)
            .cors().configurationSource(corsConfigurationSource()) // Apply CORS configuration
            .and()
            .csrf().disable() // Disable CSRF for stateless APIs (if using tokens primarily)
            .sessionManagement()
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS) // APIs are typically stateless
            .and()
            .authorizeRequests()
                // Permit all requests to actuator health and info endpoints for monitoring
                .antMatchers("/actuator/health", "/actuator/info", "/actuator/prometheus").permitAll()
                // Permit all requests to /auth/** for login, refresh etc.
                .antMatchers(HttpMethod.POST, "/auth/login").permitAll()
                .antMatchers(HttpMethod.POST, "/auth/refresh").permitAll()
                .antMatchers(HttpMethod.GET, "/auth/validate").permitAll() // As per PRD, should be protected if it reveals sensitive info
                                                                       // Or this could be an endpoint for opaque token introspection by other services
                // Secure password rotation endpoint - will be further secured by @PreAuthorize
                .antMatchers(HttpMethod.POST, "/auth/password-rotate").authenticated()
                // All other requests must be authenticated
                .anyRequest().authenticated();
    }

    /**
     * Configures CORS.
     * Replace with more restrictive settings for production.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // TODO: PRODUCTION - Restrict allowed origins. Do not use "*" in production.
        // Example: configuration.setAllowedOrigins(Arrays.asList("https://app.mysillydreams.com", "http://localhost:3000"));
        configuration.setAllowedOrigins(Arrays.asList("*")); // Development setting, should be overridden by profiles or external config
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS")); // PATCH is less common, include if used
        // Consider restricting allowed headers further if possible
        configuration.setAllowedHeaders(Arrays.asList(
                HttpHeaders.AUTHORIZATION,
                HttpHeaders.CONTENT_TYPE,
                HttpHeaders.ACCEPT,
                HttpHeaders.ORIGIN, // Important for CORS
                HttpHeaders.CACHE_CONTROL,
                "X-Requested-With"
        ));
        // Expose headers that clients might need to read
        configuration.setExposedHeaders(Arrays.asList(
                HttpHeaders.LOCATION,
                HttpHeaders.CONTENT_DISPOSITION,
                HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN,
                HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS
        ));
        configuration.setAllowCredentials(true); // Crucial for passing cookies or auth headers from frontend if origins are specific
        configuration.setMaxAge(3600L); // How long the results of a preflight request can be cached

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Apply this configuration to all paths
        return source;
    }
}
