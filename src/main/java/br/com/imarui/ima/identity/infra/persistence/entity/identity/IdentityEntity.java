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
@Table(
        name = "ima_identities",
        indexes = {
                @Index(name = "idx_identities_status", columnList = "status"),
                @Index(name = "idx_identities_primary_email", columnList = "primary_email")
        }
)
@Inheritance(strategy = InheritanceType.JOINED)
@DiscriminatorColumn(
        name = "kind",
        discriminatorType = DiscriminatorType.STRING,
        length = 30
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public abstract class IdentityEntity {
    @Column(name="nickname", length=64)
    private String nickname;
    public void assignNickname(String value) { this.nickname = value; }

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Embedded
    private IdentityEmailEmbeddable primaryEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private IdentityStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "disabled_at")
    private Instant disabledAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    protected IdentityEntity(
            UUID id,
            IdentityEmailEmbeddable primaryEmail,
            IdentityStatus status,
            Instant createdAt,
            Instant updatedAt,
            Instant activatedAt,
            Instant disabledAt
    ) {
        this.id = id;
        this.primaryEmail = primaryEmail;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.activatedAt = activatedAt;
        this.disabledAt = disabledAt;
    }

    protected final void synchronizeBaseState(
            IdentityEmailEmbeddable primaryEmail,
            IdentityStatus status,
            Instant updatedAt,
            Instant activatedAt,
            Instant disabledAt
    ) {
        this.primaryEmail = primaryEmail;
        this.status = status;
        this.updatedAt = updatedAt;
        this.activatedAt = activatedAt;
        this.disabledAt = disabledAt;
    }
}
