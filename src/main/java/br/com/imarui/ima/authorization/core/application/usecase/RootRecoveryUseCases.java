package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authentication.api.module.AdministrativeAuthentication;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.port.*;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.NOT_SUPPORTED)
public class RootRecoveryUseCases {
  public record Approval(UUID id,UUID initiatorId,UUID recipientId,UUID scopeId,java.time.Instant expiresAt,String status,
      Set<String> recoveryCapabilities,boolean preservesExistingDenials) {}
  public static final Set<String> RECOVERY=RootRecoveryEnvelope.CAPABILITIES;
  private final AuthorizationActor actors;
  private final CurrentHumanSession sessions;
  private final AdministrativeAuthentication authentication;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final RootRecoveryRepository recovery;
  private final Clock clock;
  private final PlatformTransactionManager transactions;
  private final io.micrometer.core.instrument.MeterRegistry metrics;
  private static final org.slf4j.Logger log=org.slf4j.LoggerFactory.getLogger(RootRecoveryUseCases.class);

  public Approval initiate(UUID recipient,UUID scope,String password,PolicyChangeReason reason) {
    var actor=actors.requireIdentityId(); var sid=sessions.requireSessionId();
    authentication.reauthenticate(actor,password); // before taking the policy lock, preserving Authentication lock order
    var transaction=new TransactionTemplate(transactions); transaction.setTimeout(10);
    return transaction.execute(s -> initiateAuthorized(actor,sid,recipient,scope,reason));
  }
  private Approval initiateAuthorized(UUID actor,UUID sid,UUID recipient,UUID scope,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_ROOT_RECOVERY",scope,reason);
    rootAssignment(context.before(),actor,scope);
    if(actor.equals(recipient) || !context.before().activeIdentities().contains(recipient))
      throw new SecurityException("A distinct active recipient must approve the transfer.");
    if(!authentication.sessionActive(actor,sid)) throw new SecurityException("Initiator session is no longer valid.");
    var now=clock.instant(); var transfer=new RootTransfer(UUID.randomUUID(),actor,sid,recipient,scope,
        reason.reason(),reason.correlationId(),now,now.plusSeconds(600),null);
    recovery.create(transfer);
    mutations.audit(actor,"ROOT_TRANSFER_REQUESTED","ROOT_TRANSFER",transfer.id().toString(),scope,reason,null,transfer,now);
    signal("REQUESTED",transfer);
    return view(transfer);
  }

  public Approval confirm(UUID id,String password) {
    var recipient=actors.requireIdentityId(); var sid=sessions.requireSessionId();
    authentication.reauthenticate(recipient,password);
    var transaction=new TransactionTemplate(transactions); transaction.setTimeout(10);
    return transaction.execute(s -> confirmAuthorized(id,recipient,sid));
  }
  private Approval confirmAuthorized(UUID id,UUID recipient,UUID sid) {
    mutations.lock();
    var transfer=recovery.lock(id).orElseThrow(() -> new NoSuchElementException("Recovery approval not found."));
    if(!recipient.equals(transfer.recipientId())) throw new SecurityException("Only the designated recipient can confirm.");
    if(transfer.confirmedAt()!=null) return view(transfer);
    var now=clock.instant();
    if(!transfer.pending(now)) throw new IllegalStateException("Recovery approval expired.");
    var state=policies.current(); var initiator=transfer.initiatorId(); var scope=transfer.scopeId();
    if(!state.activeIdentities().contains(recipient) || !state.activeIdentities().contains(initiator)
        || !authentication.sessionActive(recipient,sid)
        || !authentication.sessionActive(initiator,transfer.initiatorSessionId()))
      throw new SecurityException("Both approved human contexts must remain valid.");
    guard.require(state,initiator,"AUTHORIZATION_ROOT_RECOVERY",scope);
    var previous=rootAssignment(state,initiator,scope); var root=state.roles().get(previous.roleId());
    var rights=state.rolePermissions().getOrDefault(root.id(),Set.of());
    // A Role can deny a Permission it does not grant. Shedding ROOT must not
    // reveal another Role's previously blocked grant outside ROOT's composition.
    var touched=new HashSet<>(rights);
    state.denies().stream().filter(d -> d.active() && Objects.equals(d.roleId(),root.id()))
        .forEach(d -> touched.add(d.permission()));
    var impacted=guard.descendants(state,scope);
    var hierarchy=new ScopeHierarchy(state.scopes());
    if(state.assignments().stream().anyMatch(a -> a.active() && a.identityId().equals(recipient) && a.roleId()==root.id() && a.scopeId().equals(scope)))
      throw new IllegalStateException("Recipient already owns this recovery boundary.");
    var prior=state.assignments().stream().filter(a -> a.identityId().equals(recipient) && a.roleId()==root.id() && a.scopeId().equals(scope)).findFirst().orElse(null);
    var assignment=new RoleAssignment(prior==null?UUID.randomUUID():prior.id(),recipient,root.id(),scope,true,
        prior==null?now:prior.createdAt(),now,prior==null?0:prior.version()+1);
    mutations.saveAssignment(assignment);
    var reason=new PolicyChangeReason(transfer.reason(),transfer.correlationId());
    var evaluator=new EffectiveAuthorizationEvaluator();
    for(var code:rights) {
      if(!evaluator.mayDelegate(state,initiator,code,scope,true,hierarchy)) continue;
      var existing=state.delegations().stream().filter(d -> d.active() && d.identityId().equals(recipient) && d.scopeId().equals(scope) && d.permission().equals(code)).findFirst().orElse(null);
      var delegation=new PermissionDelegation(existing==null?UUID.randomUUID():existing.id(),recipient,code,scope,true,true,
          reason.reason(),existing==null?now:existing.createdAt(),now,existing==null?0:existing.version()+1);
      mutations.saveDelegation(delegation);
      mutations.audit(initiator,"ROOT_GOVERNANCE_TRANSFERRED","PERMISSION_DELEGATION",delegation.id().toString(),scope,reason,existing,delegation,now);
    }
    mutations.saveAssignment(new RoleAssignment(previous.id(),initiator,previous.roleId(),scope,false,previous.createdAt(),now,previous.version()+1));
    for(var d:state.delegations()) if(d.active() && d.identityId().equals(initiator) && d.scopeId().equals(scope) && rights.contains(d.permission())) {
      var revoked=new PermissionDelegation(d.id(),d.identityId(),d.permission(),d.scopeId(),d.redelegable(),false,reason.reason(),d.createdAt(),now,d.version()+1);
      mutations.saveDelegation(revoked);
      mutations.audit(initiator,"ROOT_GOVERNANCE_REVOKED","PERMISSION_DELEGATION",d.id().toString(),scope,reason,d,revoked,now);
    }
    preserveDenials(transfer,state,recipient,impacted,touched,reason);
    preserveDenials(transfer,state,initiator,impacted,touched,reason);
    guard.assertGains(new AuthorizationMutationGuard.Context(initiator,state),policies.current(),impacted,touched,Map.of());
    recovery.confirm(id,now);
    mutations.audit(recipient,"ROOT_TRANSFER_CONFIRMED","ROOT_TRANSFER",id.toString(),scope,reason,transfer,
        Map.of("assignment",assignment,"initiator",initiator,"recipient",recipient,"confirmedAt",now),now);
    signal("CONFIRMED",transfer);
    return new Approval(id,initiator,recipient,scope,transfer.expiresAt(),"COMPLETED",RECOVERY,true);
  }

