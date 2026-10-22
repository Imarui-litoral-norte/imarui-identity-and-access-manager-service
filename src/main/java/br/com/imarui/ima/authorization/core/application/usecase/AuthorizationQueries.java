package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authorization.api.module.SecurityAuditEvents;
import br.com.imarui.ima.authorization.core.application.port.AuthorizationActor;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.domain.policy.*;
import br.com.imarui.ima.authorization.core.repository.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@RequiredArgsConstructor
@Transactional(propagation=Propagation.REQUIRES_NEW,readOnly=true,isolation=Isolation.REPEATABLE_READ,timeout=5)
public class AuthorizationQueries {
  public record Page<T>(List<T> items,long total,int page,int size) {}
  public record RoleView(Long id,String name,Role.Ownership ownership,UUID ownerScopeId,UUID ownerTenantId,
      Role.Status status,long version,boolean available,boolean manageable) {}
  public record PermissionView(Long id,String code,String description,String module,String owner,String namespace,boolean deprecated,long version) {}
  public record EffectivePermission(String code,boolean allowed,String reason) {}
  private final AuthorizationPolicyRepository policies;
  private final AuthorizationActor actors;
  private final AuthorizationMutationGuard guard;
  private final AuthorizationCatalog catalog;
  private final ScopeRepository scopes;
  private final SecurityAuditEvents events;
  private final EffectiveAuthorizationEvaluator evaluator=new EffectiveAuthorizationEvaluator();

