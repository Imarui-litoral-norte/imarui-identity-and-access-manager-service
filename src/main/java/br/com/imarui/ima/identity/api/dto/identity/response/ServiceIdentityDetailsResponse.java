package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.ServiceIdentityDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "ServiceIdentityDetailsResponse")
public record ServiceIdentityDetailsResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome da identidade técnica.", example = "Integração Exemplo", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String name,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Descrição opcional da identidade técnica.", example = "Integração de sistemas", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String description
) implements IdentityDetailsResponse {
    public static ServiceIdentityDetailsResponse from(ServiceIdentityDetailsResult result) {
        return new ServiceIdentityDetailsResponse(result.name(), result.description());
    }
}
