package br.com.imarui.ima.identity.core.application.command.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

import java.time.LocalDate;

public record CreatePersonIdentityApplicationCommand(
        String fullName,
        String cpf,
        String email,
        LocalDate birthDate,
        String phoneNumber
) implements CreateIdentityApplicationCommand {

    @Override
    public IdentityKind kind() {
        return IdentityKind.PERSON;
    }
}
