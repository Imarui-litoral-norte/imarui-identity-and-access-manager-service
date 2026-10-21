package br.com.imarui.ima.identity.infra.persistence.entity.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.EmailType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityEmailEmbeddable {

    @Column(name = "primary_email_id", updatable = false)
    private UUID id;

    @Column(name = "primary_email", length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "primary_email_type", length = 20)
    private EmailType type;

    @Column(name = "primary_email_verified")
    private boolean verified;

    @Column(name = "primary_email_verified_at")
    private Instant verifiedAt;

    @Column(name = "primary_email_created_at", updatable = false)
    private Instant createdAt;

    public IdentityEmailEmbeddable(
            UUID id,
            String email,
            EmailType type,
            boolean verified,
            Instant verifiedAt,
            Instant createdAt
    ) {
        this.id = id;
        this.email = email;
        this.type = type;
        this.verified = verified;
        this.verifiedAt = verifiedAt;
        this.createdAt = createdAt;
    }
}
