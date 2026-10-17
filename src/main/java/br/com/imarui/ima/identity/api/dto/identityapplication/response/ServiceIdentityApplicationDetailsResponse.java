package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.ServiceIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "ServiceIdentityApplicationDetailsResponse")
public record ServiceIdentityApplicationDetailsResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome da identidade técnica.", example = "Integração Exemplo", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String name,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Descrição opcional da identidade técnica.", example = "Integração de sistemas", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String description
) implements IdentityApplicationDetailsResponse {

    public static ServiceIdentityApplicationDetailsResponse from(
            ServiceIdentityApplicationDetailsResult result
    ) {
        Objects.requireNonNull(result, "result cannot be null");

        return new ServiceIdentityApplicationDetailsResponse(
                result.name(),
                result.description()
        );
    }
}
