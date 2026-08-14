package br.com.imarui.ima.identity.core.application.result.identityapplication;

import br.com.imarui.ima.identity.core.domain.model.identityapplication.ServiceIdentityApplicationDetails;

import java.util.Objects;

public record ServiceIdentityApplicationDetailsResult(
        String name,
        String description
) implements IdentityApplicationDetailsResult {

    public static ServiceIdentityApplicationDetailsResult from(
            ServiceIdentityApplicationDetails details
    ) {
        Objects.requireNonNull(details, "details cannot be null");

        return new ServiceIdentityApplicationDetailsResult(
                details.name().value(),
                details.description() == null
                        ? null
                        : details.description().value()
        );
    }
}
