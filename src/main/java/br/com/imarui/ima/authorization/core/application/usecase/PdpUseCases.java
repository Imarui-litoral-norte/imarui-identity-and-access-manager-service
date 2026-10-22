package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication.Operation;
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
  private final TechnicalCaller callers;
  private final AuthenticatedHumanContext humans;
  private final ServiceResourceRepository resources;
  private final AuthorizationPolicyRepository policies;
  private final SecurityAuditEvents events;
  private final MeterRegistry metrics;
  private final Clock clock;

  @Transactional(propagation=Propagation.REQUIRES_NEW,isolation=Isolation.REPEATABLE_READ,timeout=5)
  public Result decide(Request request) {
    var sample=io.micrometer.core.instrument.Timer.start(metrics); String outcome="ERROR";
    try {
      var caller=callers.require();
      if(!caller.operations().contains(Operation.PDP_DECIDE)) throw new SecurityException("PDP operation denied.");
      var actor=humans.verify(request.actorToken(),request.identityId());
      UUID correlation=Objects.requireNonNull(request.correlationId(),"Correlation ID required.");
      UUID scope=resources.resolve(caller.serviceCode(),request.resourceType(),request.resourceId());
      var state=policies.current(); var tree=new ScopeHierarchy(state.scopes());
      if(request.expectedScopeId()!=null && !request.expectedScopeId().equals(scope)
          || caller.scopeRoots().stream().noneMatch(root -> tree.active(root) && tree.covers(root,scope))
          || !caller.serviceCode().equals(resources.permissionOwner(request.permission()))) {
        events.record("PDP_REJECTED",actor.identityId(),caller.serviceCode(),null,null,"CALLER_RESOURCE_MISMATCH",correlation);
        throw new SecurityException("Caller, Permission or resource boundary mismatch.");
      }
      var decision=new EffectiveAuthorizationEvaluator().evaluate(state,actor.identityId(),request.permission(),scope,tree);
      var id=UUID.randomUUID(); outcome=decision.allowed()?"ALLOW":"DENY";
      // Failure of durable audit prevents a positive decision from reaching the consumer.
      events.record("PDP_DECISION",actor.identityId(),caller.serviceCode(),id.toString(),scope,decision.reason().name(),correlation);
      metrics.counter("iiam.pdp.decisions","reason",decision.reason().name()).increment();
      // Common response omits grant/deny/Role provenance; protected diagnostics are separate.
      return new Result(id,decision.allowed(),decision.allowed()?"ALLOW":"DENY",correlation,clock.instant());
    } finally { sample.stop(metrics.timer("iiam.pdp.duration","outcome",outcome)); }
  }
}
