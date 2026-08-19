package com.training.cvmanagementbe.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Configures OpenAPI (Swagger) documentation and API contract generation.
 *
 * <p>Serves as the single source of truth for frontend client code generation and API specification.
 *
 * <p>Export command: {@code curl http://localhost:8080/api/v3/api-docs.yaml > openapi.yaml}
 */
@Configuration
public class OpenApiConfig {
    @Bean
    OpenAPI openApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("CV Management API")
                        .version("0.1.0")
                        .description("""
                                Internal system for maintaining employee CVs.
 
                                **Errors.** Every failure returns the same body with a stable
                                `code`. Clients branch on the code, never on the message.
 
                                - `400` malformed request or failed validation
                                - `401` not signed in, or the session was revoked
                                - `403` authenticated, but outside the caller's data scope
                                - `404` no such item
                                - `409` someone else changed the item first
                                - `422` well-formed request that breaks a business rule
 
                                **Data scoping.** List endpoints already return exactly the rows
                                the caller may see. Clients must not filter by role: doing so would
                                conceal a server-side scoping mistake rather than prevent one.
                                """)
                        .license(new License().name("Internal")))
                .servers(List.of(new Server().url("/api").description("Same origin, behind the proxy")))
                .components(new Components().addSecuritySchemes("bearerAuth",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }
}
