package br.com.imarui.ima.identity.infra.integration;

import br.com.imarui.ima.identity.api.module.IdentityAuthorizationApi;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class IdentityAuthorizationAdapter implements IdentityAuthorizationApi {
  private final JdbcTemplate jdbc;
  public Set<UUID> activeIdentityIds() {
    return Set.copyOf(jdbc.queryForList("select id from ima_identities where status='ACTIVE'",UUID.class));
  }
  public Set<UUID> activeTenantIds() {
    return Set.copyOf(jdbc.queryForList("select id from ima_tenants where status='ACTIVE'",UUID.class));
  }
}
