package br.com.imarui.ima.authentication.infra.persistence.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "authentication_credentials")
public class CredentialEntity {
  protected CredentialEntity() {}

  @Id
  @Column(name = "identity_id")
  private java.util.UUID identityId;

  @Column(name = "password_hash")
  private String passwordHash;

  @Column(name = "failures")
  private int failures;

  @Column(name = "locked_until")
  private java.time.Instant lockedUntil;

  @Column(name = "enabled")
  private boolean enabled;

  @Column(name = "created_at")
  private java.time.Instant createdAt;

  @Column(name = "updated_at")
  private java.time.Instant updatedAt;
}
