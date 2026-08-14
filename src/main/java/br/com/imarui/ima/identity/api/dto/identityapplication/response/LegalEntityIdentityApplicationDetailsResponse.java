package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.LegalEntityIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "LegalEntityIdentityApplicationDetailsResponse")
public record LegalEntityIdentityApplicationDetailsResponse(
        String legalName,
        String tradeName,
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
