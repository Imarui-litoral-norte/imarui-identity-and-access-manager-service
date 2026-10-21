package br.com.imarui.ima.identity.api.module;

import br.com.imarui.ima.identity.core.domain.enums.identity.*;
import java.time.Instant;
import java.util.*;

public interface IdentityAuthenticationApi {
  record Account(UUID id, IdentityKind kind, IdentityStatus status, String nickname, String email) {
    public boolean active() {
      return status == IdentityStatus.ACTIVE;
    }
  }

  Optional<Account> resolve(IdentityKind kind, String identifier);

  Optional<Account> lock(UUID id);

  void activate(UUID id);

  boolean valid(UUID id, Instant issuedAt, String tenantId);
}
