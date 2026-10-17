package br.com.imarui.ima.identity.infra.persistence.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AccessLevel;

/** Transactional outbox consumed by Authentication; reactivation never removes requests. */
@Entity
@Table(name = "ima_access_revocation_requests")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessRevocationRequestEntity {
    @Id private UUID id;
    @Column(nullable = false, length = 20) private String scope;
    @Column(name = "subject_id", nullable = false) private UUID subjectId;
    @Column(name = "requested_at", nullable = false) private Instant requestedAt;
    @Column(name = "processed_at") private Instant processedAt;

    public AccessRevocationRequestEntity(String scope, UUID subjectId, Instant requestedAt) {
        this.id = UUID.randomUUID();
        this.scope = scope;
        this.subjectId = subjectId;
        this.requestedAt = requestedAt;
    }
}
