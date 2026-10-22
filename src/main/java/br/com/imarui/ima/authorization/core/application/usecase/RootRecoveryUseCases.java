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
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(timeout=10)
public class RootRecoveryUseCases {
  public record Approval(UUID id,UUID initiatorId,UUID recipientId,UUID scopeId,java.time.Instant expiresAt,String status) {}
  public static final Set<String> RECOVERY=Set.of("AUTHORIZATION_SCOPE_MANAGE","AUTHORIZATION_ROLE_MANAGE",
      "AUTHORIZATION_ASSIGNMENT_MANAGE","AUTHORIZATION_DENY_MANAGE","AUTHORIZATION_DELEGATION_MANAGE",
      "AUTHORIZATION_CATALOG_MANAGE","AUTHORIZATION_ROOT_RECOVERY");
  private final AuthorizationActor actors;
  private final CurrentHumanSession sessions;
  private final AdministrativeAuthentication authentication;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final RootRecoveryRepository recovery;
  private final Clock clock;

  public Approval initiate(UUID recipient,UUID scope,String password,PolicyChangeReason reason) {
    var actor=actors.requireIdentityId(); var sid=sessions.requireSessionId();
    authentication.reauthenticate(actor,password); // before taking the policy lock, preserving Authentication lock order
    var context=guard.begin("AUTHORIZATION_ROOT_RECOVERY",scope,reason);
    rootAssignment(context.before(),actor,scope);
    if(actor.equals(recipient) || !context.before().activeIdentities().contains(recipient))
      throw new SecurityException("A distinct active recipient must approve the transfer.");
    if(!authentication.sessionActive(actor,sid)) throw new SecurityException("Initiator session is no longer valid.");
    var now=clock.instant(); var transfer=new RootTransfer(UUID.randomUUID(),actor,sid,recipient,scope,
        reason.reason(),reason.correlationId(),now,now.plusSeconds(600),null);
    recovery.create(transfer);
    mutations.audit(actor,"ROOT_TRANSFER_REQUESTED","ROOT_TRANSFER",transfer.id().toString(),scope,reason,null,transfer,now);
    return view(transfer);
  }

  public Approval confirm(UUID id,String password) {
    var recipient=actors.requireIdentityId(); sessions.requireSessionId();
    authentication.reauthenticate(recipient,password);
    mutations.lock();
    var transfer=recovery.lock(id).orElseThrow(() -> new NoSuchElementException("Recovery approval not found."));
    if(!recipient.equals(transfer.recipientId())) throw new SecurityException("Only the designated recipient can confirm.");
    if(transfer.confirmedAt()!=null) return view(transfer);
    var now=clock.instant();
    if(!transfer.pending(now)) throw new IllegalStateException("Recovery approval expired.");
    var state=policies.current(); var initiator=transfer.initiatorId(); var scope=transfer.scopeId();
    if(!state.activeIdentities().contains(recipient) || !state.activeIdentities().contains(initiator)
        || !authentication.sessionActive(initiator,transfer.initiatorSessionId()))
      throw new SecurityException("Both approved human contexts must remain valid.");
    guard.require(state,initiator,"AUTHORIZATION_ROOT_RECOVERY",scope);
    var previous=rootAssignment(state,initiator,scope); var root=state.roles().get(previous.roleId());
    var rights=state.rolePermissions().getOrDefault(root.id(),Set.of());
    var impacted=guard.descendants(state,scope);
    var hierarchy=new ScopeHierarchy(state.scopes());
    for(var target:impacted) for(var code:rights) guard.delegate(state,initiator,code,target,true,hierarchy);
    if(state.assignments().stream().anyMatch(a -> a.active() && a.identityId().equals(recipient) && a.roleId()==root.id() && a.scopeId().equals(scope)))
      throw new IllegalStateException("Recipient already owns this recovery boundary.");
    var prior=state.assignments().stream().filter(a -> a.identityId().equals(recipient) && a.roleId()==root.id() && a.scopeId().equals(scope)).findFirst().orElse(null);
    var assignment=new RoleAssignment(prior==null?UUID.randomUUID():prior.id(),recipient,root.id(),scope,true,
        prior==null?now:prior.createdAt(),now,prior==null?0:prior.version()+1);
    mutations.saveAssignment(assignment);
    var reason=new PolicyChangeReason(transfer.reason(),transfer.correlationId());
    for(var code:rights) {
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
    guard.assertGains(new AuthorizationMutationGuard.Context(initiator,state),policies.current(),impacted,rights,Map.of());
    recovery.confirm(id,now);
    mutations.audit(recipient,"ROOT_TRANSFER_CONFIRMED","ROOT_TRANSFER",id.toString(),scope,reason,transfer,
        Map.of("assignment",assignment,"initiator",initiator,"recipient",recipient,"confirmedAt",now),now);
    return new Approval(id,initiator,recipient,scope,transfer.expiresAt(),"COMPLETED");
  }

  private RoleAssignment rootAssignment(AuthorizationPolicyState state,UUID identity,UUID scope) {
    var tree=new ScopeHierarchy(state.scopes());
    return state.assignments().stream().filter(a -> a.active() && a.identityId().equals(identity) && a.scopeId().equals(scope)
        && state.roles().get(a.roleId()).name().equals("ROOT") && state.roles().get(a.roleId()).active() && tree.active(scope))
        .findFirst().orElseThrow(() -> new SecurityException("Existing ROOT governance on this exact boundary is required."));
  }
  private Approval view(RootTransfer t) { return new Approval(t.id(),t.initiatorId(),t.recipientId(),t.scopeId(),t.expiresAt(),
      t.confirmedAt()!=null?"COMPLETED":t.pending(clock.instant())?"PENDING":"EXPIRED"); }
}
