package com.mysillydreams.orderapi.config;

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
import org.springframework.security.core.authority.mapping.SimpleAuthorityMapper;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(jsr250Enabled = true) // Enables @RolesAllowed
@ComponentScan(basePackageClasses = KeycloakSecurityComponents.class)
public class KeycloakConfig extends KeycloakWebSecurityConfigurerAdapter {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        http
            .csrf().disable() // Disable CSRF for stateless APIs
            .authorizeRequests()
                .antMatchers(HttpMethod.POST, "/orders/**").hasRole("USER")
                .antMatchers(HttpMethod.PUT, "/orders/**/cancel").hasRole("USER")
                // GET requests for orders might be accessible to other roles or unsecured depending on requirements
                // For now, let's assume only USER can access their orders.
                // Specific GET endpoints like /orders/{id} would need further thought if admins need access etc.
                .antMatchers(HttpMethod.GET, "/orders/**").hasRole("USER")
                .anyRequest().authenticated();
    }

    // Defines the session authentication strategy.
    @Bean
    @Override
    protected SessionAuthenticationStrategy sessionAuthenticationStrategy() {
        // Use RegisterSessionAuthenticationStrategy for bearer-only applications.
        return new RegisterSessionAuthenticationStrategy(new SessionRegistryImpl());
    }

    // Use Keycloak Spring Boot adapter integration.
    @Bean
    public KeycloakConfigResolver keycloakConfigResolver() {
        return new KeycloakSpringBootConfigResolver();
    }

    // Optional: Register KeycloakAuthenticationProvider with the authentication manager.
    // By default, Spring Security uses a PlaintextPasswordEncoder if an AuthenticationManagerBuilder is not customized.
    // This ensures Keycloak is used for authentication.
    @Override
    protected void configure(AuthenticationManagerBuilder auth) throws Exception {
        auth.authenticationProvider(keycloakAuthenticationProvider());
    }

    // Optional: If you want to prefix roles with "ROLE_", you can configure it here.
    // By default, Keycloak roles are not prefixed.
    // For hasRole("USER") to work, roles from Keycloak should be "USER".
    // If Keycloak roles are "user", then use hasAuthority("ROLE_user") or map authorities.
    // For simplicity, this example assumes Keycloak roles are uppercase (e.g., "USER").
    // If not, a SimpleAuthorityMapper can be used:
    /*
    @Autowired
    public void configureGlobal(AuthenticationManagerBuilder auth) throws Exception {
        KeycloakAuthenticationProvider keycloakAuthenticationProvider = keycloakAuthenticationProvider();
        keycloakAuthenticationProvider.setGrantedAuthoritiesMapper(new SimpleAuthorityMapper());
        auth.authenticationProvider(keycloakAuthenticationProvider);
    }
    */
}
