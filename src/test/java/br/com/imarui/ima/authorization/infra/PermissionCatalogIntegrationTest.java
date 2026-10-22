package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.application.port.CatalogServiceAuthentication;
import br.com.imarui.ima.authorization.core.application.usecase.PermissionCatalogUseCases;
import br.com.imarui.ima.authorization.core.domain.model.PermissionManifest;
import br.com.imarui.ima.authorization.core.repository.*;
import br.com.imarui.ima.authorization.infra.bootstrap.PermissionCatalogBootstrap;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.support.TransactionTemplate;

class PermissionCatalogIntegrationTest extends IdentityIntegrationSupport {
  @Autowired PermissionCatalogRepository catalog;
  @Autowired AuthorizationCatalog queries;
  @Autowired PermissionCatalogUseCases configured;
  @Autowired PermissionCatalogBootstrap bootstrap;
  @Autowired Clock clock;

  private String owner() {
    String owner="service-"+UUID.randomUUID().toString().replace("-","");
    jdbc.update("insert into authorization_catalog_owners(service_code,namespace,updated_at) values(?,?,current_timestamp)",owner,owner);
    return owner;
  }
  private PermissionManifest.Entry entry(String code) { return new PermissionManifest.Entry(code,"Read route","journey",false); }
  private PermissionManifest manifest(String owner,long version,PermissionManifest.Entry... entries) {
    return new PermissionManifest(owner,owner,version,List.of(entries));
  }
  private boolean sync(String authenticated,PermissionManifest manifest) {
    var service=new PermissionCatalogUseCases(catalog,Optional.of((CatalogServiceAuthentication)() -> authenticated),clock);
    return new TransactionTemplate(transactions).execute(s -> service.synchronizeExternal(manifest));
  }

  @Test void externalRegistrationIsIdempotentPreservesIdsAndNeverGrantsPermissions() {
    var owner=owner(); var code=owner+".route.read";
    long grants=jdbc.queryForObject("select count(*) from authorization_role_permissions",Long.class);
    assertTrue(sync(owner,manifest(owner,1,entry(code))));
    long id=jdbc.queryForObject("select id from authorization_permissions where code=?",Long.class,code);
    assertFalse(sync(owner,manifest(owner,1,entry(code))));
    assertTrue(sync(owner,manifest(owner,2,new PermissionManifest.Entry(code,"Updated","journey",false))));
    assertEquals(id,jdbc.queryForObject("select id from authorization_permissions where code=?",Long.class,code));
    assertEquals(grants,jdbc.queryForObject("select count(*) from authorization_role_permissions",Long.class));
    var persisted=queries.permissions().stream().filter(p -> p.getCode().equals(code)).findFirst().orElseThrow();
    assertEquals(owner,persisted.getServiceCode());
    assertEquals(2,persisted.getCatalogVersion());
    assertEquals("Updated",persisted.getDescription());
    assertEquals(2,jdbc.queryForObject("select count(*) from authorization_catalog_revisions where service_code=?",Integer.class,owner));
  }

  @Test void omissionDeprecatesWithoutBreakingReferencesOrDeletingHistory() {
    var owner=owner(); var code=owner+".read";
    sync(owner,manifest(owner,1,entry(code)));
    long id=jdbc.queryForObject("select id from authorization_permissions where code=?",Long.class,code);
    long role=jdbc.queryForObject("insert into authorization_roles(name,ownership,active) values(?,'GLOBAL',true) returning id",Long.class,"catalog-"+owner);
    jdbc.update("insert into authorization_role_permissions(role_id,permission_id) values(?,?)",role,id);
    sync(owner,manifest(owner,2));
    assertTrue(jdbc.queryForObject("select deprecated from authorization_permissions where id=?",Boolean.class,id));
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_role_permissions where role_id=? and permission_id=?",Integer.class,role,id));
    assertEquals(2,jdbc.queryForObject("select count(*) from authorization_catalog_revisions where service_code=?",Integer.class,owner));
  }

