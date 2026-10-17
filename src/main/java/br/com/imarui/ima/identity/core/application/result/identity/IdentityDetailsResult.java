package br.com.imarui.ima.identity.core.application.result.identity;

import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.identity.person.PersonIdentity;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.LegalEntityIdentity;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceIdentity;

public sealed interface IdentityDetailsResult permits PersonIdentityDetailsResult, LegalEntityIdentityDetailsResult, ServiceIdentityDetailsResult {
    static IdentityDetailsResult from(Identity identity) {
        if (identity instanceof PersonIdentity p) return new PersonIdentityDetailsResult(p.getFullName().value(), p.getCpf().value(), p.getBirthDate(), p.getPhoneNumber() == null ? null : p.getPhoneNumber().phoneNumber().value());
        if (identity instanceof LegalEntityIdentity p) return new LegalEntityIdentityDetailsResult(p.getLegalName().value(), p.getTradeName() == null ? null : p.getTradeName().value(), p.getCnpj().value());
        if (identity instanceof ServiceIdentity p) return new ServiceIdentityDetailsResult(p.getName().value(), p.getDescription() == null ? null : p.getDescription().value());
        throw new IllegalArgumentException("Unsupported identity kind.");
    }
}
