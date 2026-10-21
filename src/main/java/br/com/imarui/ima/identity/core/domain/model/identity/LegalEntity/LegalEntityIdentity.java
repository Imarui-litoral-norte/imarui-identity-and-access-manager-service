package br.com.imarui.ima.identity.core.domain.model.identity.LegalEntity;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.Identity;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityEmail;
import br.com.imarui.ima.identity.core.domain.model.identity.IdentityId;
import java.time.Instant;
import java.util.Objects;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class LegalEntityIdentity extends Identity {

  private LegalName legalName;
  private TradeName tradeName;
  private final Cnpj cnpj;

  private LegalEntityIdentity(
      IdentityId id,
      IdentityEmail primaryEmail,
      LegalName legalName,
      TradeName tradeName,
      Cnpj cnpj,
      IdentityStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant activatedAt,
      Instant disabledAt,
      String nickname) {
    super(id, primaryEmail, status, createdAt, updatedAt, activatedAt, disabledAt, nickname);

    this.legalName = Objects.requireNonNull(legalName, "legalName cannot be null");

    this.tradeName = tradeName;

    this.cnpj = Objects.requireNonNull(cnpj, "cnpj cannot be null");
  }

  public static LegalEntityIdentity create(
      @NotNull IdentityId id,
      @NotNull IdentityEmail primaryEmail,
      @NotNull LegalName legalName,
      @Nullable TradeName tradeName,
      @NotNull Cnpj cnpj,
      @NotNull Instant now,
      String nickname) {
    Objects.requireNonNull(now, "now cannot be null");

    return new LegalEntityIdentity(
        id,
        primaryEmail,
        legalName,
        tradeName,
        cnpj,
        IdentityStatus.PENDING,
        now,
        now,
        null,
        null,
        nickname);
  }

  public static LegalEntityIdentity reconstitute(
      @NotNull IdentityId id,
      @NotNull IdentityEmail primaryEmail,
      @NotNull LegalName legalName,
      @Nullable TradeName tradeName,
      @NotNull Cnpj cnpj,
      @NotNull IdentityStatus status,
      @NotNull Instant createdAt,
      @NotNull Instant updatedAt,
      @Nullable Instant activatedAt,
      @Nullable Instant disabledAt,
      String nickname) {
    return new LegalEntityIdentity(
        id,
        primaryEmail,
        legalName,
        tradeName,
        cnpj,
        status,
        createdAt,
        updatedAt,
        activatedAt,
        disabledAt,
        nickname);
  }

  @Override
  public @NotNull IdentityKind getKind() {
    return IdentityKind.LEGAL_ENTITY;
  }

  public void changeLegalName(@NotNull LegalName newLegalName, @NotNull Instant now) {
    Objects.requireNonNull(newLegalName, "newLegalName cannot be null");

    if (legalName.equals(newLegalName)) {
      return;
    }

    registerChange(now);
    legalName = newLegalName;
  }

  public void changeTradeName(@NotNull TradeName newTradeName, @NotNull Instant now) {
    Objects.requireNonNull(newTradeName, "newTradeName cannot be null");

    if (newTradeName.equals(tradeName)) {
      return;
    }

    registerChange(now);
    tradeName = newTradeName;
  }

  public void removeTradeName(@NotNull Instant now) {
    if (tradeName == null) {
      return;
    }

    registerChange(now);
    tradeName = null;
  }
}
