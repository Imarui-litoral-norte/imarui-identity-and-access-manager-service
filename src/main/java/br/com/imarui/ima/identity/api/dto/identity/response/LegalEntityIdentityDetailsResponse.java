package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.LegalEntityIdentityDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "LegalEntityIdentityDetailsResponse")
public record LegalEntityIdentityDetailsResponse(String legalName, String tradeName, String cnpj) implements IdentityDetailsResponse {
    public static LegalEntityIdentityDetailsResponse from(LegalEntityIdentityDetailsResult result) {
        return new LegalEntityIdentityDetailsResponse(result.legalName(), result.tradeName(), result.cnpj());
    }
}
