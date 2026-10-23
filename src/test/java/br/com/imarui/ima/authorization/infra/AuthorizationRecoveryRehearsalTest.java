package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.security.*;
import java.sql.DriverManager;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/** Actual custom archive, restore to a separate disposable database, then upgrade and verify. */
class AuthorizationRecoveryRehearsalTest {
  @Test void backupPopulatedV20UpgradeFailAndRestoreWithoutLosingHistoryOrAuthority() throws Exception {
    try(var f=new OperationalPolicyFixture(10,4,"20")) {
      var keyPair=KeyPairGenerator.getInstance("RSA"); keyPair.initialize(2048);
      var publicKey=Base64.getEncoder().encodeToString(keyPair.generateKeyPair().getPublic().getEncoded());
      var credential=OperationalPolicyFixture.id("rehearsal-key");
      f.jdbc.update("insert into authentication_service_keys(id,service_code,public_key,fingerprint,active,created_at,expires_at) values(?,'iiam',?,?,true,current_timestamp,current_timestamp+interval '1 day')",credential,publicKey,"a".repeat(64));
      f.jdbc.update("insert into authentication_service_nonces values(?,?,current_timestamp+interval '1 minute')",credential,OperationalPolicyFixture.id("nonce"));
      f.jdbc.update("insert into authorization_roles__history(id,original_role_id,deleted_at,actor_identity_id,reason,correlation_id,role_state,permission_state,deny_state) values(?,900003,current_timestamp,?,'Synthetic deleted role',?,'{\"status\":\"ARCHIVED\",\"version\":1}'::jsonb,'[]'::jsonb,'[]'::jsonb)",OperationalPolicyFixture.id("deleted-role"),f.operator,OperationalPolicyFixture.id("correlation"));
      f.jdbc.update("insert into authorization_audit(id,actor_identity_id,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id) values(?,?,'REHEARSAL','ROLE','900002',?,current_timestamp,'Synthetic recovery evidence',?)",OperationalPolicyFixture.id("audit"),f.operator,f.left,OperationalPolicyFixture.id("correlation"));
      f.transaction.executeWithoutResult(s -> f.events.recordInTransaction("REHEARSAL",f.operator,"iiam","synthetic-object",f.leaf,"FIXTURE",OperationalPolicyFixture.id("correlation")));
      var before=fingerprint(f.jdbc);
      Path archive=Path.of("target","authorization-rehearsal.dump"); Files.createDirectories(archive.getParent());
      String restoreDb="auth_restore_"+UUID.randomUUID().toString().replace("-","");
      var endpoint=java.net.URI.create(f.url.substring(5));
      var environment=new HashMap<String,String>();
      environment.put("PGHOST",endpoint.getHost()); environment.put("PGPORT",Integer.toString(endpoint.getPort()));
      environment.put("PGUSER","identity_test"); environment.put("PGPASSWORD","identity_test");
      environment.put("PGDATABASE","identity_test"); environment.put("PGCONNECT_TIMEOUT","5");
      String bin=System.getenv("IIAM_PG_BIN");
      var dump=new ArrayList<String>();
      String container=System.getenv("IIAM_TEST_PG_CONTAINER");
      if(container!=null && !container.isBlank()) {
        // CI uses the matching client inside its PostgreSQL service; output is an archive, never credentials.
        command(List.of("docker","exec",container,"pg_dump","-U","identity_test","-d","identity_test","--format=custom","--schema="+f.schema,"--file=/tmp/authorization-rehearsal.dump"),environment);
        command(List.of("docker","cp",container+":/tmp/authorization-rehearsal.dump",archive.toAbsolutePath().toString()),environment);
      } else {
        dump.add(executable(bin,"pg_dump")); dump.addAll(List.of("--format=custom","--schema="+f.schema,"--file="+archive.toAbsolutePath()));
        command(dump,environment);
      }
      assertTrue(Files.size(archive)>0);
      String sha=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archive)));
      var flyway=Flyway.configure().dataSource(f.source).schemas(f.schema).defaultSchema(f.schema).load();
      flyway.migrate(); flyway.validate(); assertEquals(before,fingerprint(f.jdbc));
      if(container!=null && !container.isBlank()) {
        command(List.of("docker","cp",Path.of("scripts/authorization/preflight.sql").toAbsolutePath().toString(),container+":/tmp/authorization-preflight.sql"),environment);
        command(List.of("docker","exec","-e","PGOPTIONS=-c search_path="+f.schema,container,"psql","-X","-U","identity_test","-d","identity_test","--set=ON_ERROR_STOP=1","--file=/tmp/authorization-preflight.sql"),environment);
      } else {
        var preflightEnv=new HashMap<>(environment); preflightEnv.put("PGOPTIONS","-c search_path="+f.schema);
        command(List.of(executable(bin,"psql"),"-X","--set=ON_ERROR_STOP=1","--file="+Path.of("scripts/authorization/preflight.sql").toAbsolutePath()),preflightEnv);
      }
      // Simulated SQL failure must roll back both the policy write and its journal.
      assertThrows(org.springframework.dao.DataAccessException.class,() -> f.transaction.executeWithoutResult(s -> {
        f.jdbc.update("update authorization_identity_roles set active=false where role_id=900002");
        f.events.recordInTransaction("REHEARSAL_FAILURE",f.operator,"iiam",null,f.left,"ROLLBACK",UUID.randomUUID());
        f.jdbc.execute("select 1/0");
      }));
      assertEquals(before,fingerprint(f.jdbc));
      var admin=new JdbcTemplate(new DriverManagerDataSource(f.url,"identity_test","identity_test"));
      if(!restoreDb.matches("auth_restore_[0-9a-f]{32}")) throw new IllegalStateException("Unexpected restore target");
      admin.execute("create database "+restoreDb);
      try {
        var restoreEnv=new HashMap<>(environment); restoreEnv.put("PGDATABASE",restoreDb);
        if(container!=null && !container.isBlank()) {
          command(List.of("docker","exec",container,"pg_restore","-U","identity_test","-d",restoreDb,"--no-owner","--no-privileges","--exit-on-error","--single-transaction","/tmp/authorization-rehearsal.dump"),environment);
        } else {
          command(List.of(executable(bin,"pg_restore"),"--list",archive.toAbsolutePath().toString()),restoreEnv);
          command(List.of(executable(bin,"pg_restore"),"--no-owner","--no-privileges","--exit-on-error","--single-transaction", "--dbname="+restoreDb,archive.toAbsolutePath().toString()),restoreEnv);
        }
        String restoredUrl="jdbc:postgresql://"+endpoint.getHost()+":"+endpoint.getPort()+"/"+restoreDb;
        var restoredSource=new DriverManagerDataSource(restoredUrl+"?currentSchema="+f.schema,"identity_test","identity_test");
        var restored=new JdbcTemplate(restoredSource);
        assertEquals(before,fingerprint(restored));
        var old=Flyway.configure().dataSource(restoredSource).schemas(f.schema).defaultSchema(f.schema).target("20").load();
        old.validate();
        var upgrade=Flyway.configure().dataSource(restoredSource).schemas(f.schema).defaultSchema(f.schema).load();
        upgrade.migrate(); upgrade.validate(); assertEquals(before,fingerprint(restored));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> restored.update("delete from authorization_audit"));
        assertThrows(org.springframework.dao.DataAccessException.class,() -> restored.update("update ima_identities set status='PENDING' where id=?",f.operator));
        var report=Map.of("startingSchema","20","endingSchema",upgrade.info().current().getVersion().getVersion(),"archiveBytes",Files.size(archive),"sha256",sha,"serverVersion",f.jdbc.queryForObject("show server_version",String.class),"restoredToSeparateDatabase",true,"preserved",before,"controlledFailureRolledBack",true,"immutableAuditAndLastRootGuard",true,"limits","synthetic local database; not a production backup, disaster recovery SLA, encryption or remote storage validation");
        Files.writeString(Path.of("target/authorization-recovery-rehearsal.json"),f.json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
      } finally {
        admin.execute("drop database "+restoreDb+" with (force)");
      }
    }
  }
  private static Map<String,String> fingerprint(JdbcTemplate jdbc) {
    var result=new TreeMap<String,String>();
    for(String table:List.of("ima_identities","authorization_roles","authorization_roles__history","authorization_role_permissions","authorization_identity_roles","authorization_denies","authorization_permission_delegations","authentication_service_clients","authentication_service_keys","authentication_service_nonces","authorization_audit","authorization_security_events"))
      result.put(table,jdbc.queryForObject("select md5(coalesce(string_agg(to_jsonb(t)::text,',' order by to_jsonb(t)::text),'')) from "+table+" t",String.class));
    return result;
  }
  private static String executable(String bin,String name) { return bin==null?name:Path.of(bin,name+(System.getProperty("os.name").startsWith("Windows")?".exe":"")).toString(); }
  private static void command(List<String> command,Map<String,String> environment) throws Exception {
    var builder=new ProcessBuilder(command).redirectErrorStream(true); builder.environment().putAll(environment);
    Path output=Files.createTempFile(Path.of("target"),"rehearsal-tool-",".log"); builder.redirectOutput(output.toFile());
    var process=builder.start();
    if(!process.waitFor(60,TimeUnit.SECONDS)) { process.destroyForcibly(); throw new IllegalStateException("Recovery rehearsal tool timed out"); }
    assertEquals(0,process.exitValue(),() -> "Recovery rehearsal tool failed; inspect local "+output.getFileName());
  }
}
