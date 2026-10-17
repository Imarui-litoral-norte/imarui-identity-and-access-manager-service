package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.*;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(oneOf = {PersonIdentityDetailsResponse.class, LegalEntityIdentityDetailsResponse.class, ServiceIdentityDetailsResponse.class})
public sealed interface IdentityDetailsResponse permits PersonIdentityDetailsResponse, LegalEntityIdentityDetailsResponse, ServiceIdentityDetailsResponse {
    static IdentityDetailsResponse from(IdentityDetailsResult result) {
        if (result instanceof PersonIdentityDetailsResult value) return PersonIdentityDetailsResponse.from(value);
        if (result instanceof LegalEntityIdentityDetailsResult value) return LegalEntityIdentityDetailsResponse.from(value);
        if (result instanceof ServiceIdentityDetailsResult value) return ServiceIdentityDetailsResponse.from(value);
        throw new IllegalArgumentException("Unsupported identity details.");
    }
}
