package br.com.imarui.ima.authentication.feature;

import static org.junit.jupiter.api.Assertions.*;

import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.util.UUID;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class MigrationUpgradeIntegrationTest extends IdentityIntegrationSupport {
  @Autowired DataSource source;

  @Test
  void upgradesV5PreservingUnownedSessionHistory() throws Exception {
    String schema = "upgrade_" + UUID.randomUUID().toString().replace("-", "");
    String url;
    String user;
    try (var metadata = source.getConnection()) {
      url = metadata.getMetaData().getURL();
      user = metadata.getMetaData().getUserName();
    }
    // Never change search_path on the application pool: use a dedicated disposable JDBC connection.
    try (var connection = java.sql.DriverManager.getConnection(url, user, "identity_test")) {
      var flyway =
          Flyway.configure()
              .dataSource(url, user, "identity_test")
              .schemas(schema)
              .defaultSchema(schema)
              .target("5")
              .load();
      flyway.migrate();
      try (var sql = connection.createStatement()) {
        connection.setSchema(schema);
        sql.execute(
            "insert into authentication_sessions(id,created_at,expires_at,session_status)"
                + " values(100,current_timestamp,current_timestamp+interval '12 hours','ACTIVE')");
        sql.execute(
            "insert into"
                + " authentication_refresh_tokens(session_id,token_hash,created_at,expires_at,status)"
                + " values(100,'legacy-hash',current_timestamp,current_timestamp+interval '1"
                + " day','ACTIVE')");
      }
      Flyway.configure()
          .dataSource(url, user, "identity_test")
          .schemas(schema)
          .defaultSchema(schema)
          .load()
          .migrate();
      try (var sql = connection.createStatement();
          var row =
              sql.executeQuery(
                  "select session_status,identity_id,public_id from authentication_sessions where"
                      + " id=100")) {
        assertTrue(row.next());
        assertEquals("REVOKED", row.getString(1));
        assertNull(row.getObject(2));
        assertNull(row.getObject(3));
      }
      try (var sql = connection.createStatement();
          var row =
              sql.executeQuery(
                  "select status from authentication_refresh_tokens where"
                      + " token_hash='legacy-hash'")) {
        assertTrue(row.next());
        assertEquals("REVOKED", row.getString(1));
      }
    } finally {
      jdbc.execute("drop schema if exists " + schema + " cascade");
    }
  }

  @Test
  void upgradesPublishedV9WithoutRewritingChecksumsOrLeavingExhaustedJobsPending()
      throws Exception {
    String schema = "published_" + UUID.randomUUID().toString().replace("-", "");
    String url;
    String user;
    try (var metadata = source.getConnection()) {
      url = metadata.getMetaData().getURL();
      user = metadata.getMetaData().getUserName();
    }
    try (var connection = java.sql.DriverManager.getConnection(url, user, "identity_test")) {
      Flyway.configure()
          .dataSource(url, user, "identity_test")
          .schemas(schema)
          .defaultSchema(schema)
          .target("9")
          .load()
          .migrate();
      try (var sql = connection.createStatement()) {
        sql.execute(
            "insert into "
                + schema
                + ".ima_identities select * from public.ima_identities where id='"
                + reviewer.getId().value()
                + "'");
        sql.execute(
            "insert into "
                + schema
                + ".authentication_notification_jobs(id,identity_id,purpose,attempts,next_attempt_at)"
                + " values('"
                + UUID.randomUUID()
                + "','"
                + reviewer.getId().value()
                + "','PASSWORD_RESET',10,current_timestamp)");
      }
      try (var sql = connection.createStatement()) {
        sql.execute(
            "insert into "
                + schema
                + ".authentication_notification_jobs(id,identity_id,purpose,attempts,next_attempt_at,delivered_at)"
                + " values('"
                + UUID.randomUUID()
                + "','"
                + reviewer.getId().value()
                + "','ACCOUNT_ACTIVATION',10,current_timestamp,current_timestamp)");
      }
      var upgraded =
          Flyway.configure()
              .dataSource(url, user, "identity_test")
              .schemas(schema)
              .defaultSchema(schema)
              .load();
      upgraded.migrate();
      upgraded.validate();
      connection.setSchema(schema);
      try (var sql = connection.createStatement();
          var row =
              sql.executeQuery(
                  "select state,attempts,finished_at from authentication_notification_jobs where"
                      + " delivered_at is null")) {
        assertTrue(row.next());
        assertEquals("FAILED", row.getString(1));
        assertEquals(10, row.getInt(2));
        assertNotNull(row.getTimestamp(3));
      }
      try (var sql = connection.createStatement();
          var row =
              sql.executeQuery(
                  "select state,attempts from authentication_notification_jobs where delivered_at"
                      + " is not null")) {
        assertTrue(row.next());
        assertEquals("DELIVERED", row.getString(1));
        assertEquals(10, row.getInt(2));
      }
      try (var sql = connection.createStatement()) {
        sql.execute(
            "insert into authentication_notification_jobs(id,identity_id,purpose,next_attempt_at)"
                + " values('"
                + UUID.randomUUID()
                + "','"
                + reviewer.getId().value()
                + "','PASSWORD_RESET',current_timestamp)");
        try (var row = sql.executeQuery("select count(*) from authentication_notification_jobs")) {
          row.next();
          assertEquals(3, row.getInt(1));
        }
      }
    } finally {
      jdbc.execute("drop schema if exists " + schema + " cascade");
    }
  }
}
