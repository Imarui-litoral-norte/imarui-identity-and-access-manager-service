package br.com.imarui.ima.identity.core.application.result.identityapplication;

import br.com.imarui.ima.identity.core.domain.model.identityapplication.LegalEntityIdentityApplicationDetails;

import java.util.Objects;

public record LegalEntityIdentityApplicationDetailsResult(
        String legalName,
        String tradeName,
        String cnpj
) implements IdentityApplicationDetailsResult {

    public static LegalEntityIdentityApplicationDetailsResult from(
            LegalEntityIdentityApplicationDetails details
    ) {
        Objects.requireNonNull(details, "details cannot be null");

        return new LegalEntityIdentityApplicationDetailsResult(
                details.legalName().value(),
                details.tradeName() == null
                        ? null
                        : details.tradeName().value(),
                details.cnpj().value()
        );
    }
}
