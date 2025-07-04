package com.mysillydreams.catalogservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "Catalog Service API",
        version = "v1",
        description = "APIs for managing product catalog, stock, pricing, and shopping carts."
    )
    // servers = @Server(url = "http://localhost:8082") // Optional: Define server URL
)
@SecurityScheme(
    name = "bearerAuth",
    type = SecuritySchemeType.HTTP,
    bearerFormat = "JWT",
    scheme = "bearer",
    in = SecuritySchemeIn.HEADER,
    description = "JWT token for authentication"
)
// For Basic Auth, Swagger UI usually provides fields by default if Basic Auth is enabled in Spring Security.
// If we want to explicitly define Basic Auth:
// @SecurityScheme(name = "basicAuth", type = SecuritySchemeType.HTTP, scheme = "basic")
public class OpenApiConfig {
    // No explicit beans needed here if using annotations for definition
}
