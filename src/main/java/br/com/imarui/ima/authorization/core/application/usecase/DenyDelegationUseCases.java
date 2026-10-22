package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.AuthorizationPolicyState;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class DenyDelegationUseCases {
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final AuthorizationCatalog catalog;
  private final Clock clock;

  public DenyRule deny(String permission,UUID scope,UUID identity,Long role,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_DENY_MANAGE",scope,reason);
    known(context.before(),permission);
    if (role!=null) {
      var definition=context.before().roles().get(role);
      var tree=new br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy(context.before().scopes());
      if (definition==null || definition.ownership()!=Role.Ownership.GLOBAL
          && !tree.covers(scope,definition.ownerScopeId()) && !definition.availableAt(scope,tree,context.before().tenantScopes()))
        throw new SecurityException("Role is unavailable at this Scope.");
    }
    var now=clock.instant();
    var deny=new DenyRule(UUID.randomUUID(),permission,scope,identity,role,true,reason.reason(),now,now,0);
    mutations.saveDeny(deny);
    guard.assertRecovery(policies.current());
    mutations.audit(context.actor(),"DENY_CREATED","DENY",deny.id().toString(),scope,reason,null,deny,now);
    return deny;
  }

  public void revokeDeny(UUID id,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_DENY_MANAGE",s -> deny(s,id).scopeId(),reason);
    var previous=deny(context.before(),id);
    if (!previous.active()) return;
    var now=clock.instant();
    var changed=new DenyRule(previous.id(),previous.permission(),previous.scopeId(),previous.identityId(),previous.roleId(),
        false,reason.reason(),previous.createdAt(),now,previous.version()+1);
    mutations.saveDeny(changed);
    guard.assertGains(context,policies.current(),guard.descendants(context.before(),previous.scopeId()),Set.of(previous.permission()),Map.of());
    mutations.audit(context.actor(),"DENY_REVOKED","DENY",id.toString(),previous.scopeId(),reason,previous,changed,now);
  }

  public PermissionDelegation delegate(UUID recipient,String permission,UUID scope,boolean redelegable,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_DELEGATION_MANAGE",scope,reason);
    if (context.actor().equals(recipient)) throw new SecurityException("Self-delegation is forbidden.");
    known(context.before(),permission);
    // Passing grant authority to another subject is itself a redelegation, even when the recipient may not pass it on.
    for (var target:guard.descendants(context.before(),scope)) guard.delegate(context.before(),context.actor(),permission,target,true);
    var existing=context.before().delegations().stream().filter(d -> d.active() && d.identityId().equals(recipient)
        && d.permission().equals(permission) && d.scopeId().equals(scope)).findFirst().orElse(null);
    if (existing!=null && existing.redelegable()==redelegable) return existing;
    if (existing!=null && guard.rootIdentity(context.before(),recipient)) throw new SecurityException("ROOT bootstrap delegation is protected.");
    var now=clock.instant();
    var delegation=new PermissionDelegation(existing==null?UUID.randomUUID():existing.id(),recipient,permission,scope,
        redelegable,true,reason.reason(),existing==null?now:existing.createdAt(),now,existing==null?0:existing.version()+1);
    mutations.saveDelegation(delegation);
    mutations.audit(context.actor(),"PERMISSION_DELEGATED","PERMISSION_DELEGATION",delegation.id().toString(),scope,reason,existing,delegation,now);
    return delegation;
  }

  public void revokeDelegation(UUID id,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_DELEGATION_MANAGE",s -> delegation(s,id).scopeId(),reason);
    var previous=delegation(context.before(),id);
    if (guard.rootIdentity(context.before(),previous.identityId())) throw new SecurityException("ROOT bootstrap delegation is protected.");
    if (!previous.active()) return;
    var now=clock.instant();
    var changed=new PermissionDelegation(previous.id(),previous.identityId(),previous.permission(),previous.scopeId(),
        previous.redelegable(),false,reason.reason(),previous.createdAt(),now,previous.version()+1);
    mutations.saveDelegation(changed);
    mutations.audit(context.actor(),"PERMISSION_DELEGATION_REVOKED","PERMISSION_DELEGATION",id.toString(),previous.scopeId(),reason,previous,changed,now);
  }

  /** Explicit central first-authority approval; registration alone cannot invoke or perform this operation. */
  public PermissionDelegation bootstrapCatalogAuthority(String permission,UUID rootScope,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_DELEGATION_MANAGE",rootScope,reason);
    guard.require(context.before(),context.actor(),"AUTHORIZATION_CATALOG_MANAGE",AuthorizationScope.PLATFORM_ID);
    if (!guard.rootIdentity(context.before(),context.actor())) throw new SecurityException("Initial catalog authority requires an existing ROOT operator.");
    var tree=new br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy(context.before().scopes());
    if (tree.ancestors(rootScope).size()!=1) throw new IllegalArgumentException("Initial authority must use an administered root boundary.");
    var definition=catalog.permissions().stream().filter(p -> p.getCode().equals(permission)).findFirst()
        .orElseThrow(() -> new NoSuchElementException("Permission not found."));
    if (definition.getServiceCode().equals("iiam") || definition.isDeprecated())
      throw new SecurityException("Internal and deprecated permissions cannot use catalog authority bootstrap.");
    if (context.before().delegations().stream().anyMatch(d -> d.permission().equals(permission) && tree.covers(rootScope,d.scopeId())))
      throw new SecurityException("Initial authority already existed; ordinary redelegation or controlled recovery is required.");
    var decision=new br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator()
        .evaluate(context.before(),context.actor(),permission,rootScope);
    if (decision.reason()==br.com.imarui.ima.authorization.core.domain.policy.EffectiveAuthorizationEvaluator.Reason.EXPLICIT_DENY)
      throw new SecurityException("A DENY cannot be bypassed by initial governance approval.");
    var now=clock.instant();
    var delegation=new PermissionDelegation(UUID.randomUUID(),context.actor(),permission,rootScope,true,true,reason.reason(),now,now,0);
    mutations.saveDelegation(delegation);
    mutations.audit(context.actor(),"CATALOG_AUTHORITY_BOOTSTRAPPED","PERMISSION_DELEGATION",delegation.id().toString(),rootScope,reason,null,delegation,now);
    return delegation;
  }

  private void known(AuthorizationPolicyState state,String code) {
    if (!state.deprecatedPermissions().containsKey(code)) throw new NoSuchElementException("Permission not found.");
  }
  private DenyRule deny(AuthorizationPolicyState state,UUID id) {
    return state.denies().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("DENY not found."));
  }
  private PermissionDelegation delegation(AuthorizationPolicyState state,UUID id) {
    return state.delegations().stream().filter(d -> d.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("Delegation not found."));
  }
}
