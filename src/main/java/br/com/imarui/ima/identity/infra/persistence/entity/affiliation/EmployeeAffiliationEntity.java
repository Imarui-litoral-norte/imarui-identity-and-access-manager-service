package br.com.imarui.ima.identity.infra.persistence.entity.affiliation;

import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "ima_employee_affiliations",
        indexes = {
                @Index(name = "idx_employee_affiliations_registration", columnList = "registration")
        }
)
@DiscriminatorValue("EMPLOYEE")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EmployeeAffiliationEntity extends AffiliationEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID uniquenessTenantId;

    @Column(name = "registration", nullable = false, updatable = false)
    private String registration;

    public EmployeeAffiliationEntity(
            UUID id,
            UUID tenantId,
            UUID identityId,
            String registration,
            AffiliationStatus status,
            Instant startedAt,
            Instant updatedAt,
            Instant endedAt
    ) {
        super(id, tenantId, identityId, status, startedAt, updatedAt, endedAt);
        this.uniquenessTenantId = tenantId;
        this.registration = registration;
    }

    public void synchronize(
            AffiliationStatus status,
            Instant updatedAt,
            Instant endedAt
    ) {
        synchronizeBaseState(status, updatedAt, endedAt);
    }
}
