package br.com.imarui.ima.platform.openapi;
import org.springframework.context.annotation.*;
import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

@Configuration
public class IdentityOpenApiConfiguration {
    @Bean
    public OpenAPI identityApi() {
        return new OpenAPI().info(new Info().title("Imarui IAM - Identity API").version("v1")
            .description("Identity lifecycle, onboarding, tenants and contextual affiliations. Timestamps are UTC at microsecond precision."))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }
}
