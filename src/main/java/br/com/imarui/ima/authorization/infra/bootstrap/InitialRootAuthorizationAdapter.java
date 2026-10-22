package br.com.imarui.ima.authorization.infra.bootstrap;
import br.com.imarui.ima.authorization.api.module.InitialRootAuthorization;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.repository.AuthorizationMutationRepository;
import java.time.Clock;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;

@Component
@RequiredArgsConstructor
@Transactional(propagation=Propagation.MANDATORY)
public class InitialRootAuthorizationAdapter implements InitialRootAuthorization {
  private final JdbcTemplate jdbc;
  private final AuthorizationMutationRepository mutations;
  private final Clock clock;
  public void assign(UUID identity) {
    mutations.lock();
    if (!Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from authentication_bootstrap where name='initial-root' and identity_id=?) and not exists(select 1 from authorization_identity_roles a join authorization_roles r on r.id=a.role_id where r.name='ROOT')",Boolean.class,identity)))
      throw new SecurityException("One-shot ROOT bootstrap proof required.");
    long role=jdbc.queryForObject("select id from authorization_roles where name='ROOT' and ownership='GLOBAL' and active",Long.class);
    var now=clock.instant(); var assignment=new RoleAssignment(UUID.randomUUID(),identity,role,AuthorizationScope.PLATFORM_ID,true,now,now,0);
    mutations.saveAssignment(assignment);
    for (var code:jdbc.queryForList("select p.code from authorization_role_permissions rp join authorization_permissions p on p.id=rp.permission_id where rp.role_id=? and p.service_code='iiam' and not p.deprecated",String.class,role))
      mutations.saveDelegation(new PermissionDelegation(UUID.randomUUID(),identity,code,AuthorizationScope.PLATFORM_ID,true,true,
          "Protected initial ROOT governance",now,now,0));
    mutations.audit(null,"ROOT_BOOTSTRAPPED","ROLE_ASSIGNMENT",assignment.id().toString(),AuthorizationScope.PLATFORM_ID,
        new PolicyChangeReason("Protected initial ROOT governance",null),null,assignment,now);
  }
}
