package br.com.imarui.ima.identity.core.domain.model.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityFullName;
import br.com.imarui.ima.identity.core.domain.model.identity.PhoneNumber;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;

import java.time.LocalDate;
import java.util.Objects;

public record PersonIdentityApplicationDetails(
        IdentityFullName fullName,
        Cpf cpf,
        LocalDate birthDate,
        PhoneNumber phoneNumber
) implements IdentityApplicationDetails {

    public PersonIdentityApplicationDetails {
        Objects.requireNonNull(fullName, "fullName cannot be null");
        Objects.requireNonNull(cpf, "cpf cannot be null");
    }

    @Override
    public IdentityKind kind() {
        return IdentityKind.PERSON;
    }
}
