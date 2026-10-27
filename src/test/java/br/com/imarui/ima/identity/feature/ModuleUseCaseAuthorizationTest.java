package br.com.imarui.ima.identity.feature;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import br.com.imarui.ima.identity.core.application.command.identity.*;
import br.com.imarui.ima.identity.core.application.command.tenant.*;
import br.com.imarui.ima.identity.core.application.usecase.tenant.GetTenantUseCase;
import br.com.imarui.ima.authentication.core.application.service.session.AdminSessionService;
import br.com.imarui.ima.authentication.core.application.service.refreshtoken.AdminRefreshTokenService;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

class ModuleUseCaseAuthorizationTest extends IdentityIntegrationSupport {
  @Autowired GetTenantUseCase tenantQueries;
  @Autowired AdminSessionService sessionQueries;
  @Autowired AdminRefreshTokenService refreshQueries;

  private void assertProtected() {
    assertThrows(AccessDeniedException.class, () -> disableTenant.execute(new DisableTenantCommand(tenant.getId())));
    assertThrows(AccessDeniedException.class, () -> tenantQueries.execute(tenant.getId()));
    assertThrows(AccessDeniedException.class, () -> list.execute());
    assertThrows(AccessDeniedException.class, () -> sessionQueries.findPage(0, 10));
    assertThrows(AccessDeniedException.class, () -> refreshQueries.findPage(0, 10));
    assertTrue(tenants.findById(tenant.getId()).orElseThrow().isActive());
    assertEquals(0, revocations.count());
  }

  @Test void directUseCasesRejectAnActiveIdentityWithoutPermissions() {
    token(reviewer.getId()); // Persist an empty grant; invoke use cases directly, with no controller.
    assertProtected();
  }

  @Test void explicitDeniesApplyInUseCasesDespiteAnAllowAssignment() {
    for (String permission : new String[]{"TENANT_MANAGE", "TENANT_READ", "IDENTITY_APPLICATION_REVIEW", "AUTHENTICATION_SESSION_READ", "AUTHENTICATION_REFRESH_TOKEN_READ"})
      jdbc.update("insert into authorization_denies(id,permission_code,scope_id,identity_id,reason,created_at,updated_at) values(?,?,?,?,'Audit regression',current_timestamp,current_timestamp)", UUID.randomUUID(), permission, AuthorizationScope.PLATFORM_ID, reviewer.getId().value());
    assertProtected();
  }

  @Test void directUseCasesRejectAnAnonymousCaller() {
    SecurityContextHolder.clearContext();
    assertThrows(br.com.imarui.ima.identity.core.application.exception.CurrentActorUnavailableException.class, () -> list.execute());
    assertThrows(br.com.imarui.ima.identity.core.application.exception.CurrentActorUnavailableException.class, () -> sessionQueries.findPage(0, 10));
    assertThrows(br.com.imarui.ima.identity.core.application.exception.CurrentActorUnavailableException.class, () -> tenantQueries.execute(tenant.getId()));
  }

  @Test void selfServiceUseCasesRejectAnotherOwnerEvenWithKnownCredentials() {
    var target = identities.save(service("other-owner@example.test"));
    firstAccess(target.getId());
    assertThrows(AccessDeniedException.class, () -> authentication.sessions(target.getId().value()));
    assertThrows(AccessDeniedException.class, () -> authentication.endSession(target.getId().value(), UUID.randomUUID()));
    assertThrows(AccessDeniedException.class, () -> authentication.changePassword(target.getId().value(), "Fixture password 123!", "Replacement password 456!"));
  }

  @Test void aSiblingScopeGrantDoesNotAuthorizeTenantOrPlatformUseCases() {
    var sibling = br.com.imarui.ima.identity.core.domain.model.tenant.Tenant.create(
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantId.generate(),
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantCode.from("SIBLING"),
        br.com.imarui.ima.identity.core.domain.model.tenant.TenantName.from("Sibling tenant"), earlier);
    sibling.activate(earlier);
    sibling = tenants.save(sibling);
    var scope = jdbc.queryForObject("select scope_id from authorization_tenant_scopes where tenant_id=?", UUID.class, sibling.getId().value());
    Long role = jdbc.queryForObject("select role_id from authorization_identity_roles where identity_id=?", Long.class, reviewer.getId().value());
    jdbc.update("delete from authorization_identity_roles where identity_id=?", reviewer.getId().value());
    jdbc.update("insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id) values(?,?,?)", reviewer.getId().value(), role, scope);
    assertProtected();
  }
}
