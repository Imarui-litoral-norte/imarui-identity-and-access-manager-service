package br.com.imarui.ima.identity.core.domain.model.identity.person;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityStatus;
import br.com.imarui.ima.identity.core.domain.model.identity.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Getter
public final class PersonIdentity extends Identity {

  private IdentityFullName fullName;
  private final Cpf cpf;
  private LocalDate birthDate;
  private IdentityPhoneNumber phoneNumber;
  private IdentityProfilePhoto profilePhoto;

  private PersonIdentity(
      IdentityId id,
      IdentityEmail primaryEmail,
      IdentityFullName fullName,
      Cpf cpf,
      LocalDate birthDate,
      IdentityPhoneNumber phoneNumber,
      IdentityProfilePhoto profilePhoto,
      IdentityStatus status,
      Instant createdAt,
      Instant updatedAt,
      Instant activatedAt,
      Instant disabledAt,
      String nickname) {
    super(id, primaryEmail, status, createdAt, updatedAt, activatedAt, disabledAt, nickname);

    this.fullName = Objects.requireNonNull(fullName, "fullName cannot be null");

    this.cpf = Objects.requireNonNull(cpf, "cpf cannot be null");

    this.birthDate = birthDate;
    this.phoneNumber = phoneNumber;
    this.profilePhoto = profilePhoto;
  }

  public static PersonIdentity create(
      @NotNull IdentityId id,
      @NotNull IdentityEmail primaryEmail,
      @NotNull IdentityFullName fullName,
      @NotNull Cpf cpf,
      @Nullable LocalDate birthDate,
      @Nullable IdentityPhoneNumber phoneNumber,
      @NotNull Instant now,
      String nickname) {
    Objects.requireNonNull(now, "now cannot be null");

    return new PersonIdentity(
        id,
        primaryEmail,
        fullName,
        cpf,
        birthDate,
        phoneNumber,
        null,
        IdentityStatus.PENDING,
        now,
        now,
        null,
        null,
        nickname);
  }

  public static PersonIdentity reconstitute(
      @NotNull IdentityId id,
      @NotNull IdentityEmail primaryEmail,
      @NotNull IdentityFullName fullName,
      @NotNull Cpf cpf,
      @Nullable LocalDate birthDate,
      @Nullable IdentityPhoneNumber phoneNumber,
      @Nullable IdentityProfilePhoto profilePhoto,
      @NotNull IdentityStatus status,
      @NotNull Instant createdAt,
      @NotNull Instant updatedAt,
      @Nullable Instant activatedAt,
      @Nullable Instant disabledAt,
      String nickname) {
    return new PersonIdentity(
        id,
        primaryEmail,
        fullName,
        cpf,
        birthDate,
        phoneNumber,
        profilePhoto,
        status,
        createdAt,
        updatedAt,
        activatedAt,
        disabledAt,
        nickname);
  }

  @Override
  public @NotNull IdentityKind getKind() {
    return IdentityKind.PERSON;
  }

  public void changeFullName(@NotNull IdentityFullName newFullName, @NotNull Instant now) {
    Objects.requireNonNull(newFullName, "newFullName cannot be null");

    if (fullName.equals(newFullName)) {
      return;
    }

    registerChange(now);
    fullName = newFullName;
  }

  public void changeBirthDate(@NotNull LocalDate newBirthDate, @NotNull Instant now) {
    Objects.requireNonNull(newBirthDate, "newBirthDate cannot be null");

    if (newBirthDate.equals(birthDate)) {
      return;
    }

    registerChange(now);
    birthDate = newBirthDate;
  }

  public void removeBirthDate(@NotNull Instant now) {
    if (birthDate == null) {
      return;
    }

    registerChange(now);
    birthDate = null;
  }

  public void changePhoneNumber(@NotNull IdentityPhoneNumber newPhoneNumber, @NotNull Instant now) {
    Objects.requireNonNull(newPhoneNumber, "newPhoneNumber cannot be null");

    if (newPhoneNumber.equals(phoneNumber)) {
      return;
    }

    registerChange(now);
    phoneNumber = newPhoneNumber;
  }

  public void removePhoneNumber(@NotNull Instant now) {
    if (phoneNumber == null) {
      return;
    }

    registerChange(now);
    phoneNumber = null;
  }

  public void changeProfilePhoto(
      @NotNull IdentityProfilePhoto newProfilePhoto, @NotNull Instant now) {
    Objects.requireNonNull(newProfilePhoto, "newProfilePhoto cannot be null");

    if (newProfilePhoto.equals(profilePhoto)) {
      return;
    }

    registerChange(now);
    profilePhoto = newProfilePhoto;
  }

  public void removeProfilePhoto(@NotNull Instant now) {
    if (profilePhoto == null) {
      return;
    }

    registerChange(now);
    profilePhoto = null;
  }
}
