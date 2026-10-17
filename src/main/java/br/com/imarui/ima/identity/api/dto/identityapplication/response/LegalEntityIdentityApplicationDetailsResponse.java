package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.LegalEntityIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "LegalEntityIdentityApplicationDetailsResponse")
public record LegalEntityIdentityApplicationDetailsResponse(
        @io.swagger.v3.oas.annotations.media.Schema(description = "Razão social da pessoa jurídica.", example = "Empresa Exemplo Ltda", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String legalName,
        @io.swagger.v3.oas.annotations.media.Schema(description = "Nome fantasia opcional.", example = "Empresa Exemplo", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY)
        String tradeName,
        @io.swagger.v3.oas.annotations.media.Schema(description = "CNPJ canônico de 14 dígitos.", example = "11222333000181", accessMode = io.swagger.v3.oas.annotations.media.Schema.AccessMode.READ_ONLY, requiredMode = io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED)
        String cnpj
) implements IdentityApplicationDetailsResponse {

    public static LegalEntityIdentityApplicationDetailsResponse from(
            LegalEntityIdentityApplicationDetailsResult result
    ) {
        Objects.requireNonNull(result, "result cannot be null");

        return new LegalEntityIdentityApplicationDetailsResponse(
                result.legalName(),
                result.tradeName(),
                result.cnpj()
        );
    }
}
