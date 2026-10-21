package br.com.imarui.ima.identity.core.application.command.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;

public sealed interface CreateIdentityApplicationCommand
        permits CreatePersonIdentityApplicationCommand,
                CreateLegalEntityIdentityApplicationCommand,
                CreateServiceIdentityApplicationCommand {

    IdentityKind kind();

    String email();
    String nickname();
}
