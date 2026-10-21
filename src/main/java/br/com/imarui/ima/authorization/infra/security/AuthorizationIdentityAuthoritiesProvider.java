package br.com.imarui.ima.authorization.infra.security;

import br.com.imarui.ima.authorization.api.module.IdentityAuthorities;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthorizationIdentityAuthoritiesProvider implements IdentityAuthorities {
  private final JdbcTemplate jdbc;

  public List<String> find(UUID id) {
    return jdbc.queryForList(
        "select distinct p.code from authorization_permissions p join"
            + " authorization_role_permissions rp on rp.permission_id=p.id join authorization_roles"
            + " r on r.id=rp.role_id join authorization_identity_roles ir on ir.role_id=r.id join"
            + " ima_identities i on i.id=ir.identity_id where ir.identity_id=? and"
            + " ir.scope='PLATFORM' and r.active and i.status='ACTIVE' order by p.code",
        String.class,
        id);
  }
}
