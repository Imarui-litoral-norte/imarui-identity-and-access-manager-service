package br.com.imarui.ima.identity.infra.persistence.entity.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(name = "ima_service_identities")
@DiscriminatorValue("SERVICE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ServiceIdentityEntity extends IdentityEntity {

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", length = 500)
    private String description;

    public ServiceIdentityEntity(
            UUID id,
            IdentityEmailEmbeddable primaryEmail,
            String name,
            String description,
            IdentityStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt,
            Instant disabledAt
    ) {
        super(
                id,
                primaryEmail,
                status,
                createdAt,
                updatedAt,
                activatedAt,
                disabledAt
        );
        this.name = name;
        this.description = description;
    }

    public void synchronize(
            IdentityEmailEmbeddable primaryEmail,
            String name,
            String description,
            IdentityStatus status,
            Instant updatedAt,
            Instant activatedAt,
            Instant disabledAt
    ) {
        synchronizeBaseState(
                primaryEmail,
                status,
                updatedAt,
                activatedAt,
                disabledAt
        );
        this.name = name;
        this.description = description;
    }
}
