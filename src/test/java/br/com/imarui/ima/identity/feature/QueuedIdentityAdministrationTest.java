package br.com.imarui.ima.identity.feature;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class QueuedIdentityAdministrationTest extends IdentityIntegrationSupport {
  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  void queuedDisableRechecksSessionAndPolicyAfterResourceLock(boolean revokeSession) throws Exception {
    var target = identities.save(service("queued-target@example.test"));
    firstAccess(target.getId());
    var jwt = token(reviewer.getId(), "IDENTITY_MANAGE");
    var executor = Executors.newSingleThreadExecutor();
    try (var blocker = jdbc.getDataSource().getConnection()) {
      blocker.setAutoCommit(false);
      int pid;
      try (var query = blocker.createStatement(); var row = query.executeQuery("select pg_backend_pid()")) {
        row.next(); pid = row.getInt(1);
      }
      try (var query = blocker.prepareStatement("select id from ima_identities where id=? for update")) {
        query.setObject(1, target.getId().value()); query.executeQuery().close();
      }
      var response = executor.submit(() -> http("POST", "/api/v1/admin/identities/" + target.getId() + "/disable", null, jwt));
      boolean waiting = false;
      long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
      while (System.nanoTime() < deadline) {
        waiting = Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from pg_stat_activity where ? = any(pg_blocking_pids(pid)))", Boolean.class, pid));
        if (waiting) break;
        Thread.sleep(15);
      }
      assertTrue(waiting, "HTTP request has passed its filter and is waiting for the resource");
      if (revokeSession)
        jdbc.update("update authentication_sessions set session_status='REVOKED',revoked_at=current_timestamp where identity_id=?", reviewer.getId().value());
      else
        jdbc.update("delete from authorization_identity_roles where identity_id=?", reviewer.getId().value());
      blocker.commit();
      var result = response.get(10, TimeUnit.SECONDS);
      assertEquals(revokeSession ? 401 : 403, result.statusCode(), result.body());
      assertTrue(identities.findById(target.getId()).orElseThrow().isActive());
      assertEquals(0, revocations.count());
    } finally { executor.shutdownNow(); }
  }
}
