package br.com.imarui.ima.identity.core.application.usecase.identityapplication;

import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateLegalEntityIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreatePersonIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.command.identityapplication.CreateServiceIdentityApplicationCommand;
import br.com.imarui.ima.identity.core.application.result.identityapplication.IdentityApplicationResult;
import br.com.imarui.ima.identity.core.domain.model.identity.Email;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityFullName;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.Cnpj;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.LegalName;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.TradeName;
import br.com.imarui.ima.identity.core.domain.model.identity.PhoneNumber;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceDescription;
import br.com.imarui.ima.identity.core.domain.model.identity.service.ServiceName;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplication;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.IdentityApplicationId;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.LegalEntityIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.PersonIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.domain.model.identityapplication.ServiceIdentityApplicationDetails;
import br.com.imarui.ima.identity.core.repository.IdentityApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class CreateIdentityApplicationUseCase {

    private final Clock clock;
    private final br.com.imarui.ima.identity.core.application.port.IdentityRegistrationLock registrationLock;
    private final br.com.imarui.ima.identity.core.application.service.IdentityApplicationConflicts conflicts;
    private final IdentityApplicationRepository identityApplicationRepository;

    @Transactional
    public IdentityApplicationResult execute(
            CreateIdentityApplicationCommand command
    ) {
        Objects.requireNonNull(command, "command cannot be null");

        Instant now = Instant.now(clock);
        IdentityApplication application = IdentityApplication.create(
                IdentityApplicationId.generate(),
                Email.from(command.email()),
                detailsFrom(command),
                now
        );

        registrationLock.acquire(application);
        conflicts.check(application, true);
        return IdentityApplicationResult.from(
                identityApplicationRepository.save(application)
        );
    }

    private IdentityApplicationDetails detailsFrom(
            CreateIdentityApplicationCommand command
    ) {
        if (command instanceof CreatePersonIdentityApplicationCommand person) {
            return new PersonIdentityApplicationDetails(
                    IdentityFullName.from(person.fullName()),
                    Cpf.from(person.cpf()),
                    person.birthDate(),
                    phoneNumberFrom(person.phoneNumber())
            );
        }

        if (command instanceof CreateLegalEntityIdentityApplicationCommand legalEntity) {
            return new LegalEntityIdentityApplicationDetails(
                    LegalName.from(legalEntity.legalName()),
                    tradeNameFrom(legalEntity.tradeName()),
                    Cnpj.from(legalEntity.cnpj())
            );
        }

        if (command instanceof CreateServiceIdentityApplicationCommand service) {
            return new ServiceIdentityApplicationDetails(
                    ServiceName.from(service.name()),
                    serviceDescriptionFrom(service.description())
            );
        }

        throw new IllegalArgumentException(
                "Unsupported identity application command: "
                        + command.getClass().getName()
        );
    }

    private PhoneNumber phoneNumberFrom(String value) {
        return value == null ? null : PhoneNumber.from(value);
    }

    private TradeName tradeNameFrom(String value) {
        return value == null ? null : TradeName.from(value);
    }

    private ServiceDescription serviceDescriptionFrom(String value) {
        return value == null ? null : ServiceDescription.from(value);
    }
}
