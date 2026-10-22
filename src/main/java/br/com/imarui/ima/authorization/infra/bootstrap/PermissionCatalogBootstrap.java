package br.com.imarui.ima.authorization.infra.bootstrap;

import br.com.imarui.ima.authorization.api.catalog.*;
import br.com.imarui.ima.authorization.core.application.usecase.PermissionCatalogUseCases;
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
  private final PermissionCatalogUseCases catalogs;

  @Transactional
  public void run(ApplicationArguments args) {
    catalogs.synchronizeInternal();
    // Explicit internal ROOT provisioning is separate from registration. External catalog entries never grant access.
    jdbc.update(
        "insert into authorization_roles(name,active) values('ROOT',true) on conflict(name) do"
            + " nothing");
    for (var definition : PermissionDefinition.values()) jdbc.update(
        "insert into authorization_role_permissions(role_id,permission_id) select r.id,p.id from"
            + " authorization_roles r cross join authorization_permissions p where r.name='ROOT'"
            + " and p.service_code='iiam' and p.code=? on conflict do nothing", definition.code().name());
  }
}
