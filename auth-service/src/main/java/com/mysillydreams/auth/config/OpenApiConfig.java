package com.mysillydreams.auth.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Auth Service API",
        version = "v1.0",
        description = "API for authentication, authorization, and token management in the MySillyDreams Platform."
        // You can add contact, license, etc. here
        // contact = @Contact(name = "API Support", email = "support@mysillydreams.com"),
        // license = @License(name = "Apache 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.html")
    )
    // You can also define global servers here if needed
    // servers = {
    //     @Server(url = "http://localhost:8080", description = "Local development server"),
    //     @Server(url = "https://api.mysillydreams.com", description = "Production server")
    // }
)
@SecurityScheme(
    name = "bearerAuth", // This name is referenced by @SecurityRequirement(name = "bearerAuth")
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "JWT Bearer token authentication"
)
public class OpenApiConfig {
    // This class is primarily for annotations for springdoc-openapi.
    // No beans need to be defined here for basic setup.
}
