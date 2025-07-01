package com.mysillydreams.userservice.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
    info = @Info(
        title = "User Service API",
        version = "v1.0",
        description = "API for managing user profiles, vendor onboarding, and related operations in the MySillyDreams Platform.",
        contact = @Contact(name = "API Support", email = "support@mysillydreams.com", url = "https://mysillydreams.com/support"),
        license = @License(name = "Apache 2.0", url = "http://www.apache.org/licenses/LICENSE-2.0.html")
    ),
    servers = {
        @Server(url = "http://localhost:8081", description = "Local development server for User Service"),
        @Server(url = "https://api.mysillydreams.com/user-service", description = "Production server (example)")
    }
)
// Define a global security scheme for JWT Bearer Authentication, if User Service endpoints are protected
// This might be more relevant if User Service itself validates tokens.
// If User Service relies on API Gateway for auth, this might be for information or for specific local test scenarios.
// For now, assuming some endpoints might require auth, similar to Auth-Service.
@SecurityScheme(
    name = "bearerAuthUser", // Unique name for this service's scheme if it's different or managed differently
    type = SecuritySchemeType.HTTP,
    scheme = "bearer",
    bearerFormat = "JWT",
    in = SecuritySchemeIn.HEADER,
    description = "JWT Bearer token authentication for User Service (typically validated by API Gateway, but defined for API clarity)"
)
public class OpenApiConfig {
    // This class is primarily for springdoc-openapi annotations.
    // No beans need to be defined here for basic setup.
    // Individual endpoints that require this security scheme should be annotated with:
    // @SecurityRequirement(name = "bearerAuthUser")
}
