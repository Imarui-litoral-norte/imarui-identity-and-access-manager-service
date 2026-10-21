package br.com.imarui.ima.authentication.core.port;

import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import java.time.Instant;
import java.util.*;

public interface IdentityAccess {
  record IdentityView(
      UUID id, IdentityKind kind, IdentityStatus status, String nickname, String email) {
    public boolean active() {
      return status == IdentityStatus.ACTIVE;
    }
  }

  Optional<IdentityView> resolve(IdentityKind kind, String identifier);

  Optional<IdentityView> lock(UUID id);

  void activate(UUID id);

  boolean valid(UUID id, Instant issuedAt, String tenant);
}
