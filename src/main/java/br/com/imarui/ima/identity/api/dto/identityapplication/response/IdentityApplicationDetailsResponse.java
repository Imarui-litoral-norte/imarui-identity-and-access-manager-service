package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationDetailsResult;
import br.com.imarui.ima.identity.core.application.result.identityapplication.LegalEntityIdentityApplicationDetailsResult;
import br.com.imarui.ima.identity.core.application.result.identityapplication.PersonIdentityApplicationDetailsResult;
import br.com.imarui.ima.identity.core.application.result.identityapplication.ServiceIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(
        description = "Dados especificos do tipo de identidade solicitado.",
        oneOf = {
                PersonIdentityApplicationDetailsResponse.class,
                LegalEntityIdentityApplicationDetailsResponse.class,
                ServiceIdentityApplicationDetailsResponse.class
        }
)
public sealed interface IdentityApplicationDetailsResponse
        permits PersonIdentityApplicationDetailsResponse,
                LegalEntityIdentityApplicationDetailsResponse,
                ServiceIdentityApplicationDetailsResponse {

    static IdentityApplicationDetailsResponse from(
            IdentityApplicationDetailsResult result
    ) {
        Objects.requireNonNull(result, "details result cannot be null");

        if (result instanceof PersonIdentityApplicationDetailsResult person) {
            return PersonIdentityApplicationDetailsResponse.from(person);
        }

        if (result instanceof LegalEntityIdentityApplicationDetailsResult legalEntity) {
            return LegalEntityIdentityApplicationDetailsResponse.from(legalEntity);
        }

        if (result instanceof ServiceIdentityApplicationDetailsResult service) {
            return ServiceIdentityApplicationDetailsResponse.from(service);
        }

        throw new IllegalArgumentException(
                "Unsupported identity application details result: "
                        + result.getClass().getName()
        );
    }
}