  @Test void ownershipVersionConflictsAndDowngradesPreserveLastValidCatalog() {
    var owner=owner(); var other=owner(); var code=owner+".read";
    var valid=manifest(owner,1,entry(code));
    assertThrows(SecurityException.class,() -> sync(other,valid));
    sync(owner,valid);
    assertThrows(IllegalArgumentException.class,() -> sync(owner,manifest(owner,1)));
    sync(owner,manifest(owner,2,entry(code)));
    assertThrows(IllegalArgumentException.class,() -> sync(owner,valid));
    assertEquals(2,jdbc.queryForObject("select version from authorization_catalog_owners where service_code=?",Long.class,owner));
    assertFalse(jdbc.queryForObject("select deprecated from authorization_permissions where code=?",Boolean.class,code));
  }

  @Test void codeCollisionRollsBackEarlierEntriesAndLeavesOwnerVersionUntouched() {
    var owner=owner(); var collision=owner+".collision";
    jdbc.update("insert into authorization_permissions(code,description,module,service_code,namespace,catalog_version,updated_at) values(?,'Legacy collision','IDENTITY','iiam','iiam',1,current_timestamp)",collision);
    var invalid=manifest(owner,1,entry(owner+".aaa"),entry(collision));
    assertThrows(SecurityException.class,() -> sync(owner,invalid));
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_permissions where code=?",Integer.class,owner+".aaa"));
    assertEquals(0,jdbc.queryForObject("select version from authorization_catalog_owners where service_code=?",Long.class,owner));
    assertEquals("iiam",jdbc.queryForObject("select service_code from authorization_permissions where code=?",String.class,collision));
  }

  @Test void missingTechnicalAdapterRejectsExternalSyncAndNoExternalHttpEndpointIsExposed() throws Exception {
    var owner=owner();
    assertThrows(SecurityException.class,() -> configured.synchronizeExternal(manifest(owner,1,entry(owner+".read"))));
    assertEquals(401,http("POST","/api/v1/authorization/catalogs/synchronize","{}",null).statusCode());
    assertEquals(404,http("POST","/api/v1/authorization/catalogs/synchronize","{}",token(reviewer.getId())).statusCode());
  }

  @Test void repeatedInternalBootstrapDoesNotGrantExternalCatalogEntriesToRoot() {
    var owner=owner(); var code=owner+".read";
    sync(owner,manifest(owner,1,entry(code)));
    new TransactionTemplate(transactions).executeWithoutResult(s -> {
      bootstrap.run(new DefaultApplicationArguments()); bootstrap.run(new DefaultApplicationArguments());
    });
    assertEquals(0,jdbc.queryForObject("select count(*) from authorization_role_permissions rp join authorization_roles r on r.id=rp.role_id join authorization_permissions p on p.id=rp.permission_id where r.name='ROOT' and p.code=?",Integer.class,code));
    assertEquals(1,jdbc.queryForObject("select count(*) from authorization_catalog_revisions where service_code='iiam'",Integer.class));
  }

  @Test void conflictingConcurrentManifestsHaveOneWinnerAndKeepOnlyOneValidRevision() throws Exception {
    var owner=owner(); var code=owner+".read"; var barrier=new java.util.concurrent.CyclicBarrier(2);
    var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    java.util.function.Function<String,java.util.concurrent.Callable<Boolean>> update=description -> () -> {
      barrier.await(10,java.util.concurrent.TimeUnit.SECONDS);
      try { return sync(owner,manifest(owner,1,new PermissionManifest.Entry(code,description,"journey",false))); }
      catch (IllegalArgumentException conflict) { return false; }
    };
    try {
      var a=pool.submit(update.apply("Manifest A")); var b=pool.submit(update.apply("Manifest B"));
      assertNotEquals(a.get(20,java.util.concurrent.TimeUnit.SECONDS),b.get(20,java.util.concurrent.TimeUnit.SECONDS));
      assertEquals(1,jdbc.queryForObject("select count(*) from authorization_catalog_revisions where service_code=?",Integer.class,owner));
      assertEquals(1,jdbc.queryForObject("select version from authorization_catalog_owners where service_code=?",Long.class,owner));
      assertTrue(Set.of("Manifest A","Manifest B").contains(jdbc.queryForObject("select description from authorization_permissions where code=?",String.class,code)));
    } finally { pool.shutdownNow(); }
  }
}
