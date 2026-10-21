package br.com.imarui.ima.identity.core.domain.model.identityapplication;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identityapplication.IdentityApplicationStatus;
import br.com.imarui.ima.identity.core.domain.exception.identityapplication.IdentityApplicationReviewNotAllowedException;
import br.com.imarui.ima.identity.core.domain.exception.identityapplication.InvalidIdentityApplicationStateException;
import br.com.imarui.ima.identity.core.domain.model.affiliation.AffiliationId;
import br.com.imarui.ima.identity.core.domain.model.identity.Email;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import br.com.imarui.ima.identity.core.domain.model.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class IdentityApplication {

  private static final int MAX_REJECTION_REASON_LENGTH = 500;

  private final IdentityApplicationId id;
  private String nickname;

  public void assignNickname(String value) {
    nickname = br.com.imarui.ima.identity.core.domain.model.identity.Nickname.from(value).value();
  }

  private final Email email;
  private final IdentityApplicationDetails details;
  private IdentityApplicationStatus status;
  private final Instant requestedAt;
  private Instant reviewedAt;
  private IdentityId reviewedBy;
  private String rejectionReason;
  private IdentityId resolvedIdentityId;
  private TenantId assignedTenantId;
  private AffiliationId resultingAffiliationId;

  private IdentityApplication(
      IdentityApplicationId id,
      Email email,
      IdentityApplicationDetails details,
      IdentityApplicationStatus status,
      Instant requestedAt,
      Instant reviewedAt,
      IdentityId reviewedBy,
      String rejectionReason,
      IdentityId resolvedIdentityId,
      TenantId assignedTenantId,
      AffiliationId resultingAffiliationId,
      String nickname) {
    this.id = Objects.requireNonNull(id, "id cannot be null");

    this.nickname =
        br.com.imarui.ima.identity.core.domain.model.identity.Nickname.from(nickname).value();
    this.email = email;
    if (email == null) throw new IllegalArgumentException("Email is required.");

    this.details = Objects.requireNonNull(details, "details cannot be null");

    this.status = Objects.requireNonNull(status, "status cannot be null");

    this.requestedAt = Objects.requireNonNull(requestedAt, "requestedAt cannot be null");

    this.reviewedAt = reviewedAt;
    this.reviewedBy = reviewedBy;
    this.rejectionReason = normalizeRejectionReason(rejectionReason);

    this.resolvedIdentityId = resolvedIdentityId;
    this.assignedTenantId = assignedTenantId;
    this.resultingAffiliationId = resultingAffiliationId;

    validateState();
  }

  public static IdentityApplication create(
      @NotNull IdentityApplicationId id,
      @NotNull Email email,
      @NotNull IdentityApplicationDetails details,
      @NotNull Instant now,
      String nickname) {
    Objects.requireNonNull(now, "now cannot be null");

    return new IdentityApplication(
        id,
        email,
        details,
        IdentityApplicationStatus.PENDING,
        now,
        null,
        null,
        null,
        null,
        null,
        null,
        nickname);
  }

  public static IdentityApplication reconstitute(
      @NotNull IdentityApplicationId id,
      @NotNull Email email,
      @NotNull IdentityApplicationDetails details,
      @NotNull IdentityApplicationStatus status,
      @NotNull Instant requestedAt,
      @Nullable Instant reviewedAt,
      @Nullable IdentityId reviewedBy,
      @Nullable String rejectionReason,
      @Nullable IdentityId resolvedIdentityId,
      @Nullable TenantId assignedTenantId,
      @Nullable AffiliationId resultingAffiliationId,
      String nickname) {
    return new IdentityApplication(
        id,
        email,
        details,
        status,
        requestedAt,
        reviewedAt,
        reviewedBy,
        rejectionReason,
        resolvedIdentityId,
        assignedTenantId,
        resultingAffiliationId,
        nickname);
  }

  public IdentityKind getKind() {
    return details.kind();
  }

  public void approve(
      @NotNull IdentityId reviewedBy,
      @NotNull IdentityId resolvedIdentityId,
      @NotNull TenantId assignedTenantId,
      @NotNull AffiliationId resultingAffiliationId,
      @NotNull Instant now) {
    Objects.requireNonNull(reviewedBy, "reviewedBy cannot be null");

    Objects.requireNonNull(resolvedIdentityId, "resolvedIdentityId cannot be null");

    Objects.requireNonNull(assignedTenantId, "assignedTenantId cannot be null");

    Objects.requireNonNull(resultingAffiliationId, "resultingAffiliationId cannot be null");

    Objects.requireNonNull(now, "now cannot be null");

    validateEventTime(now);
    validateCanBeReviewed();

    status = IdentityApplicationStatus.APPROVED;
    reviewedAt = now;
    this.reviewedBy = reviewedBy;

    rejectionReason = null;

    this.resolvedIdentityId = resolvedIdentityId;
    this.assignedTenantId = assignedTenantId;
    this.resultingAffiliationId = resultingAffiliationId;

    validateState();
  }

  public void reject(
      @NotNull IdentityId reviewedBy, @NotNull String rejectionReason, @NotNull Instant now) {
    Objects.requireNonNull(reviewedBy, "reviewedBy cannot be null");

    Objects.requireNonNull(rejectionReason, "rejectionReason cannot be null");

    Objects.requireNonNull(now, "now cannot be null");

    String normalizedRejectionReason = normalizeRejectionReason(rejectionReason);

    validateEventTime(now);
    validateCanBeReviewed();

    status = IdentityApplicationStatus.REJECTED;
    reviewedAt = now;
    this.reviewedBy = reviewedBy;
    this.rejectionReason = normalizedRejectionReason;

    resolvedIdentityId = null;
    assignedTenantId = null;
    resultingAffiliationId = null;

    validateState();
  }

  public boolean isPending() {
    return status == IdentityApplicationStatus.PENDING;
  }

  public boolean isApproved() {
    return status == IdentityApplicationStatus.APPROVED;
  }

  public boolean isRejected() {
    return status == IdentityApplicationStatus.REJECTED;
  }

  private void validateCanBeReviewed() {
    if (!isPending()) {
      throw new IdentityApplicationReviewNotAllowedException(id, status);
    }
  }

  private void validateEventTime(Instant now) {
    if (now.isBefore(requestedAt)) {
      throw new IllegalArgumentException("Review time cannot be before requestedAt.");
    }
  }

  private void validateState() {
    if (reviewedAt != null && reviewedAt.isBefore(requestedAt)) {
      throw new InvalidIdentityApplicationStateException(
          "reviewedAt cannot be before requestedAt.");
    }

    switch (status) {
      case PENDING -> validatePendingState();
      case APPROVED -> validateApprovedState();
      case REJECTED -> validateRejectedState();
    }
  }

  private void validatePendingState() {
    requireState(reviewedAt == null, "A pending application cannot have reviewedAt.");

    requireState(reviewedBy == null, "A pending application cannot have reviewedBy.");

    requireState(rejectionReason == null, "A pending application cannot have rejectionReason.");

    requireState(
        resolvedIdentityId == null, "A pending application cannot have resolvedIdentityId.");

    requireState(assignedTenantId == null, "A pending application cannot have assignedTenantId.");

    requireState(
        resultingAffiliationId == null,
        "A pending application cannot have resultingAffiliationId.");
  }

  private void validateApprovedState() {
    requireState(reviewedAt != null, "An approved application must have reviewedAt.");

    requireState(reviewedBy != null, "An approved application must have reviewedBy.");

    requireState(rejectionReason == null, "An approved application cannot have rejectionReason.");

    requireState(
        resolvedIdentityId != null, "An approved application must have resolvedIdentityId.");

    requireState(assignedTenantId != null, "An approved application must have assignedTenantId.");

    requireState(
        resultingAffiliationId != null,
        "An approved application must have resultingAffiliationId.");
  }

  private void validateRejectedState() {
    requireState(reviewedAt != null, "A rejected application must have reviewedAt.");

    requireState(reviewedBy != null, "A rejected application must have reviewedBy.");

    requireState(rejectionReason != null, "A rejected application must have rejectionReason.");

    requireState(
        resolvedIdentityId == null, "A rejected application cannot have resolvedIdentityId.");

    requireState(assignedTenantId == null, "A rejected application cannot have assignedTenantId.");

    requireState(
        resultingAffiliationId == null,
        "A rejected application cannot have resultingAffiliationId.");
  }

  private static String normalizeRejectionReason(String rejectionReason) {
    if (rejectionReason == null) {
      return null;
    }

    String normalizedRejectionReason = rejectionReason.trim();

    if (normalizedRejectionReason.isBlank()) {
      throw new InvalidIdentityApplicationStateException("rejectionReason cannot be blank.");
    }

    if (normalizedRejectionReason.length() > MAX_REJECTION_REASON_LENGTH) {
      throw new InvalidIdentityApplicationStateException(
          "rejectionReason cannot exceed " + MAX_REJECTION_REASON_LENGTH + " characters.");
    }

    return normalizedRejectionReason;
  }

  private static void requireState(boolean condition, String message) {
    if (!condition) {
      throw new InvalidIdentityApplicationStateException(message);
    }
  }
}
