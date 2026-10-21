package br.com.imarui.ima.authentication.core.domain.model;

import java.time.*;
import java.util.UUID;

public record AuthenticationChallenge(
    UUID id,
    UUID identityId,
    Purpose purpose,
    String tokenHash,
    Instant createdAt,
    Instant expiresAt,
    Instant consumedAt) {
  public enum Purpose {
    ACCOUNT_ACTIVATION,
    PASSWORD_RESET
  }

  public boolean valid(Purpose expected, Instant now) {
    return purpose == expected && consumedAt == null && now.isBefore(expiresAt);
  }

  @Override
  public String toString() {
    return "AuthenticationChallenge[REDACTED]";
  }
}
