package br.com.imarui.ima.identity.api.dto.identityapplication.response;

import br.com.imarui.ima.identity.core.application.result.identityapplication.ServiceIdentityApplicationDetailsResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.Objects;

@Schema(name = "ServiceIdentityApplicationDetailsResponse")
public record ServiceIdentityApplicationDetailsResponse(
        String name,
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
