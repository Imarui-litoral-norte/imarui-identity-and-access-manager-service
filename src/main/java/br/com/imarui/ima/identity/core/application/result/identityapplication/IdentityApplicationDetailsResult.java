package br.com.imarui.ima.identity.core.application.result.identityapplication;

import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.LegalEntityIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.PersonIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.ServiceIdentityApplicationDetails;

import java.util.Objects;

public sealed interface IdentityApplicationDetailsResult
        permits PersonIdentityApplicationDetailsResult,
                LegalEntityIdentityApplicationDetailsResult,
                ServiceIdentityApplicationDetailsResult {

    static IdentityApplicationDetailsResult from(
            IdentityApplicationDetails details
    ) {
        Objects.requireNonNull(details, "details cannot be null");

        if (details instanceof PersonIdentityApplicationDetails person) {
            return PersonIdentityApplicationDetailsResult.from(person);
        }

        if (details instanceof LegalEntityIdentityApplicationDetails legalEntity) {
            return LegalEntityIdentityApplicationDetailsResult.from(legalEntity);
        }

        if (details instanceof ServiceIdentityApplicationDetails service) {
            return ServiceIdentityApplicationDetailsResult.from(service);
        }

        throw new IllegalArgumentException(
                "Unsupported identity application details: "
                        + details.getClass().getName()
        );
    }
}
