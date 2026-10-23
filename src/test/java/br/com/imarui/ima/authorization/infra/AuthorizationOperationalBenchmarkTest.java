package br.com.imarui.ima.authorization.infra;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.port.AuthenticatedHumanContext;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.application.usecase.*;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.infra.persistence.JdbcServiceResourceRepository;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** Fixed dimensions and fresh schemas make repeated runs comparable. No latency SLA assertion. */
class AuthorizationOperationalBenchmarkTest {
  @Test void measureControlledScaleConcurrentPdpMutationsAndRevocation() throws Exception {
    var scenarios=new ArrayList<Object>();
    for(int[] dimensions:new int[][]{{50,30},{150,80},{300,160}}) {
      try(var f=new OperationalPolicyFixture(dimensions[0],dimensions[1])) {
        var guard=new AuthorizationMutationGuard(() -> f.operator,f.policies,f.mutations);
        var roles=new RoleAdministrationUseCases(guard,f.mutations,f.policies,Clock.systemUTC());
        var scopes=new ScopeAdministrationUseCases(f.scopes,guard,f.mutations,f.policies,Clock.systemUTC());
        var state=f.read.execute(s -> f.policies.current()); var tree=new ScopeHierarchy(state.scopes());
        var evaluator=new EffectiveAuthorizationEvaluator();
        var reason=new PolicyChangeReason("Isolated operational benchmark",OperationalPolicyFixture.id("correlation"));
        var results=new LinkedHashMap<String,Object>();
        results.put("counts",Map.of("scopes",state.scopes().size(),"identities",state.activeIdentities().size(),"assignments",state.assignments().size(),"roles",state.roles().size(),"permissions",state.deprecatedPermissions().size(),"denies",state.denies().size(),"delegations",state.delegations().size()));
        results.put("snapshot_ms",measure(100,() -> f.read.execute(s -> f.policies.current())));
        results.put("hierarchy_ms",measure(100,() -> new ScopeHierarchy(state.scopes())));
        results.put("evaluation_ms",measure(1000,() -> evaluator.evaluate(state,f.subject,"IDENTITY_READ",f.leaf,tree)));
        results.put("shared_role_mutation_ms",measure(3,() -> f.transaction.execute(s -> roles.revise(900002,"OPS_SHARED",true,Set.of("IDENTITY_READ"),reason))));
        results.put("reparent_ms",measure(4,() -> f.transaction.execute(s -> {
          var previous=f.scopes.find(f.leaf).orElseThrow();
          return scopes.revise(f.leaf,previous.name(),previous.status(),previous.parentId().equals(f.left)?f.right:f.left,reason);
        })));
        var caller=new TechnicalServiceAuthentication.Caller(OperationalPolicyFixture.id("client"),"iiam",OperationalPolicyFixture.id("credential"),Set.of(TechnicalServiceAuthentication.Operation.PDP_DECIDE),Set.of(AuthorizationScope.PLATFORM_ID),reason.correlationId());
        var technical=mock(TechnicalServiceAuthentication.class); when(technical.current(caller)).thenReturn(caller);
        var pdp=new PdpUseCases(() -> caller,(token,identity) -> new AuthenticatedHumanContext.Human(identity,OperationalPolicyFixture.id("session")),new JdbcServiceResourceRepository(f.jdbc),f.policies,f.events,f.metrics,Clock.systemUTC(),technical);
        var request=new PdpUseCases.Request("synthetic-prevalidated-context",f.subject,"IDENTITY_READ","OBJECT","synthetic-object",f.leaf,reason.correlationId());
        var pool=Executors.newFixedThreadPool(5); var times=new ConcurrentLinkedQueue<Double>();
        try {
          var start=new CountDownLatch(1); var jobs=new ArrayList<Future<?>>();
          for(int worker=0;worker<4;worker++) jobs.add(pool.submit(() -> {
            start.await(); for(int i=0;i<30;i++) { long began=System.nanoTime(); assertTrue(f.decision.execute(s -> pdp.decide(request)).allowed()); times.add(ms(began)); } return null;
          }));
          jobs.add(pool.submit(() -> { start.await(); for(int i=0;i<3;i++) f.transaction.execute(s -> roles.revise(900002,"OPS_SHARED",true,Set.of("IDENTITY_READ"),reason)); return null; }));
          long began=System.nanoTime(); start.countDown(); for(var job:jobs) job.get(60,TimeUnit.SECONDS);
          results.put("concurrent_pdp_ms",stats(new ArrayList<>(times))); results.put("observed_pdp_per_second",120/(ms(began)/1000));
        } finally { pool.shutdownNow(); }
        results.put("permission_revocation_ms",measure(1,() -> f.transaction.execute(s -> roles.revise(900002,"OPS_SHARED",true,Set.of(),reason))));
        assertFalse(f.decision.execute(s -> pdp.decide(request)).allowed(),"A new revision after commit must see revocation");
        assertEquals(121,f.jdbc.queryForObject("select count(*) from authorization_security_events where event='PDP_DECISION'",Integer.class));
        scenarios.add(results);
      }
    }
    var report=new LinkedHashMap<String,Object>();
    report.put("java",System.getProperty("java.version")); report.put("os",System.getProperty("os.name")); report.put("processors",Runtime.getRuntime().availableProcessors());
    report.put("methodology","fresh schema per scenario; fixed dimensions; warmup 3; 100 snapshot/hierarchy, 1000 evaluator, 120 PDP across 4 readers plus 3 concurrent writes; 3 shared-role/4 reparent/1 revoke samples; p99 only for n>=100");
    report.put("limits","local synthetic policy pipeline with real PostgreSQL/snapshot/evaluator/audit/transactions; human and technical authentication prevalidated fixture ports, excludes HTTP/RSA/network; no production SLA; global mutation lock retained");
    report.put("scenarios",scenarios);
    Files.createDirectories(Path.of("target"));
    Files.writeString(Path.of("target/authorization-operational-benchmark.json"),new tools.jackson.databind.json.JsonMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report));
  }
  private Map<String,Object> measure(int samples,Supplier<?> operation) {
    // Do not warm up mutations: each one is a real audited state change.
    if(samples>=100) for(int i=0;i<3;i++) operation.get();
    var values=new ArrayList<Double>();
    for(int i=0;i<samples;i++) { long began=System.nanoTime(); operation.get(); values.add(ms(began)); }
    return stats(values);
  }
  private Map<String,Object> stats(List<Double> values) {
    var sorted=values.stream().sorted().toList(); var result=new LinkedHashMap<String,Object>(); int n=sorted.size();
    result.put("samples",n); result.put("p50",sorted.get((int)Math.ceil(n*.5)-1)); result.put("p95",sorted.get((int)Math.ceil(n*.95)-1));
    if(n>=100) result.put("p99",sorted.get((int)Math.ceil(n*.99)-1)); result.put("max",sorted.get(n-1)); return result;
  }
  private static double ms(long began) { return (System.nanoTime()-began)/1_000_000.0; }
}
