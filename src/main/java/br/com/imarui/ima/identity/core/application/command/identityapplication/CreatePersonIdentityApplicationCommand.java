package br.com.imarui.ima.identity.core.application.command.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

import java.time.LocalDate;

public record CreatePersonIdentityApplicationCommand(
        String fullName,
        String cpf,
        String email,
        LocalDate birthDate,
        String phoneNumber
, String nickname
) implements CreateIdentityApplicationCommand {

    public CreatePersonIdentityApplicationCommand(String fullName, String cpf, String email, LocalDate birthDate, String phoneNumber) { this(fullName, cpf, email, birthDate, phoneNumber, null); }

    @Override
    public IdentityKind kind() {
        return IdentityKind.PERSON;
    }
}
