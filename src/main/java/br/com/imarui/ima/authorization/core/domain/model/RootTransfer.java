package br.com.imarui.ima.authorization.core.domain.model;

import java.time.Instant;
import java.util.UUID;

public record RootTransfer(UUID id,UUID initiatorId,UUID initiatorSessionId,UUID recipientId,UUID scopeId,
    String reason,UUID correlationId,Instant createdAt,Instant expiresAt,Instant confirmedAt) {
  public boolean pending(Instant now) { return confirmedAt==null && now.isBefore(expiresAt); }
}
