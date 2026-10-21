package br.com.imarui.ima.identity.api.factory;

import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateLegalEntityIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreatePersonIdentityApplicationRequest;
import br.com.imarui.ima.identity.api.dto.identityapplication.request.create.CreateServiceIdentityApplicationRequest;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateLegalEntityIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreatePersonIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateServiceIdentityApplicationCommand;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
public final class IdentityApplicationCommandFactory {

    public CreateIdentityApplicationCommand from(
            CreateIdentityApplicationRequest request
    ) {
        Objects.requireNonNull(
                request,
                "request cannot be null"
        );

        if (request instanceof CreatePersonIdentityApplicationRequest person) {
            return new CreatePersonIdentityApplicationCommand(
                    person.fullName(),
                    person.cpf(),
                    person.email(),
                    person.birthDate(),
                    person.phoneNumber(), person.nickname()
            );
        }

        if (request instanceof CreateLegalEntityIdentityApplicationRequest legalEntity) {
            return new CreateLegalEntityIdentityApplicationCommand(
                    legalEntity.legalName(),
                    legalEntity.tradeName(),
                    legalEntity.cnpj(),
                    legalEntity.email(), legalEntity.nickname()
            );
        }

        if (request instanceof CreateServiceIdentityApplicationRequest service) {
            return new CreateServiceIdentityApplicationCommand(
                    service.name(),
                    service.description(),
                    service.email(), service.nickname()
            );
        }

        throw new IllegalArgumentException(
                "Unsupported identity application request: "
                        + request.getClass().getName()
        );
    }
}
