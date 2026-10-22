package br.com.imarui.ima.authorization.core.application.usecase;

import br.com.imarui.ima.authentication.api.module.TechnicalServiceAuthentication;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.application.service.AuthorizationMutationGuard;
import br.com.imarui.ima.authorization.core.domain.model.AuthorizationScope;
import br.com.imarui.ima.authorization.core.repository.*;
import java.time.*;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class TechnicalClientAdministrationUseCases {
  private final AuthorizationMutationGuard guard;
  private final AuthorizationMutationRepository mutations;
  private final TechnicalServiceAuthentication authentication;
  private final ServiceResourceRepository resources;
  private final Clock clock;

  public TechnicalServiceAuthentication.Client enroll(String service,Set<TechnicalServiceAuthentication.Operation> operations,
      Set<UUID> roots,String publicKey,Instant expires,PolicyChangeReason reason) {
    var context=guard.begin("AUTHENTICATION_SERVICE_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    for(var scope:roots) guard.require(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",scope);
    var client=authentication.enroll(service,operations,roots,publicKey,expires);
    mutations.audit(context.actor(),"TECHNICAL_CLIENT_ENROLLED","TECHNICAL_CLIENT",service,AuthorizationScope.PLATFORM_ID,reason,null,client,clock.instant());
    return client;
  }
  public TechnicalServiceAuthentication.Client configure(String service,boolean enabled,Set<TechnicalServiceAuthentication.Operation> operations,
      Set<UUID> roots,long version,PolicyChangeReason reason) {
    var context=guard.begin("AUTHENTICATION_SERVICE_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    var previous=authentication.client(service);
    for(var scope:roots) guard.require(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",scope);
    var client=authentication.configure(service,enabled,operations,roots,version);
    mutations.audit(context.actor(),"TECHNICAL_CLIENT_REVISED","TECHNICAL_CLIENT",service,AuthorizationScope.PLATFORM_ID,reason,previous,client,clock.instant());
    return client;
  }
  public TechnicalServiceAuthentication.Key rotate(String service,String key,Instant expires,PolicyChangeReason reason) {
    var context=guard.begin("AUTHENTICATION_SERVICE_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    var result=authentication.rotate(service,key,expires);
    mutations.audit(context.actor(),"TECHNICAL_KEY_ADDED","TECHNICAL_KEY",result.id().toString(),AuthorizationScope.PLATFORM_ID,reason,null,result,clock.instant());
    return result;
  }
  public void revoke(String service,UUID key,PolicyChangeReason reason) {
    var context=guard.begin("AUTHENTICATION_SERVICE_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    authentication.revokeKey(service,key);
    mutations.audit(context.actor(),"TECHNICAL_KEY_REVOKED","TECHNICAL_KEY",key.toString(),AuthorizationScope.PLATFORM_ID,reason,null,Map.of("service",service,"key",key),clock.instant());
  }
  public void bind(String service,String type,String resource,UUID scope,PolicyChangeReason reason) {
    var context=guard.begin("AUTHENTICATION_SERVICE_MANAGE",AuthorizationScope.PLATFORM_ID,reason);
    guard.require(context.before(),context.actor(),"AUTHORIZATION_SCOPE_MANAGE",scope);
    var client=authentication.client(service);
    var tree=new br.com.imarui.ima.authorization.core.domain.policy.ScopeHierarchy(context.before().scopes());
    if(client.scopeRoots().stream().noneMatch(root -> tree.covers(root,scope))) throw new SecurityException("Resource is outside the technical jurisdiction.");
    resources.bind(service,type,resource,scope);
    mutations.audit(context.actor(),"SERVICE_RESOURCE_BOUND","SERVICE_RESOURCE",resource,scope,reason,null,Map.of("service",service,"type",type,"resource",resource,"scope",scope),clock.instant());
  }
}