  public Page<AuthorizationScope> scopes(UUID scope,int page,int size) {
    var state=state(scope); var tree=new ScopeHierarchy(state.scopes()); var actor=actors.requireIdentityId();
    return page(tree.descendants(scope).stream().filter(s -> evaluator.evaluate(state,actor,"AUTHORIZATION_AUDIT_READ",s.id(),tree).allowed())
        .sorted(Comparator.comparing(AuthorizationScope::code)).toList(),page,size);
  }
  public AuthorizationScope scope(UUID id) {
    var state=state(id); return state.scopes().stream().filter(s -> s.id().equals(id)).findFirst().orElseThrow();
  }
  public Page<AuthorizationScope> ancestors(UUID scope,int page,int size) {
    var state=state(scope); return page(new ScopeHierarchy(state.scopes()).ancestors(scope),page,size);
  }
  public Page<RoleView> roles(UUID scope,Role.Ownership ownership,Role.Status status,boolean available,int page,int size) {
    var state=state(scope); var tree=new ScopeHierarchy(state.scopes()); var actor=actors.requireIdentityId();
    var list=state.roles().values().stream().filter(r -> ownership==null || r.ownership()==ownership)
        .filter(r -> status==null || r.status()==status)
        .filter(r -> available ? r.active() && !r.name().equals("ROOT") && tree.active(scope) && r.availableAt(scope,tree,state.tenantScopes())
            : r.ownership()==Role.Ownership.GLOBAL ? scope.equals(AuthorizationScope.PLATFORM_ID) : scope.equals(r.ownerScopeId()))
        .sorted(Comparator.comparing(Role::name).thenComparing(Role::id))
        .map(r -> view(r,state,actor,scope,tree)).toList();
    return page(list,page,size);
  }
  public Page<RoleView> tenantRoles(UUID tenant,int page,int size) {
    return roles(scopes.tenantScope(tenant).orElseThrow(() -> new NoSuchElementException("Tenant not found.")),null,null,true,page,size);
  }
  public RoleView role(long id) {
    var state=policies.current(); var r=role(state,id); var owner=jurisdiction(r); read(state,owner);
    return view(r,state,actors.requireIdentityId(),owner,new ScopeHierarchy(state.scopes()));
  }
  public Set<String> rolePermissions(long id) {
    var state=policies.current(); read(state,jurisdiction(role(state,id))); return state.rolePermissions().getOrDefault(id,Set.of());
  }
  public Page<RoleAssignment> assignments(UUID scope,UUID identity,boolean inherited,int page,int size) {
    var state=state(scope); var tree=new ScopeHierarchy(state.scopes());
    return page(state.assignments().stream().filter(a -> identity==null || identity.equals(a.identityId()))
        .filter(a -> inherited?tree.covers(a.scopeId(),scope):a.scopeId().equals(scope))
        .sorted(Comparator.comparing(a -> a.id().toString())).toList(),page,size);
  }
  public Page<DenyRule> denies(UUID scope,Boolean active,int page,int size) {
    var state=state(scope);
    return page(state.denies().stream().filter(d -> d.scopeId().equals(scope) && (active==null || d.active()==active))
        .sorted(Comparator.comparing(d -> d.id().toString())).toList(),page,size);
  }
  public Page<PermissionDelegation> delegations(UUID scope,UUID identity,int page,int size) {
    var state=state(scope);
    return page(state.delegations().stream().filter(d -> d.scopeId().equals(scope) && (identity==null || identity.equals(d.identityId())))
        .sorted(Comparator.comparing(d -> d.id().toString())).toList(),page,size);
  }
  public Page<PermissionView> permissions(UUID scope,String owner,String prefix,int page,int size) {
    state(scope);
    return page(catalog.permissions().stream().filter(p -> owner==null || owner.equals(p.getServiceCode()))
        .filter(p -> prefix==null || p.getCode().startsWith(prefix))
        .map(p -> new PermissionView(p.getId(),p.getCode(),p.getDescription(),p.getModule(),p.getServiceCode(),p.getNamespace(),p.isDeprecated(),p.getCatalogVersion())).toList(),page,size);
  }
  public Page<EffectivePermission> effective(UUID identity,UUID scope,String prefix,int page,int size) {
    var state=state(scope); var tree=new ScopeHierarchy(state.scopes());
    // Page codes before evaluation: at most 100 decisions, all from one current snapshot.
    var codes=state.deprecatedPermissions().keySet().stream().filter(c -> prefix==null || c.startsWith(prefix)).sorted().toList();
    var selected=page(codes,page,size);
    var values=selected.items().stream().map(code -> {
      var result=evaluator.evaluate(state,identity,code,scope,tree);
      return new EffectivePermission(code,result.allowed(),result.reason().name());
    }).toList();
    auditRead(identity,scope); return new Page<>(values,selected.total(),page,size);
  }
  public EffectiveAuthorizationEvaluator.Explanation explain(UUID identity,String permission,UUID scope) {
    var state=state(scope); var explanation=evaluator.explain(state,identity,permission,scope);
    auditRead(identity,scope); return explanation;
  }
  private AuthorizationPolicyState state(UUID scope) { var state=policies.current(); read(state,scope); return state; }
  private void read(AuthorizationPolicyState state,UUID scope) {
    if(state.scopes().stream().noneMatch(s -> s.id().equals(scope))) throw new NoSuchElementException("Scope not found.");
    guard.require(state,actors.requireIdentityId(),"AUTHORIZATION_AUDIT_READ",scope);
  }
  private void auditRead(UUID identity,UUID scope) {
    events.record("EFFECTIVE_POLICY_READ",actors.requireIdentityId(),"iiam",identity.toString(),scope,"AUTHORIZED_AUDIT_READ",UUID.randomUUID());
  }
  private RoleView view(Role role,AuthorizationPolicyState state,UUID actor,UUID scope,ScopeHierarchy tree) {
    return new RoleView(role.id(),role.name(),role.ownership(),role.ownerScopeId(),role.ownerTenantId(),role.status(),role.version(),
        role.active() && !role.name().equals("ROOT") && tree.active(scope) && role.availableAt(scope,tree,state.tenantScopes()),
        !role.name().equals("ROOT") && evaluator.evaluate(state,actor,"AUTHORIZATION_ROLE_MANAGE",jurisdiction(role),tree).allowed());
  }
  private UUID jurisdiction(Role role) { return role.ownership()==Role.Ownership.GLOBAL?AuthorizationScope.PLATFORM_ID:role.ownerScopeId(); }
  private Role role(AuthorizationPolicyState state,long id) { var r=state.roles().get(id); if(r==null) throw new NoSuchElementException("Role not found."); return r; }
  public static <T> Page<T> page(List<T> list,int page,int size) {
    if(page<0 || page>1000000 || size<1 || size>100) throw new IllegalArgumentException("Page must be >=0 and size 1–100.");
    int start=(int)Math.min((long)list.size(),(long)page*size);
    return new Page<>(List.copyOf(list.subList(start,Math.min(list.size(),start+size))),list.size(),page,size);
  }
}
