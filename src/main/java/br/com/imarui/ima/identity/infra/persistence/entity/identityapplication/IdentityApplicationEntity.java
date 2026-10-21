package br.com.imarui.ima.identity.infra.persistence.entity.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import jakarta.persistence.Access;
import jakarta.persistence.AccessType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "ima_identity_applications",
    indexes = {
      @Index(
          name = "idx_identity_applications_status_requested_at",
          columnList = "status, requested_at"),
      @Index(name = "idx_identity_applications_cpf", columnList = "cpf")
    })
@Access(AccessType.FIELD)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityApplicationEntity {
  @Column(name = "nickname", nullable = false, length = 64)
  private String nickname;

  public void assignNickname(String value) {
    this.nickname = value;
  }

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Enumerated(EnumType.STRING)
  @Column(name = "identity_kind", nullable = false, length = 30)
  private IdentityKind kind;

  @Column(name = "email", nullable = false, length = 254)
  private String email;

  @Column(name = "full_name", length = 150)
  private String fullName;

  @Column(name = "cpf", length = 11)
  private String cpf;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "phone_number", length = 15)
  private String phoneNumber;

  @Column(name = "legal_name", length = 150)
  private String legalName;

  @Column(name = "trade_name", length = 150)
  private String tradeName;

  @Column(name = "cnpj", length = 14)
  private String cnpj;

  @Column(name = "service_name", length = 100)
  private String serviceName;

  @Column(name = "service_description", length = 500)
  private String serviceDescription;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private IdentityApplicationStatus status;

  @Column(name = "requested_at", nullable = false, updatable = false)
  private Instant requestedAt;

  @Column(name = "reviewed_at")
  private Instant reviewedAt;

  @Column(name = "reviewed_by_identity_id")
  private UUID reviewedByIdentityId;

  @Column(name = "rejection_reason", length = 500)
  private String rejectionReason;

  @Column(name = "resolved_identity_id")
  private UUID resolvedIdentityId;

  @Column(name = "assigned_tenant_id")
  private UUID assignedTenantId;

  @Column(name = "resulting_affiliation_id")
  private UUID resultingAffiliationId;

  @Version
  @Column(name = "version", nullable = false)
  private Long version;

  public IdentityApplicationEntity(
      UUID id,
      IdentityKind kind,
      String email,
      String fullName,
      String cpf,
      LocalDate birthDate,
      String phoneNumber,
      String legalName,
      String tradeName,
      String cnpj,
      String serviceName,
      String serviceDescription,
      IdentityApplicationStatus status,
      Instant requestedAt,
      Instant reviewedAt,
      UUID reviewedByIdentityId,
      String rejectionReason,
      UUID resolvedIdentityId,
      UUID assignedTenantId,
      UUID resultingAffiliationId) {
    this.id = id;
    this.kind = kind;
    this.email = email;
    this.fullName = fullName;
    this.cpf = cpf;
    this.birthDate = birthDate;
    this.phoneNumber = phoneNumber;
    this.legalName = legalName;
    this.tradeName = tradeName;
    this.cnpj = cnpj;
    this.serviceName = serviceName;
    this.serviceDescription = serviceDescription;
    this.status = status;
    this.requestedAt = requestedAt;
    this.reviewedAt = reviewedAt;
    this.reviewedByIdentityId = reviewedByIdentityId;
    this.rejectionReason = rejectionReason;
    this.resolvedIdentityId = resolvedIdentityId;
    this.assignedTenantId = assignedTenantId;
    this.resultingAffiliationId = resultingAffiliationId;
  }

  public void updateFrom(
      IdentityApplicationStatus status,
      Instant reviewedAt,
      UUID reviewedByIdentityId,
      String rejectionReason,
      UUID resolvedIdentityId,
      UUID assignedTenantId,
      UUID resultingAffiliationId) {
    this.status = status;
    this.reviewedAt = reviewedAt;
    this.reviewedByIdentityId = reviewedByIdentityId;
    this.rejectionReason = rejectionReason;
    this.resolvedIdentityId = resolvedIdentityId;
    this.assignedTenantId = assignedTenantId;
    this.resultingAffiliationId = resultingAffiliationId;
  }
}
