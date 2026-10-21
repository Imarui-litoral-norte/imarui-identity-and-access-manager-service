package br.com.imarui.ima.identity.core.domain.model.identity;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.core.domain.exception.identity.IdentityAlreadyActiveException;
import br.com.imarui.ima.identity.core.domain.exception.identity.IdentityAlreadyDisabledException;
import br.com.imarui.ima.identity.core.domain.exception.identity.IdentityNotDisabledException;
import br.com.imarui.ima.identity.core.domain.exception.identity.InvalidIdentityStateException;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

@Getter
public abstract class Identity {

  private final IdentityId id;
  private String nickname;

  public void assignNickname(String value) {
    nickname = br.com.imarui.ima.identity.core.domain.model.identity.Nickname.from(value).value();
  }

  private IdentityEmail primaryEmail;
  private IdentityStatus status;
  private final Instant createdAt;
  private Instant updatedAt;
  private Instant activatedAt;
  private Instant disabledAt;

  protected Identity(
      IdentityId id,
      IdentityEmail primaryEmail,
      IdentityStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant activatedAt,
      Instant disabledAt,
      String nickname) {
    this.id = Objects.requireNonNull(id, "id cannot be null");

    this.nickname = Nickname.from(nickname).value();
    this.primaryEmail = primaryEmail;
    if (primaryEmail == null) throw new IllegalArgumentException("Primary email is required.");

    this.status = Objects.requireNonNull(status, "status cannot be null");

    this.createdAt = Objects.requireNonNull(createdAt, "createdAt cannot be null");

    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt cannot be null");

    this.activatedAt = activatedAt;
    this.disabledAt = disabledAt;

    validateState();
  }

  @NotNull
  public abstract IdentityKind getKind();

  public void changeNickname(String value, Instant now) {
    validateEventTime(now);
    assignNickname(value);
    updatedAt = now;
  }

  public final boolean isPending() {
    return status == IdentityStatus.PENDING;
  }

  public final boolean isActive() {
    return status == IdentityStatus.ACTIVE;
  }

  public final boolean isDisabled() {
    return status == IdentityStatus.DISABLED;
  }

  public final void activate(@NotNull Instant now) {
    Objects.requireNonNull(now, "now cannot be null");
    validateEventTime(now);

    if (!isPending()) {
      throw new IdentityAlreadyActiveException(id.toString());
    }

    status = IdentityStatus.ACTIVE;
    activatedAt = now;
    disabledAt = null;
    updatedAt = now;
  }

  public final void disable(@NotNull Instant now) {
    Objects.requireNonNull(now, "now cannot be null");
    validateEventTime(now);

    if (isDisabled()) {
      throw new IdentityAlreadyDisabledException(id.toString());
    }

    if (!isActive()) {
      throw new InvalidIdentityStateException("Only an active identity can be disabled.");
    }

    status = IdentityStatus.DISABLED;
    disabledAt = now;
    updatedAt = now;
  }

  public final void reactivate(@NotNull Instant now) {
    Objects.requireNonNull(now, "now cannot be null");
    validateEventTime(now);

    if (!isDisabled()) {
      throw new IdentityNotDisabledException(id.toString());
    }

    status = IdentityStatus.ACTIVE;
    disabledAt = null;
    updatedAt = now;
  }

  public final void changePrimaryEmail(
      @NotNull IdentityEmail newPrimaryEmail, @NotNull Instant now) {
    Objects.requireNonNull(newPrimaryEmail, "newPrimaryEmail cannot be null");

    Objects.requireNonNull(now, "now cannot be null");

    validateEventTime(now);

    if (primaryEmail.equals(newPrimaryEmail)) {
      return;
    }

    primaryEmail = newPrimaryEmail;
    updatedAt = now;
  }

  protected final void registerChange(@NotNull Instant now) {
    Objects.requireNonNull(now, "now cannot be null");
    validateEventTime(now);

    updatedAt = now;
  }

  private void validateState() {
    if (updatedAt.isBefore(createdAt)) {
      throw new InvalidIdentityStateException("updatedAt cannot be before createdAt.");
    }

    if (activatedAt != null && activatedAt.isBefore(createdAt)) {
      throw new InvalidIdentityStateException("activatedAt cannot be before createdAt.");
    }

    if (disabledAt != null && disabledAt.isBefore(createdAt)) {
      throw new InvalidIdentityStateException("disabledAt cannot be before createdAt.");
    }

    if (activatedAt != null && activatedAt.isAfter(updatedAt)) {
      throw new InvalidIdentityStateException("activatedAt cannot be after updatedAt.");
    }

    if (disabledAt != null && disabledAt.isAfter(updatedAt)) {
      throw new InvalidIdentityStateException("disabledAt cannot be after updatedAt.");
    }

    if (activatedAt != null && disabledAt != null && disabledAt.isBefore(activatedAt)) {
      throw new InvalidIdentityStateException("disabledAt cannot be before activatedAt.");
    }

    switch (status) {
      case PENDING -> validatePendingState();
      case ACTIVE -> validateActiveState();
      case DISABLED -> validateDisabledState();
    }
  }

  private void validatePendingState() {
    if (activatedAt != null) {
      throw new InvalidIdentityStateException("A pending identity cannot have activatedAt.");
    }

    if (disabledAt != null) {
      throw new InvalidIdentityStateException("A pending identity cannot have disabledAt.");
    }
  }

  private void validateActiveState() {
    if (activatedAt == null) {
      throw new InvalidIdentityStateException("An active identity must have activatedAt.");
    }

    if (disabledAt != null) {
      throw new InvalidIdentityStateException("An active identity cannot have disabledAt.");
    }
  }

  private void validateDisabledState() {
    if (activatedAt == null) {
      throw new InvalidIdentityStateException("A disabled identity must have activatedAt.");
    }

    if (disabledAt == null) {
      throw new InvalidIdentityStateException("A disabled identity must have disabledAt.");
    }
  }

  private void validateEventTime(Instant now) {
    if (now.isBefore(updatedAt)) {
      throw new IllegalArgumentException("Event time cannot be before updatedAt.");
    }
  }
}
