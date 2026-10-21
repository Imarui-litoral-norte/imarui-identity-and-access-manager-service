package br.com.imarui.ima.authentication.infra.integration;

import br.com.imarui.ima.authentication.core.port.IdentityAccess;
import br.com.imarui.ima.identity.api.module.IdentityAuthenticationApi;
import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import java.time.Instant;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthenticationIdentityAdapter implements IdentityAccess {
  private final IdentityAuthenticationApi api;

  private IdentityView view(IdentityAuthenticationApi.Account a) {
    return new IdentityView(a.id(), a.kind(), a.status(), a.nickname(), a.email());
  }

  public Optional<IdentityView> resolve(IdentityKind k, String s) {
    return api.resolve(k, s).map(this::view);
  }

  public Optional<IdentityView> lock(UUID id) {
    return api.lock(id).map(this::view);
  }

  public void activate(UUID id) {
    api.activate(id);
  }

  public boolean valid(UUID id, Instant iat, String tenant) {
    return api.valid(id, iat, tenant);
  }
}
