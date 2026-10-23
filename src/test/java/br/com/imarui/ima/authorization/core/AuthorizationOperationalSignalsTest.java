package br.com.imarui.ima.authorization.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.imarui.ima.authorization.api.http.AuthorizationExceptionHandler;
import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import org.junit.jupiter.api.Test;

class AuthorizationOperationalSignalsTest {
  @Test void timeoutAndDatabaseFailuresHaveFixedDistinctSignalsWithoutExceptionDetails() {
    var metrics=new SimpleMeterRegistry();
    var handler=new AuthorizationExceptionHandler(mock(SecurityAuditEvents.class),Clock.systemUTC(),metrics);
    assertEquals(503,handler.unavailable(new org.springframework.dao.QueryTimeoutException("sensitive database text")).getStatus());
    assertEquals(503,handler.unavailable(new org.springframework.dao.DataAccessResourceFailureException("sensitive connection text")).getStatus());
    assertEquals(1,metrics.counter("iiam.authorization.dependency.failures","reason","TIMEOUT").count());
    assertEquals(1,metrics.counter("iiam.authorization.dependency.failures","reason","DATABASE_OR_TRANSACTION").count());
    assertEquals(2,metrics.counter("iiam.authorization.http.failures","code","AUTHORIZATION_UNAVAILABLE").count());
    assertTrue(metrics.getMeters().stream().flatMap(m -> m.getId().getTags().stream())
        .allMatch(tag -> java.util.Set.of("reason","code").contains(tag.getKey()) && !tag.getValue().contains("sensitive")));
  }
}
