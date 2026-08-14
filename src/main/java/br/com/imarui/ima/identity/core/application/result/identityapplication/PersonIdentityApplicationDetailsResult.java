package br.com.imarui.ima.identity.core.application.result.identityapplication;

import br.com.imarui.ima.identity.core.domain.model.identityapplication.PersonIdentityApplicationDetails;

import java.time.LocalDate;
import java.util.Objects;

public record PersonIdentityApplicationDetailsResult(
        String fullName,
        String cpf,
        LocalDate birthDate,
        String phoneNumber
) implements IdentityApplicationDetailsResult {

    public static PersonIdentityApplicationDetailsResult from(
            PersonIdentityApplicationDetails details
    ) {
        Objects.requireNonNull(details, "details cannot be null");

        return new PersonIdentityApplicationDetailsResult(
                details.fullName().value(),
                details.cpf().value(),
                details.birthDate(),
                details.phoneNumber() == null
                        ? null
                        : details.phoneNumber().value()
        );
    }
}
