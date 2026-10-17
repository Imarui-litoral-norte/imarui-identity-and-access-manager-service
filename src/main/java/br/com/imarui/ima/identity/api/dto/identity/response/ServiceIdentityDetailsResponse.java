package br.com.imarui.ima.identity.api.dto.identity.response;
import br.com.imarui.ima.identity.core.application.result.identity.ServiceIdentityDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;
@Schema(name = "ServiceIdentityDetailsResponse")
public record ServiceIdentityDetailsResponse(String name, String description) implements IdentityDetailsResponse {
    public static ServiceIdentityDetailsResponse from(ServiceIdentityDetailsResult result) {
        return new ServiceIdentityDetailsResponse(result.name(), result.description());
    }
}
