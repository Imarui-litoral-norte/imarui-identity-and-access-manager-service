package br.com.imarui.ima.identity.infra.persistence;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import java.util.UUID;

class GlobalIdentifiersMigrationTest extends IdentityIntegrationSupport {
  @Autowired DataSource dataSource;
  @ParameterizedTest @ValueSource(strings={"NICKNAME","EMAIL"})
  void refusesAmbiguousV25WithoutChangingExistingRows(String collision) throws Exception {
    String schema="identifier_upgrade_"+UUID.randomUUID().toString().replace("-","");
    String url;try(var c=dataSource.getConnection()){url=c.getMetaData().getURL();}
    var flyway=Flyway.configure().dataSource(url,"identity_test","identity_test").schemas(schema).defaultSchema(schema).target("25").load();
    try {
      flyway.migrate();
      var db=new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema,"identity_test","identity_test"));
      for(String kind:new String[]{"PERSON","SERVICE"}) {
        db.update("insert into ima_identities(id,kind,nickname,status,created_at,updated_at,primary_email_id,primary_email,primary_email_type,primary_email_verified,primary_email_created_at,version) values(?,?,?,'PENDING',current_timestamp,current_timestamp,?,?,'PRIMARY',false,current_timestamp,0)",UUID.randomUUID(),kind,collision.equals("NICKNAME")?"same-login":kind.toLowerCase()+"-login",UUID.randomUUID(),collision.equals("EMAIL")?"same@example.test":kind.toLowerCase()+"@example.test");
      }
      assertThrows(org.flywaydb.core.api.FlywayException.class,()->Flyway.configure().dataSource(url,"identity_test","identity_test").schemas(schema).defaultSchema(schema).load().migrate());
      assertEquals(2,db.queryForObject("select count(*) from ima_identities",Integer.class));
      assertEquals(0,db.queryForObject("select count(*) from pg_indexes where schemaname=? and indexname='uk_identity_nickname_global'",Integer.class,schema));
    } finally { jdbc.execute("drop schema if exists "+schema+" cascade"); }
  }
}
