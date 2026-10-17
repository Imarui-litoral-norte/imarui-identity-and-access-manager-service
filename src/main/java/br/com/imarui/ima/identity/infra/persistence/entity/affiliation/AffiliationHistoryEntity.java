package br.com.imarui.ima.identity.infra.persistence.entity.affiliation;

import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationStatus;
import br.com.imarui.ima.identity.core.domain.enums.affiliation.AffiliationType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;

/** Independent audit snapshot: original identifiers deliberately have no operational FK or unique key. */
@Entity
@Immutable
@Table(name = "ima_affiliations__history", indexes = {
    @Index(name = "idx_affiliation_history_original", columnList = "original_affiliation_id, deleted_at"),
    @Index(name = "idx_affiliation_history_identity", columnList = "identity_id, deleted_at"),
    @Index(name = "idx_affiliation_history_tenant", columnList = "tenant_id, deleted_at"),
    @Index(name = "idx_affiliation_history_operation", columnList = "identity_history_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AffiliationHistoryEntity {
    @Id private UUID id;
    @Column(name = "identity_history_id", nullable = false, updatable = false) private UUID identityHistoryId;
    @Column(name = "original_affiliation_id", nullable = false, updatable = false) private UUID originalAffiliationId;
    @Column(name = "identity_id", nullable = false, updatable = false) private UUID identityId;
    @Column(name = "tenant_id", nullable = false, updatable = false) private UUID tenantId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30, updatable = false) private AffiliationType type;
    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", nullable = false, length = 20, updatable = false) private AffiliationStatus previousStatus;
    @Column(updatable = false) private String registration;
    @Column(name = "customer_code", updatable = false) private String customerCode;
    @Column(name = "supplier_code", updatable = false) private String supplierCode;
    @Column(name = "started_at", nullable = false, updatable = false) private Instant startedAt;
    @Column(name = "updated_at", nullable = false, updatable = false) private Instant updatedAt;
    @Column(name = "ended_at", updatable = false) private Instant endedAt;
    @Column(name = "original_version", nullable = false, updatable = false) private long originalVersion;
    @Column(name = "deleted_at", nullable = false, updatable = false) private Instant deletedAt;
    @Column(name = "actor_id", nullable = false, updatable = false) private UUID actorId;
    @Column(length = 500, updatable = false) private String reason;

    public AffiliationHistoryEntity(AffiliationEntity source, AffiliationType type,
            String registration, String customerCode, String supplierCode,
            UUID identityHistoryId, UUID actorId, String reason, Instant deletedAt) {
        this.id = UUID.randomUUID();
        this.identityHistoryId = identityHistoryId;
        this.originalAffiliationId = source.getId();
        this.identityId = source.getIdentityId();
        this.tenantId = source.getTenantId();
        this.type = type;
        this.previousStatus = source.getStatus();
        this.registration = registration;
        this.customerCode = customerCode;
        this.supplierCode = supplierCode;
        this.startedAt = source.getStartedAt();
        this.updatedAt = source.getUpdatedAt();
        this.endedAt = source.getEndedAt();
        this.originalVersion = source.getVersion();
        this.deletedAt = deletedAt;
        this.actorId = actorId;
        this.reason = reason;
    }
}
