package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import br.com.imarui.ima.identity.support.IdentityIntegrationSupport;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** A reproducible bounded measurement, not a hardware-independent latency assertion. */
class AuthorizationPerformanceIntegrationTest extends IdentityIntegrationSupport {
  @Autowired AuthorizationPolicyRepository policies;
  @Autowired EffectiveAuthorizationUseCases decisions;
  @Autowired RoleAdministrationUseCases roles;
  @Autowired ScopeRepository scopes;

  @Test void measureCurrentSnapshotHierarchyEvaluationAndSharedMutationWithPopulatedPolicy() throws Exception {
    var prefix="b"+UUID.randomUUID().toString().replace("-","")+"_";
    var scopePrefix=prefix.toUpperCase(Locale.ROOT)+"SC_";
    var transaction=new TransactionTemplate(transactions);
    transaction.executeWithoutResult(s -> {
      jdbc.update("delete from authentication_bootstrap where name='initial-root'");
      jdbc.update("insert into authentication_bootstrap values('initial-root',?,current_timestamp)",reviewer.getId().value());
      jdbc.update("delete from authorization_identity_roles where identity_id=?",reviewer.getId().value());
      rootAuthorization.assign(reviewer.getId().value());
      jdbc.update("""
          insert into ima_identities(id,kind,primary_email_id,primary_email,primary_email_type,primary_email_verified,
            primary_email_created_at,status,created_at,updated_at,version,nickname)
          select gen_random_uuid(),'SERVICE',gen_random_uuid(),?||n||'@example.test','PRIMARY',true,
            current_timestamp,'ACTIVE',current_timestamp,current_timestamp,0,?||n
          from generate_series(1,300) n
          """,prefix,prefix);
      jdbc.update("insert into ima_service_identities(id,name) select id,nickname from ima_identities where nickname like ?",prefix+"%");
      jdbc.update("""
          insert into authorization_scopes(id,code,name,status,parent_id,created_at,updated_at)
          select gen_random_uuid(),?||n,'Measurement scope '||n,'ACTIVE',?,current_timestamp,current_timestamp
          from generate_series(1,60) n
          """,scopePrefix,AuthorizationScope.PLATFORM_ID);
      jdbc.update("insert into authorization_roles(name,ownership,status) select ?||n,'GLOBAL','ACTIVE' from generate_series(1,40) n",prefix+"ROLE_");
      jdbc.update("""
          insert into authorization_role_permissions(role_id,permission_id)
          select r.id,p.id from authorization_roles r cross join authorization_permissions p
          where r.name like ? and p.code='IDENTITY_READ'
          """,prefix+"ROLE_%");
      jdbc.update("""
          with subjects as (select id,row_number() over(order by id)::int as n from ima_identities where nickname like ?)
          insert into authorization_identity_roles(identity_id,role_id,authorization_scope_id)
          select i.id,r.id,s.id from subjects i cross join generate_series(1,3) slot
          join authorization_roles r on r.name=?
          join authorization_scopes s on s.code=(?||(((i.n-1)*3+slot-1)%60+1))
          """,prefix+"%",prefix+"ROLE_1",scopePrefix);
    });
    actor(reviewer.getId()); var resource=scopes.tenantScope(tenant.getId().value()).orElseThrow();
    for(int i=0;i<3;i++) assertTrue(decisions.evaluate(reviewer.getId().value(),"IDENTITY_READ",resource).allowed());
    var read=new TransactionTemplate(transactions); read.setReadOnly(true); read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
    var loads=new ArrayList<Double>(); AuthorizationPolicyState state=null;
    for(int i=0;i<30;i++) { long start=System.nanoTime(); state=read.execute(s -> policies.current()); loads.add(ms(start)); }
    var treeTimes=new ArrayList<Double>(); ScopeHierarchy tree=null;
    for(int i=0;i<30;i++) { long start=System.nanoTime(); tree=new ScopeHierarchy(state.scopes()); treeTimes.add(ms(start)); }
    var evaluator=new EffectiveAuthorizationEvaluator(); var evaluation=new ArrayList<Double>();
    for(int i=0;i<100;i++) { long start=System.nanoTime(); assertTrue(evaluator.evaluate(state,reviewer.getId().value(),"IDENTITY_READ",resource,tree).allowed()); evaluation.add(ms(start)); }
    long role=jdbc.queryForObject("select id from authorization_roles where name=?",Long.class,prefix+"ROLE_1");
    long start=System.nanoTime();
    roles.revise(role,prefix+"REVISED",true,Set.of("IDENTITY_READ"),new PolicyChangeReason("Isolated performance measurement",UUID.randomUUID()));
    double mutation=ms(start);
    var report=new LinkedHashMap<String,Object>();
    report.put("scenario","isolated PostgreSQL; 300 additional identities, 60 scopes, 40 roles, 900 shared assignments");
    report.put("counts",Map.of("scopes",state.scopes().size(),"identities",state.activeIdentities().size(),"roles",state.roles().size(),"assignments",state.assignments().size(),"permissions",state.deprecatedPermissions().size()));
    report.put("snapshot_ms",stats(loads)); report.put("hierarchy_ms",stats(treeTimes)); report.put("evaluation_ms",stats(evaluation));
    report.put("shared_role_mutation_ms",mutation); report.put("samples",Map.of("snapshot",30,"hierarchy",30,"evaluation",100,"mutation",1));
    report.put("limitation","single host, bounded synthetic scenario; no production throughput/SLA claim; full snapshot and global mutation lock retained");
    Files.writeString(Path.of("target","authorization-benchmark.json"),json.writerWithDefaultPrettyPrinter().writeValueAsString(report));
    System.out.println("AUTHORIZATION_BENCHMARK "+json.writeValueAsString(report));
    jdbc.update("delete from authentication_bootstrap where name='initial-root'");
  }
  private double ms(long start) { return (System.nanoTime()-start)/1_000_000.0; }
  private Map<String,Double> stats(List<Double> values) {
    var sorted=values.stream().sorted().toList();
    return Map.of("p50",sorted.get(sorted.size()/2),"p95",sorted.get((int)Math.ceil(sorted.size()*0.95)-1),"max",sorted.get(sorted.size()-1));
  }
}
