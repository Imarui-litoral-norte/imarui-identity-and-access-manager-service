package br.com.imarui.ima.identity.infra.persistence.entity.identity;

import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@org.hibernate.annotations.Immutable
@Table(name = "ima_identities__history")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityHistoryEntity {
    @Id private UUID id;
    @Column(name = "original_identity_id", nullable = false) private UUID originalIdentityId;
    @Column(nullable = false, length = 30) private String kind;
    @Column(name = "previous_status", nullable = false, length = 20) private String previousStatus;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "activated_at") private Instant activatedAt;
    @Column(name = "disabled_at") private Instant disabledAt;
    @Column(name = "deleted_at", nullable = false) private Instant deletedAt;
    @Column(name = "actor_id", nullable = false) private UUID actorId;
    @Column(length = 500) private String reason;
    @Column(nullable = false, columnDefinition = "text") private String snapshot;

    public IdentityHistoryEntity(Identity identity, UUID actor, String reason, Instant at, String snapshot) {
        id = UUID.randomUUID(); originalIdentityId = identity.getId().value();
        kind = identity.getKind().name(); previousStatus = identity.getStatus().name();
        createdAt = identity.getCreatedAt(); updatedAt = identity.getUpdatedAt();
        activatedAt = identity.getActivatedAt(); disabledAt = identity.getDisabledAt();
        deletedAt = at; actorId = actor; this.reason = reason; this.snapshot = snapshot;
    }
}
