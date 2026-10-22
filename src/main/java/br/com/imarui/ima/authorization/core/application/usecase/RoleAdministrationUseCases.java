package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
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
@Transactional
public class RoleAdministrationUseCases {
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final AuthorizationPolicyRepository policies;
  private final Clock clock;

  public Role create(String name,Role.Ownership ownership,UUID ownerScope,UUID ownerTenant,
      Set<String> permissions,PolicyChangeReason reason) {
    Objects.requireNonNull(ownership); permissions=Set.copyOf(permissions);
    Role.requireOwnership(ownership,ownerScope,ownerTenant);
    if(permissions.size()>1000) throw new IllegalArgumentException("A Role may contain at most 1000 Permissions.");
    UUID jurisdiction=ownership==Role.Ownership.GLOBAL?AuthorizationScope.PLATFORM_ID:ownerScope;
    var context=guard.begin("AUTHORIZATION_ROLE_MANAGE",jurisdiction,reason);
    if (reservedRootName(name)) throw new SecurityException("ROOT is provisioned only by the protected bootstrap.");
    if (name!=null) name=name.strip();
    if (ownership==Role.Ownership.TENANT && !Objects.equals(context.before().tenantScopes().get(ownerTenant),ownerScope))
      throw new SecurityException("Role owner is not the Tenant's bound Scope.");
    for (var code:permissions) guard.delegate(context.before(),context.actor(),code,jurisdiction,false,context.hierarchy());
    var now=clock.instant();
    var role=mutations.saveRole(new Role(null,name,ownership,ownerScope,ownerTenant,true,now,now,0));
    mutations.replacePermissions(role.id(),permissions);
    mutations.audit(context.actor(),"ROLE_CREATED","ROLE",role.id().toString(),jurisdiction,reason,null,
        Map.of("role",role,"permissions",permissions),now);
    return role;
  }

  public Role revise(long roleId,String name,boolean active,Set<String> permissions,PolicyChangeReason reason) {
    return revise(roleId,name,active?Role.Status.ACTIVE:Role.Status.INACTIVE,permissions,reason);
  }

  public Role revise(long roleId,String name,Role.Status status,Set<String> permissions,PolicyChangeReason reason) {
    Objects.requireNonNull(status);
    permissions=Set.copyOf(permissions);
    if(permissions.size()>1000) throw new IllegalArgumentException("A Role may contain at most 1000 Permissions.");
    var context=guard.begin("AUTHORIZATION_ROLE_MANAGE",s -> jurisdiction(role(s,roleId)),reason);
    var previous=role(context.before(),roleId);
    if (previous.name().equals("ROOT") || reservedRootName(name))
      throw new SecurityException("ROOT composition and identity are protected.");
    if (previous.status()==Role.Status.ARCHIVED)
      throw new IllegalStateException("Archived Roles cannot be revised or reactivated.");
    if (status==Role.Status.ARCHIVED && context.before().assignments().stream().anyMatch(a -> a.roleId()==roleId && a.active()))
      throw new IllegalStateException("Revoke active RoleAssignments before archiving.");
    if (name!=null) name=name.strip();
    var oldPermissions=context.before().rolePermissions().getOrDefault(roleId,Set.of());
    var additions=new HashSet<>(permissions); additions.removeAll(oldPermissions);
    if (!additions.isEmpty() && context.before().assignments().stream().anyMatch(a ->
        a.active() && a.roleId()==roleId && a.identityId().equals(context.actor())))
      throw new SecurityException("Adding permissions to a Role assigned to the actor is a self-grant.");
    var impacted=impact(context.before(),roleId);
    var grants=new HashSet<>(additions);
    if (status==Role.Status.ACTIVE && !previous.active()) grants.addAll(permissions);
    for (var code:grants) {
      guard.delegate(context.before(),context.actor(),code,jurisdiction(previous),false,context.hierarchy());
      for (var scope:impacted) guard.delegate(context.before(),context.actor(),code,scope,false,context.hierarchy());
    }
    var now=clock.instant();
    var changed=new Role(previous.id(),name,previous.ownership(),previous.ownerScopeId(),previous.ownerTenantId(),
        status,previous.createdAt(),now,previous.version()+1);
    mutations.saveRole(changed); mutations.replacePermissions(roleId,permissions);
    var touched=new HashSet<>(oldPermissions); touched.addAll(permissions);
    context.before().denies().stream().filter(d -> Objects.equals(d.roleId(),roleId)).forEach(d -> touched.add(d.permission()));
    guard.assertGains(context,policies.current(),impacted,touched,Map.of());
    mutations.audit(context.actor(),"ROLE_REVISED","ROLE",Long.toString(roleId),jurisdiction(previous),reason,
        Map.of("role",previous,"permissions",oldPermissions),Map.of("role",changed,"permissions",permissions),now);
    return changed;
  }

