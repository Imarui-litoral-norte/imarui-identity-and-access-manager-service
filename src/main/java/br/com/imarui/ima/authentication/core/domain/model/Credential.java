package br.com.imarui.ima.authentication.core.domain.model;

import java.time.*;
import java.util.UUID;

/** Password state only. Identity owns eligibility and all personal information. */
public record Credential(
    UUID identityId,
    String passwordHash,
    int failures,
    Instant lockedUntil,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt) {
  public boolean allowed(Instant now) {
    return enabled && passwordHash != null && (lockedUntil == null || !now.isBefore(lockedUntil));
  }

  public Credential failed(Instant now) {
    int n = lockedUntil != null && !now.isBefore(lockedUntil) ? 1 : failures + 1;
    return new Credential(
        identityId,
        passwordHash,
        n,
        n >= 5 ? now.plusSeconds(900) : lockedUntil,
        enabled,
        createdAt,
        now);
  }

  public Credential successful(Instant now) {
    return new Credential(identityId, passwordHash, 0, null, enabled, createdAt, now);
  }

  public Credential password(String hash, Instant now) {
    return new Credential(identityId, hash, 0, null, true, createdAt, now);
  }

  public static void validatePassword(String password) {
    if (password == null
        || password.codePointCount(0, password.length()) < 12
        || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72)
      throw new IllegalArgumentException(
          "Password must have at least 12 characters and at most 72 UTF-8 bytes.");
  }

  @Override
  public String toString() {
    return "Credential[REDACTED]";
  }
}
