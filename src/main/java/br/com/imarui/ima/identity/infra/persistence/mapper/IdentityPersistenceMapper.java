package br.com.imarui.ima.identity.infra.persistence.mapper;

import br.com.imarui.ima.identity.core.domain.model.identity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity.*;
import br.com.imarui.ima.identity.core.domain.model.identity.person.Cpf;
import br.com.imarui.ima.identity.core.domain.model.identity.person.PersonIdentity;
import br.com.imarui.ima.identity.core.domain.model.identity.service.*;
import br.com.imarui.ima.identity.infra.persistence.entity.identity.*;
import org.springframework.stereotype.Component;

@Component
public class IdentityPersistenceMapper {
 public IdentityEntity toEntity(Identity value) { var entity = baseEntity(value); entity.assignNickname(value.getNickname()); return entity; }
 public Identity toDomain(IdentityEntity entity) { var value = baseDomain(entity); if (entity.getNickname()!=null) value.assignNickname(entity.getNickname()); return value; }


    private IdentityEntity baseEntity(Identity identity) {
        if (identity instanceof PersonIdentity person) {
            return toPersonEntity(person);
        }

        if (identity instanceof LegalEntityIdentity legalEntity) {
            return toLegalEntity(legalEntity);
        }

        if (identity instanceof ServiceIdentity service) {
            return toServiceEntity(service);
        }

        throw unsupported(identity.getClass());
    }

    public void updateEntity(
            Identity identity,
            IdentityEntity entity
    ) {
        entity.assignNickname(identity.getNickname());
        if (
                identity instanceof PersonIdentity person
                        && entity instanceof PersonIdentityEntity target
        ) {
            target.synchronize(
                    toEmail(person.getPrimaryEmail()),
                    person.getFullName().value(),
                    person.getBirthDate(),
                    toPhone(person.getPhoneNumber()),
                    toProfilePhoto(person.getProfilePhoto()),
                    person.getStatus(),
                    person.getUpdatedAt(),
                    person.getActivatedAt(),
                    person.getDisabledAt()
            );
            return;
        }

        if (
                identity instanceof LegalEntityIdentity legalEntity
                        && entity instanceof LegalEntityIdentityEntity target
        ) {
            target.synchronize(
                    toEmail(legalEntity.getPrimaryEmail()),
                    legalEntity.getLegalName().value(),
                    legalEntity.getTradeName() == null
                            ? null
                            : legalEntity.getTradeName().value(),
                    legalEntity.getStatus(),
                    legalEntity.getUpdatedAt(),
                    legalEntity.getActivatedAt(),
                    legalEntity.getDisabledAt()
            );
            return;
        }

        if (
                identity instanceof ServiceIdentity service
                        && entity instanceof ServiceIdentityEntity target
        ) {
            target.synchronize(
                    toEmail(service.getPrimaryEmail()),
                    service.getName().value(),
                    service.getDescription() == null
                            ? null
                            : service.getDescription().value(),
                    service.getStatus(),
                    service.getUpdatedAt(),
                    service.getActivatedAt(),
                    service.getDisabledAt()
            );
            return;
        }

        throw new IllegalArgumentException(
                "Identity domain and persistence types do not match."
        );
    }

    private Identity baseDomain(IdentityEntity entity) {
        if (entity instanceof PersonIdentityEntity person) {
            return toPersonDomain(person);
        }

        if (entity instanceof LegalEntityIdentityEntity legalEntity) {
            return toLegalEntityDomain(legalEntity);
        }

        if (entity instanceof ServiceIdentityEntity service) {
            return toServiceDomain(service);
        }

        throw unsupported(entity.getClass());
    }

    private PersonIdentityEntity toPersonEntity(
            PersonIdentity person
    ) {
        return new PersonIdentityEntity(
                person.getId().value(),
                toEmail(person.getPrimaryEmail()),
                person.getFullName().value(),
                person.getCpf().value(),
                person.getBirthDate(),
                toPhone(person.getPhoneNumber()),
                toProfilePhoto(person.getProfilePhoto()),
                person.getStatus(),
                person.getCreatedAt(),
                person.getUpdatedAt(),
                person.getActivatedAt(),
                person.getDisabledAt()
        );
    }

    private LegalEntityIdentityEntity toLegalEntity(
            LegalEntityIdentity legalEntity
    ) {
        return new LegalEntityIdentityEntity(
                legalEntity.getId().value(),
                toEmail(legalEntity.getPrimaryEmail()),
                legalEntity.getLegalName().value(),
                legalEntity.getTradeName() == null
                        ? null
                        : legalEntity.getTradeName().value(),
                legalEntity.getCnpj().value(),
                legalEntity.getStatus(),
                legalEntity.getCreatedAt(),
                legalEntity.getUpdatedAt(),
                legalEntity.getActivatedAt(),
                legalEntity.getDisabledAt()
        );
    }

