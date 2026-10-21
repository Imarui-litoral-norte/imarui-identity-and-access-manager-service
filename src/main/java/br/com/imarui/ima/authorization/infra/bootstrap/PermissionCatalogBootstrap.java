package br.com.imarui.ima.authorization.infra.bootstrap;

import br.com.imarui.ima.authorization.api.catalog.*;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.*;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Order(0)
@RequiredArgsConstructor
public class PermissionCatalogBootstrap implements ApplicationRunner {
  private final JdbcTemplate jdbc;

  @Transactional
  public void run(ApplicationArguments args) {
    jdbc.query("select pg_advisory_xact_lock(390047)", r -> {});
    for (var d : PermissionDefinition.values())
      jdbc.update(
          "insert into authorization_permissions(code,description,module) values(?,?,?) on"
              + " conflict(code) do update set"
              + " description=excluded.description,module=excluded.module",
          d.code().name(),
          d.description(),
          d.module().name());
    jdbc.update(
        "insert into authorization_roles(name,active) values('ROOT',true) on conflict(name) do"
            + " nothing");
    jdbc.update(
        "insert into authorization_role_permissions(role_id,permission_id) select r.id,p.id from"
            + " authorization_roles r cross join authorization_permissions p where r.name='ROOT' on"
            + " conflict do nothing");
  }
}
