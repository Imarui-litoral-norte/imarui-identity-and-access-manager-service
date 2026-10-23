package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.ScopeRepository;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.support.TransactionTemplate;

class ScopePersistenceIntegrationTest extends IdentityIntegrationSupport {
  @Autowired ScopeRepository scopes;

  @Test void tenantBindingIsCreatedAtomicallyAndIsStableWithoutReusingTenantIdentity() {
    var scopeId = scopes.tenantScope(tenant.getId().value()).orElseThrow();
    assertNotEquals(tenant.getId().value(), scopeId);
    assertEquals(AuthorizationScope.PLATFORM_ID, scopes.find(scopeId).orElseThrow().parentId());
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "update authorization_tenant_scopes set scope_id=? where tenant_id=?", UUID.randomUUID(), tenant.getId().value()));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "delete from authorization_tenant_scopes where tenant_id=?", tenant.getId().value()));
    assertEquals(scopeId, scopes.tenantScope(tenant.getId().value()).orElseThrow());
  }

  @Test void databaseRejectsCyclesAndPreservesTreeAfterRollback() {
    var now = Instant.now();
    var a = AuthorizationScope.create("TREE_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT), "A", null, now);
    var b = AuthorizationScope.create("CHILD_"+UUID.randomUUID().toString().replace("-","").toUpperCase(Locale.ROOT), "B", a.id(), now);
    new TransactionTemplate(transactions).executeWithoutResult(s -> { scopes.save(a); scopes.save(b); });
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "update authorization_scopes set parent_id=? where id=?", b.id(), a.id()));
    assertNull(scopes.find(a.id()).orElseThrow().parentId());
    assertEquals(List.of(b.id(),a.id()), scopes.ancestors(b.id()).stream().map(AuthorizationScope::id).toList());
    assertEquals(2, scopes.descendants(a.id()).size());
  }

  @Test void reparentingPreservesUuidAndVersionAndUncommittedChangesRollback() {
    var scopeId = scopes.tenantScope(tenant.getId().value()).orElseThrow();
    var original = scopes.find(scopeId).orElseThrow();
    new TransactionTemplate(transactions).executeWithoutResult(s -> scopes.save(
        original.revise("Independent", original.status(), null, Instant.now())));
    var moved = scopes.find(scopeId).orElseThrow();
    assertNull(moved.parentId());
    assertEquals(original.id(), moved.id());
    assertEquals(original.version()+1, moved.version());
    assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).executeWithoutResult(s -> {
      scopes.save(moved.revise("Rollback", moved.status(), original.parentId(), Instant.now()));
      throw new IllegalStateException("rollback");
    }));
    assertEquals(moved, scopes.find(scopeId).orElseThrow());
  }

  @Test void platformBoundaryCannotBeDisabledMovedOrDeleted() {
    var id = AuthorizationScope.PLATFORM_ID;
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "update authorization_scopes set status='DISABLED' where id=?", id));
    assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
        "delete from authorization_scopes where id=?", id));
    assertTrue(scopes.find(id).isPresent());
  }
}
