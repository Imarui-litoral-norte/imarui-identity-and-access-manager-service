package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authorization.api.module.IdentityAuthorities;
import br.com.imarui.ima.authorization.core.application.usecase.EffectiveAuthorizationUseCases;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizationIdentityAuthoritiesProvider implements IdentityAuthorities {
  private final EffectiveAuthorizationUseCases authorizations;

  public List<String> find(UUID id) {
    return authorizations.platformAuthorities(id);
  }
}
