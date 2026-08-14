package br.com.imarui.ima.identity.core.domain.model.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.LegalName;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.TradeName;

import java.util.Objects;

public record LegalEntityIdentityApplicationDetails(
        LegalName legalName,
        TradeName tradeName,
        Cnpj cnpj
) implements IdentityApplicationDetails {

    public LegalEntityIdentityApplicationDetails {
        Objects.requireNonNull(legalName, "legalName cannot be null");
        Objects.requireNonNull(cnpj, "cnpj cannot be null");
    }

    @Override
    public IdentityKind kind() {
        return IdentityKind.LEGAL_ENTITY;
    }
}
