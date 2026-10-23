package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.infra.persistence.JdbcSecurityAuditRepository;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AuthorizationAuditCapacityTest {
  @Test void measureExistingIndexesPagedUnionAndGrowthWithoutDeletingHistory() throws Exception {
    try(var f=new OperationalPolicyFixture(10,100)) {
      f.transaction.executeWithoutResult(s -> {
        f.jdbc.update("""
            insert into authorization_security_events(id,event,service_code,resource_id,scope_id,occurred_at,reason_code,correlation_id)
            select gen_random_uuid(),'PDP_DECISION','iiam','object-'||n,s.id,current_timestamp-n*interval '1 second',
            'ALLOWED',md5('correlation-'||n)::uuid
            from generate_series(1,20000) n join authorization_scopes s on s.code='OPS_'||(n%100+1)
            """);
        f.jdbc.update("""
            insert into authorization_audit(id,service_code,action,resource_type,resource_id,scope_id,occurred_at,reason,correlation_id)
            select gen_random_uuid(),'iiam','FIXTURE_MUTATION','ROLE','role-'||n,s.id,current_timestamp-n*interval '1 second',
            'Synthetic capacity measurement',md5('correlation-'||n)::uuid
            from generate_series(1,20000) n join authorization_scopes s on s.code='OPS_'||(n%100+1)
            """);
      });
      f.jdbc.execute("analyze authorization_security_events"); f.jdbc.execute("analyze authorization_audit");
      var audits=new JdbcSecurityAuditRepository(f.jdbc,f.json); var durations=new ArrayList<Double>();
      for(int i=0;i<100;i++) {
        long began=System.nanoTime(); var page=f.read.execute(s -> audits.query(Set.of(f.leaf),false,null,null,0,25));
        durations.add((System.nanoTime()-began)/1_000_000.0);
        assertEquals(400,page.total()); assertEquals(25,page.items().size()); assertTrue(page.items().stream().allMatch(e -> f.leaf.equals(e.scopeId())));
      }
      var correlation=UUID.nameUUIDFromBytes("unused-correlation".getBytes(java.nio.charset.StandardCharsets.UTF_8));
      var empty=f.read.execute(s -> audits.query(Set.of(f.leaf),false,null,correlation,0,25)); assertEquals(0,empty.total());
      String scopePlan=plan(f,"select id from authorization_security_events where scope_id='"+f.leaf+"' order by occurred_at desc,id limit 25");
      String correlationPlan=plan(f,"select id from authorization_security_events where correlation_id=md5('correlation-100')::uuid");
      assertTrue(scopePlan.contains("ix_security_event_scope_time"),scopePlan);
      assertTrue(correlationPlan.contains("ix_security_event_correlation"),correlationPlan);
      var storage=new LinkedHashMap<String,Object>();
      for(var table:List.of("authorization_audit","authorization_security_events")) storage.put(table,
          Map.of("rows",f.jdbc.queryForObject("select count(*) from "+table,Long.class),"totalBytesIncludingIndexes",f.jdbc.queryForObject("select pg_total_relation_size(?::regclass)",Long.class,table),"meanLogicalRowBytes",f.jdbc.queryForObject("select avg(pg_column_size(t)) from "+table+" t",Double.class)));
      assertThrows(org.springframework.dao.DataAccessException.class,() -> f.jdbc.update("delete from authorization_security_events where id=(select id from authorization_security_events limit 1)"));
      assertThrows(org.springframework.dao.DataAccessException.class,() -> f.jdbc.update("update authorization_audit set reason='modified' where id=(select id from authorization_audit limit 1)"));
      var sorted=durations.stream().sorted().toList();
      var report=Map.of("rows",40000,"scopes",100,"serverVersion",f.jdbc.queryForObject("show server_version",String.class),"pagedScopedUnion_ms",Map.of("samples",100,"p50",sorted.get(49),"p95",sorted.get(94),"p99",sorted.get(98)),"storage",storage,"scopePlan",f.json.readTree(scopePlan),"correlationPlan",f.json.readTree(correlationPlan),"otherPlans",Map.of("period",f.json.readTree(plan(f,"select id from authorization_security_events where occurred_at>current_timestamp-interval '100 seconds' order by occurred_at desc,id limit 25")),"resource",f.json.readTree(plan(f,"select id from authorization_security_events where service_code='iiam' and resource_id='object-100' order by occurred_at desc limit 25"))),"limits","synthetic short event payloads; scoped API includes count and OFFSET, deep pages/wide scope remain costly; no retention policy approved and no history deleted");
      Files.writeString(Path.of("target/authorization-audit-capacity.json"),f.json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    }
  }
  private String plan(OperationalPolicyFixture f,String sql) { return f.jdbc.queryForObject("explain (analyze,buffers,format json) "+sql,String.class); }
}
