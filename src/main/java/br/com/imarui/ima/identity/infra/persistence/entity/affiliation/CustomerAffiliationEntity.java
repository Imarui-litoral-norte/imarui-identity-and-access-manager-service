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
        name = "ima_customer_affiliations",
        indexes = {
                @Index(name = "idx_customer_affiliations_code", columnList = "customer_code")
        }
)
@DiscriminatorValue("CUSTOMER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CustomerAffiliationEntity extends AffiliationEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID uniquenessTenantId;

    @Column(name = "customer_code", nullable = false, updatable = false)
    private String customerCode;

    public CustomerAffiliationEntity(
            UUID id,
            UUID tenantId,
            UUID identityId,
            String customerCode,
            AffiliationStatus status,
            Instant startedAt,
            Instant updatedAt,
            Instant endedAt
    ) {
        super(id, tenantId, identityId, status, startedAt, updatedAt, endedAt);
        this.uniquenessTenantId = tenantId;
        this.customerCode = customerCode;
    }

    public void synchronize(
            AffiliationStatus status,
            Instant updatedAt,
            Instant endedAt
    ) {
        synchronizeBaseState(status, updatedAt, endedAt);
    }
}