  public Approval inspect(UUID id) {
    var actor=actors.requireIdentityId(); sessions.requireSessionId();
    var transaction=new TransactionTemplate(transactions); transaction.setTimeout(5);
    return transaction.execute(s -> {
      var transfer=recovery.lock(id).orElseThrow(() -> new NoSuchElementException("Recovery approval not found."));
      if(!actor.equals(transfer.initiatorId()) && !actor.equals(transfer.recipientId())) throw new SecurityException("Recovery approval is private to its two participants.");
      return view(transfer);
    });
  }

  private void preserveDenials(RootTransfer transfer,AuthorizationPolicyState before,UUID subject,Set<UUID> impacted,
      Set<String> rights,PolicyChangeReason reason) {
    var evaluator=new EffectiveAuthorizationEvaluator();
    var previous=guard.potentialImpactState(before);
    var current=guard.potentialImpactState(policies.current());
    var tree=new ScopeHierarchy(current.scopes());
    var ordered=impacted.stream().sorted(Comparator.comparingInt((UUID id) -> tree.ancestors(id).size()).thenComparing(UUID::toString)).toList();
    for(var scope:ordered) for(var code:rights.stream().sorted().toList()) {
      if(evaluator.evaluate(previous,subject,code,scope,tree).allowed()
          || !evaluator.evaluate(current,subject,code,scope,tree).allowed()
          || evaluator.evaluate(previous,transfer.initiatorId(),code,scope,tree).reason()!=EffectiveAuthorizationEvaluator.Reason.EXPLICIT_DENY) continue;
      var now=clock.instant();
      var deny=new DenyRule(UUID.randomUUID(),code,scope,subject,null,true,reason.reason(),now,now,0);
      mutations.saveDeny(deny);
      mutations.audit(transfer.initiatorId(),"ROOT_CONSTRAINT_PRESERVED","DENY",deny.id().toString(),scope,reason,
          Map.of("transferId",transfer.id(),"sourceIdentity",transfer.initiatorId(),"previousReason","EXPLICIT_DENY"),deny,now);
      var denies=new ArrayList<>(current.denies()); denies.add(deny);
      current=new AuthorizationPolicyState(current.scopes(),current.roles(),current.rolePermissions(),current.assignments(),
          denies,current.delegations(),current.deprecatedPermissions(),current.tenantScopes(),current.activeIdentities(),current.activeTenants());
    }
  }

  private RoleAssignment rootAssignment(AuthorizationPolicyState state,UUID identity,UUID scope) {
    var tree=new ScopeHierarchy(state.scopes());
    return state.assignments().stream().filter(a -> a.active() && a.identityId().equals(identity) && a.scopeId().equals(scope)
        && state.roles().get(a.roleId()).name().equals("ROOT") && state.roles().get(a.roleId()).active() && tree.active(scope))
        .findFirst().orElseThrow(() -> new SecurityException("Existing ROOT governance on this exact boundary is required."));
  }
  private Approval view(RootTransfer t) { return new Approval(t.id(),t.initiatorId(),t.recipientId(),t.scopeId(),t.expiresAt(),
      t.confirmedAt()!=null?"COMPLETED":t.pending(clock.instant())?"PENDING":"EXPIRED",RECOVERY,true); }

  private void signal(String event,RootTransfer transfer) {
    org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
        new org.springframework.transaction.support.TransactionSynchronization() {
          public void afterCommit() {
            metrics.counter("iiam.root.transfers","event",event).increment();
            log.warn("authorization_root_transfer event={} transferId={} scopeId={} correlationId={}",
                event,transfer.id(),transfer.scopeId(),transfer.correlationId());
          }
        });
  }
}
