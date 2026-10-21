package br.com.imarui.ima.identity.infra.persistence.entity.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.EmailType;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityEmailEmbeddable {

  @Column(name = "primary_email_id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "primary_email", nullable = false, length = 320)
  private String email;

  @Enumerated(EnumType.STRING)
  @Column(name = "primary_email_type", nullable = false, length = 20)
  private EmailType type;

  @Column(name = "primary_email_verified", nullable = false)
  private boolean verified;

  @Column(name = "primary_email_verified_at")
  private Instant verifiedAt;

  @Column(name = "primary_email_created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public IdentityEmailEmbeddable(
      UUID id,
      String email,
      EmailType type,
      boolean verified,
      Instant verifiedAt,
      Instant createdAt) {
    this.id = id;
    this.email = email;
    this.type = type;
    this.verified = verified;
    this.verifiedAt = verifiedAt;
    this.createdAt = createdAt;
  }
}
