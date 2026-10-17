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
        name = "ima_supplier_affiliations",
        indexes = {
                @Index(name = "idx_supplier_affiliations_code", columnList = "supplier_code")
        }
)
@DiscriminatorValue("SUPPLIER")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SupplierAffiliationJpaEntity extends AffiliationEntity {
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID uniquenessTenantId;

    @Column(name = "supplier_code", nullable = false, updatable = false)
    private String supplierCode;

    public SupplierAffiliationJpaEntity(
            UUID id,
            UUID tenantId,
            UUID identityId,
            String supplierCode,
            AffiliationStatus status,
            Instant startedAt,
            Instant updatedAt,
            Instant endedAt
    ) {
        super(id, tenantId, identityId, status, startedAt, updatedAt, endedAt);
        this.uniquenessTenantId = tenantId;
        this.supplierCode = supplierCode;
    }

    public void synchronize(
            AffiliationStatus status,
            Instant updatedAt,
            Instant endedAt
    ) {
        synchronizeBaseState(status, updatedAt, endedAt);
    }
}
