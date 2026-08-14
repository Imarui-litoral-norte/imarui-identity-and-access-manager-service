package br.com.imarui.ima.identity.infra.persistence.mapper;

import br.com.imarui.ima.identity.core.domain.model.affiliation.AffiliationId;
import br.com.imarui.ima.identity.core.domain.model.identity.Email;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityFullName;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
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
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import br.com.imarui.ima.identity.infra.persistence.entity.identityapplication.IdentityApplicationEntity;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.UUID;

@Component
public class IdentityApplicationPersistenceMapper {

    public IdentityApplicationEntity toEntity(
            IdentityApplication application
    ) {
        IdentityApplicationDetails details = application.getDetails();

        String fullName = null;
        String cpf = null;
        LocalDate birthDate = null;
        String phoneNumber = null;
        String legalName = null;
        String tradeName = null;
        String cnpj = null;
        String serviceName = null;
        String serviceDescription = null;

        if (details instanceof PersonIdentityApplicationDetails person) {
            fullName = person.fullName().value();
            cpf = person.cpf().value();
            birthDate = person.birthDate();
            phoneNumber = valueOf(person.phoneNumber());
        } else if (details instanceof LegalEntityIdentityApplicationDetails legalEntity) {
            legalName = legalEntity.legalName().value();
            tradeName = valueOf(legalEntity.tradeName());
            cnpj = legalEntity.cnpj().value();
        } else if (details instanceof ServiceIdentityApplicationDetails service) {
            serviceName = service.name().value();
            serviceDescription = valueOf(service.description());
        }

        return new IdentityApplicationEntity(
                application.getId().value(),
                application.getKind(),
                application.getEmail().value(),
                fullName,
                cpf,
                birthDate,
                phoneNumber,
                legalName,
                tradeName,
                cnpj,
                serviceName,
                serviceDescription,
                application.getStatus(),
                application.getRequestedAt(),
                application.getReviewedAt(),
                valueOf(application.getReviewedBy()),
                application.getRejectionReason(),
                valueOf(application.getResolvedIdentityId()),
                valueOf(application.getAssignedTenantId()),
                valueOf(application.getResultingAffiliationId())
        );
    }

    public void updateEntity(
            IdentityApplication application,
            IdentityApplicationEntity entity
    ) {
        entity.updateFrom(
                application.getStatus(),
                application.getReviewedAt(),
                valueOf(application.getReviewedBy()),
                application.getRejectionReason(),
                valueOf(application.getResolvedIdentityId()),
                valueOf(application.getAssignedTenantId()),
                valueOf(application.getResultingAffiliationId())
        );
    }

    public IdentityApplication toDomain(
            IdentityApplicationEntity entity
    ) {
        return IdentityApplication.reconstitute(
                IdentityApplicationId.from(entity.getId()),
                Email.from(entity.getEmail()),
                detailsFrom(entity),
                entity.getStatus(),
                entity.getRequestedAt(),
                entity.getReviewedAt(),
                identityIdFrom(entity.getReviewedByIdentityId()),
                entity.getRejectionReason(),
                identityIdFrom(entity.getResolvedIdentityId()),
                tenantIdFrom(entity.getAssignedTenantId()),
                affiliationIdFrom(entity.getResultingAffiliationId())
        );
    }

    private IdentityApplicationDetails detailsFrom(
            IdentityApplicationEntity entity
    ) {
        return switch (entity.getKind()) {
            case PERSON -> new PersonIdentityApplicationDetails(
                    IdentityFullName.from(entity.getFullName()),
                    Cpf.from(entity.getCpf()),
                    entity.getBirthDate(),
                    phoneNumberFrom(entity.getPhoneNumber())
            );
            case LEGAL_ENTITY -> new LegalEntityIdentityApplicationDetails(
                    LegalName.from(entity.getLegalName()),
                    tradeNameFrom(entity.getTradeName()),
                    Cnpj.from(entity.getCnpj())
            );
            case SERVICE -> new ServiceIdentityApplicationDetails(
                    ServiceName.from(entity.getServiceName()),
                    serviceDescriptionFrom(entity.getServiceDescription())
            );
        };
    }

    private String valueOf(PhoneNumber value) {
        return value == null ? null : value.value();
    }

    private String valueOf(TradeName value) {
        return value == null ? null : value.value();
    }

    private String valueOf(ServiceDescription value) {
        return value == null ? null : value.value();
    }

    private UUID valueOf(IdentityId identityId) {
        return identityId == null ? null : identityId.value();
    }

    private UUID valueOf(TenantId tenantId) {
        return tenantId == null ? null : tenantId.value();
    }

    private UUID valueOf(AffiliationId affiliationId) {
        return affiliationId == null ? null : affiliationId.value();
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

    private IdentityId identityIdFrom(UUID value) {
        return value == null ? null : IdentityId.from(value);
    }

    private TenantId tenantIdFrom(UUID value) {
        return value == null ? null : TenantId.from(value);
    }

    private AffiliationId affiliationIdFrom(UUID value) {
        return value == null ? null : AffiliationId.from(value);
    }
}
