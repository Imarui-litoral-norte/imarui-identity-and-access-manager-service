package br.com.imarui.ima.authentication.infra.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "authentication_challenges")
public class AuthenticationChallengeEntity {
  protected AuthenticationChallengeEntity() {}

  @Id
  @Column(name = "id")
  private java.util.UUID id;

  @Column(name = "identity_id")
  private java.util.UUID identityId;

  @Column(name = "purpose")
  private String purpose;

  @Column(name = "token_hash")
  private String tokenHash;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "expires_at")
  private java.time.Instant expiresAt;

  @Column(name = "consumed_at")
  private java.time.Instant consumedAt;
}