  public void delete(long roleId,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_ROLE_MANAGE",s -> jurisdiction(role(s,roleId)),reason);
    var previous=role(context.before(),roleId);
    if (previous.name().equals("ROOT")) throw new SecurityException("ROOT cannot be deleted.");
    if (previous.status()!=Role.Status.ARCHIVED || context.before().assignments().stream().anyMatch(a -> a.roleId()==roleId))
      throw new IllegalStateException("Only archived Roles without operational assignments may be deleted.");
    var now=clock.instant();
    mutations.deleteRole(previous,context.actor(),reason,now);
    guard.assertRecovery(policies.current());
    mutations.audit(context.actor(),"ROLE_DELETED","ROLE",Long.toString(roleId),jurisdiction(previous),reason,Map.of("role",previous,"permissions",context.before().rolePermissions().getOrDefault(roleId,Set.of()),"denies",context.before().denies().stream().filter(d -> Objects.equals(d.roleId(),roleId)).toList()),null,now);
  }

  public RoleAssignment assign(UUID identity,long roleId,UUID scope,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_ASSIGNMENT_MANAGE",scope,reason);
    if (context.actor().equals(identity)) throw new SecurityException("Self-assignment is forbidden.");
    var role=role(context.before(),roleId);
    if (role.name().equals("ROOT")) throw new SecurityException("New ROOT identities cannot be created by Role administration.");
    var hierarchy=new ScopeHierarchy(context.before().scopes());
    if (!role.active() || !role.availableAt(scope,hierarchy,context.before().tenantScopes()))
      throw new SecurityException("Role is unavailable at this resource Scope.");
    var impacted=guard.descendants(context.before(),scope);
    impacted.removeIf(target -> !role.availableAt(target,hierarchy,context.before().tenantScopes()));
    for (var permission:context.before().rolePermissions().getOrDefault(roleId,Set.of()))
      for (var target:impacted) guard.delegate(context.before(),context.actor(),permission,target,false,context.hierarchy());
    var prior=context.before().assignments().stream().filter(a -> a.identityId().equals(identity)
        && a.roleId()==roleId && a.scopeId().equals(scope)).findFirst().orElse(null);
    if (prior!=null && prior.active()) return prior;
    var now=clock.instant();
    var assignment=new RoleAssignment(prior==null?UUID.randomUUID():prior.id(),identity,roleId,scope,true,
        prior==null?now:prior.createdAt(),now,prior==null?0:prior.version()+1);
    mutations.saveAssignment(assignment);
    guard.assertGains(context,policies.current(),impacted,touched(context.before(),roleId),Map.of());
    mutations.audit(context.actor(),"ROLE_ASSIGNED","ROLE_ASSIGNMENT",assignment.id().toString(),scope,reason,prior,assignment,now);
    return assignment;
  }

  public void revokeAssignment(UUID assignmentId,PolicyChangeReason reason) {
    var context=guard.begin("AUTHORIZATION_ASSIGNMENT_MANAGE",s -> assignment(s,assignmentId).scopeId(),reason);
    var previous=assignment(context.before(),assignmentId);
    if (context.actor().equals(previous.identityId())) throw new SecurityException("An actor cannot change their own RoleAssignments.");
    if (role(context.before(),previous.roleId()).name().equals("ROOT")) throw new SecurityException("ROOT assignments are protected.");
    if (!previous.active()) return;
    var now=clock.instant();
    var changed=new RoleAssignment(previous.id(),previous.identityId(),previous.roleId(),previous.scopeId(),false,
        previous.createdAt(),now,previous.version()+1);
    mutations.saveAssignment(changed);
    guard.assertGains(context,policies.current(),guard.descendants(context.before(),previous.scopeId()),
        touched(context.before(),previous.roleId()),Map.of());
    mutations.audit(context.actor(),"ROLE_ASSIGNMENT_REVOKED","ROLE_ASSIGNMENT",assignmentId.toString(),previous.scopeId(),reason,previous,changed,now);
  }

  private Set<String> touched(AuthorizationPolicyState state,long role) {
    var result=new HashSet<>(state.rolePermissions().getOrDefault(role,Set.of()));
    state.denies().stream().filter(d -> Objects.equals(d.roleId(),role)).forEach(d -> result.add(d.permission()));
    return result;
  }
  private Set<UUID> impact(AuthorizationPolicyState state,long role) {
    var result=new HashSet<UUID>(); var hierarchy=new ScopeHierarchy(state.scopes()); var definition=role(state,role);
    state.assignments().stream().filter(a -> a.active() && a.roleId()==role).forEach(a ->
        guard.descendants(state,a.scopeId()).stream().filter(s -> definition.availableAt(s,hierarchy,state.tenantScopes())).forEach(result::add));
    return result;
  }
  private Role role(AuthorizationPolicyState state,long id) {
    var role=state.roles().get(id); if (role==null) throw new NoSuchElementException("Role not found."); return role;
  }
  private UUID jurisdiction(Role role) { return role.ownership()==Role.Ownership.GLOBAL?AuthorizationScope.PLATFORM_ID:role.ownerScopeId(); }
  private boolean reservedRootName(String name) { return name!=null && name.strip().equalsIgnoreCase("ROOT"); }
  private RoleAssignment assignment(AuthorizationPolicyState state,UUID id) {
    return state.assignments().stream().filter(a -> a.id().equals(id)).findFirst().orElseThrow(() -> new NoSuchElementException("RoleAssignment not found."));
  }
}