    private ServiceIdentityEntity toServiceEntity(
            ServiceIdentity service
    ) {
        return new ServiceIdentityEntity(
                service.getId().value(),
                toEmail(service.getPrimaryEmail()),
                service.getName().value(),
                service.getDescription() == null
                        ? null
                        : service.getDescription().value(),
                service.getStatus(),
                service.getCreatedAt(),
                service.getUpdatedAt(),
                service.getActivatedAt(),
                service.getDisabledAt()
        );
    }

    private PersonIdentity toPersonDomain(
            PersonIdentityEntity entity
    ) {
        return PersonIdentity.reconstitute(
                IdentityId.from(entity.getId()),
                toEmailDomain(entity.getPrimaryEmail()),
                IdentityFullName.from(entity.getFullName()),
                Cpf.from(entity.getCpf()),
                entity.getBirthDate(),
                toPhoneDomain(entity.getPhoneNumber()),
                toProfilePhotoDomain(entity.getProfilePhoto()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getActivatedAt(),
                entity.getDisabledAt()
        );
    }

    private LegalEntityIdentity toLegalEntityDomain(
            LegalEntityIdentityEntity entity
    ) {
        return LegalEntityIdentity.reconstitute(
                IdentityId.from(entity.getId()),
                toEmailDomain(entity.getPrimaryEmail()),
                LegalName.from(entity.getLegalName()),
                entity.getTradeName() == null
                        ? null
                        : TradeName.from(entity.getTradeName()),
                Cnpj.from(entity.getCnpj()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getActivatedAt(),
                entity.getDisabledAt()
        );
    }

    private ServiceIdentity toServiceDomain(
            ServiceIdentityEntity entity
    ) {
        return ServiceIdentity.reconstitute(
                IdentityId.from(entity.getId()),
                toEmailDomain(entity.getPrimaryEmail()),
                ServiceName.from(entity.getName()),
                entity.getDescription() == null
                        ? null
                        : ServiceDescription.from(entity.getDescription()),
                entity.getStatus(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getActivatedAt(),
                entity.getDisabledAt()
        );
    }

    private IdentityEmailEmbeddable toEmail(
            IdentityEmail email
    ) {
        if(email==null)return null;
        return new IdentityEmailEmbeddable(
                email.id().value(),
                email.email().value(),
                email.type(),
                email.isVerified(),
                email.verifiedAt(),
                email.createdAt()
        );
    }

    private IdentityEmail toEmailDomain(
            IdentityEmailEmbeddable email
    ) {
        if(email==null||email.getId()==null)return null;
        return IdentityEmail.reconstitute(
                IdentityEmailId.from(email.getId()),
                Email.from(email.getEmail()),
                email.getType(),
                email.isVerified(),
                email.getVerifiedAt(),
                email.getCreatedAt()
        );
    }

    private IdentityPhoneNumberEmbeddable toPhone(
            IdentityPhoneNumber phone
    ) {
        if (phone == null) {
            return null;
        }

        return new IdentityPhoneNumberEmbeddable(
                phone.phoneNumber().value(),
                phone.isVerified(),
                phone.verifiedAt(),
                phone.createdAt(),
                phone.updatedAt()
        );
    }

    private IdentityPhoneNumber toPhoneDomain(
            IdentityPhoneNumberEmbeddable phone
    ) {
        if (phone == null || phone.getPhoneNumber() == null) {
            return null;
        }

        return IdentityPhoneNumber.reconstitute(
                PhoneNumber.from(phone.getPhoneNumber()),
                Boolean.TRUE.equals(phone.getVerified()),
                phone.getVerifiedAt(),
                phone.getCreatedAt(),
                phone.getUpdatedAt()
        );
    }

    private IdentityProfilePhotoEmbeddable toProfilePhoto(
            IdentityProfilePhoto photo
    ) {
        if (photo == null) {
            return null;
        }

        return new IdentityProfilePhotoEmbeddable(
                photo.id().value(),
                photo.storageKey(),
                photo.contentType(),
                photo.sizeInBytes(),
                photo.createdAt()
        );
    }

    private IdentityProfilePhoto toProfilePhotoDomain(
            IdentityProfilePhotoEmbeddable photo
    ) {
        if (photo == null || photo.getId() == null) {
            return null;
        }

        return IdentityProfilePhoto.reconstitute(
                ProfilePhotoId.from(photo.getId()),
                photo.getStorageKey(),
                photo.getContentType(),
                photo.getSizeInBytes(),
                photo.getCreatedAt()
        );
    }

    private IllegalArgumentException unsupported(Class<?> type) {
        return new IllegalArgumentException(
                "Unsupported identity persistence type: " + type.getName()
        );
    }
}
