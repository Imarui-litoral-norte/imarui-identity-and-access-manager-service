package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class QueuedAuthenticationAdministrationTest extends IdentityIntegrationSupport {
  @ParameterizedTest
  @CsvSource({"invitation,true", "invitation,false", "session,true", "session,false", "refresh,true", "refresh,false", "password,true"})
  void queuedAuthenticationAdministrationRechecksItsActor(String operation, boolean revokeSession) throws Exception {
    var target = operation.equals("password") ? reviewer : identities.save(service("queued-authentication@example.test"));
    String table, path, permission;
    Object resource;
    if (operation.equals("password")) {
      table = "ima_identities"; resource = target.getId().value();
      path = "/authentication/password"; permission = "IDENTITY_READ";
    } else if (operation.equals("invitation")) {
      table = "ima_identities"; resource = target.getId().value();
      path = "/authentication/admin/identities/" + resource + "/invitation";
      permission = "AUTHENTICATION_INVITATION_CREATE";
    } else {
      firstAccess(target.getId());
      authentication.login(br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind.SERVICE, target.getNickname(), "Fixture password 123!");
      Long session = jdbc.queryForObject("select id from authentication_sessions where identity_id=?", Long.class, target.getId().value());
      if (operation.equals("session")) {
        table = "authentication_sessions"; resource = session;
        path = "/authentication/admin/sessions/" + resource + "/logout";
        permission = "AUTHENTICATION_SESSION_LOGOUT";
      } else {
        table = "authentication_refresh_tokens";
        resource = jdbc.queryForObject("select id from authentication_refresh_tokens where session_id=?", Long.class, session);
        path = "/authentication/admin/refresh-tokens/" + resource + "/revoke";
        permission = "AUTHENTICATION_REFRESH_TOKEN_REVOKE";
      }
    }
    var jwt = token(reviewer.getId(), permission);
    var executor = Executors.newSingleThreadExecutor();
    try (var blocker = jdbc.getDataSource().getConnection()) {
      blocker.setAutoCommit(false);
      int pid;
      try (var query = blocker.createStatement(); var row = query.executeQuery("select pg_backend_pid()")) { row.next(); pid = row.getInt(1); }
      // Table names are fixed above, never supplied by an HTTP caller.
      try (var query = blocker.prepareStatement("select id from " + table + " where id=? for update")) { query.setObject(1, resource); query.executeQuery().close(); }
      var body = operation.equals("password") ? "{\"currentPassword\":\"Fixture password 123!\",\"newPassword\":\"Replacement password 456!\"}" : null;
      var response = executor.submit(() -> http(operation.equals("invitation") || operation.equals("password") ? "POST" : "PATCH", path, body, jwt));
      boolean waiting = false;
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (System.nanoTime() < deadline) {
        waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))", Boolean.class, pid));
        if (waiting) break;
        Thread.sleep(15);
      }
      assertTrue(waiting, "Request is authenticated and waiting for its target lock");
      if (revokeSession)
        jdbc.update("update authentication_sessions set session_status='REVOKED',revoked_at=current_timestamp where identity_id=?", reviewer.getId().value());
      else
        jdbc.update("delete from authorization_identity_roles where identity_id=?", reviewer.getId().value());
      blocker.commit();
      var result = response.get(10, TimeUnit.SECONDS);
      assertEquals(revokeSession ? 401 : 403, result.statusCode(), result.body());
      if (operation.equals("password")) assertNotNull(authentication.login(br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind.SERVICE, "reviewer", "Fixture password 123!"));
      else if (operation.equals("invitation")) assertEquals(0, jdbc.queryForObject("select count(*) from authentication_challenges where identity_id=?", Integer.class, target.getId().value()));
      else assertEquals("ACTIVE", jdbc.queryForObject("select " + (operation.equals("session") ? "session_status" : "status") + " from " + table + " where id=?", String.class, resource));
    } finally { executor.shutdownNow(); }
  }
}
