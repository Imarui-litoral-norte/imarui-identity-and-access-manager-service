package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Operation;
import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Caller;
import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import br.com.imarui.ima.authorization.core.application.port.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
public class PdpUseCases {
  public record Request(String actorToken,UUID identityId,String permission,String resourceType,String resourceId,UUID expectedScopeId,UUID correlationId) {
    @Override public String toString() { return "PdpRequest[REDACTED]"; }
  }
  public record Result(UUID decisionId,boolean allowed,String reasonCode,UUID correlationId,java.time.Instant evaluatedAt) {}
  private record Context(Caller caller,AuthenticatedHumanContext.Human actor,AuthorizationPolicyState state,ScopeHierarchy tree) {}
  public static class Rejected extends SecurityException {
    public final UUID actor,correlation;
    public final String service;
    Rejected(UUID actor,String service,UUID correlation) { super("Caller or resource boundary mismatch."); this.actor=actor; this.service=service; this.correlation=correlation; }
  }
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(PdpUseCases.class);
  private final TechnicalCaller callers;
  private final AuthenticatedHumanContext humans;
  private final ServiceResourceRepository resources;
  private final AuthorizationPolicyRepository policies;
  private final SecurityAuditEvents events;
  private final MeterRegistry metrics;
  private final Clock clock;
  private final br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication technicalAuthentication;

  @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5)
  public Result decide(Request request) { return evaluate(request,prepare(request)); }

  @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5)
  public List<Result> batch(List<Request> requests) {
    if(requests==null || requests.isEmpty() || requests.size()>20 || requests.stream().anyMatch(Objects::isNull))
      throw new IllegalArgumentException("Batch must contain 1–20 decisions.");
    var first=requests.get(0);
    if(requests.stream().anyMatch(r -> !Objects.equals(first.actorToken(),r.actorToken())
        || !Objects.equals(first.identityId(),r.identityId()) || !Objects.equals(first.correlationId(),r.correlationId())))
      throw new IllegalArgumentException("Batch must use one signed Actor/session and correlation.");
    var context=prepare(first);
    return requests.stream().map(r -> evaluate(r,context)).toList();
  }

  private Context prepare(Request request) {
    var caller=technicalAuthentication.current(callers.require());
    if(!caller.operations().contains(Operation.PDP_DECIDE) || request.correlationId()==null
        || !request.correlationId().equals(caller.correlationId())) throw new SecurityException("PDP caller/correlation mismatch.");
    var actor=humans.verify(request.actorToken(),request.identityId());
    var state=policies.current();
    return new Context(caller,actor,state,new ScopeHierarchy(state.scopes()));
  }

  private Result evaluate(Request request,Context context) {
    var sample=io.micrometer.core.instrument.Timer.start(metrics); String outcome="ERROR";
    try {
      var caller=context.caller(); var actor=context.actor(); var state=context.state(); var tree=context.tree();
      UUID correlation=request.correlationId();
      UUID scope=resources.resolve(caller.serviceCode(),request.resourceType(),request.resourceId());
      if(request.expectedScopeId()!=null && !request.expectedScopeId().equals(scope)
          || caller.scopeRoots().stream().noneMatch(root -> tree.active(root) && tree.covers(root,scope))
          || !caller.serviceCode().equals(resources.permissionOwner(request.permission())))
        throw new Rejected(actor.identityId(),caller.serviceCode(),correlation);
      var decision=new EffectiveAuthorizationEvaluator().evaluate(state,actor.identityId(),request.permission(),scope,tree);
      var id=UUID.randomUUID();
      // Same connection/transaction: audit failure prevents ALLOW without nested-pool starvation.
      events.decision(id,actor.identityId(),caller.serviceCode(),request.resourceType(),request.resourceId(),scope,request.permission(),decision.reason().name(),correlation);
      outcome=decision.allowed()?"ALLOW":"DENY";
      String committedOutcome=outcome;
      org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
        public void afterCommit() {
          metrics.counter("iiam.pdp.decisions","reason",decision.reason().name()).increment();
          log.info("authorization_decision decisionId={} correlationId={} outcome={} reason={}",id,correlation,committedOutcome,decision.reason());
        }
      });
      // Common response omits grant/deny/Role provenance; protected diagnostics are separate.
      return new Result(id,decision.allowed(),decision.allowed()?"ALLOW":"DENY",correlation,clock.instant());
    } finally { sample.stop(metrics.timer("iiam.pdp.duration","outcome",outcome)); }
  }
}
