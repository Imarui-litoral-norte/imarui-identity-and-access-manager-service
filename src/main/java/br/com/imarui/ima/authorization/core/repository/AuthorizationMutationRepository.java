package br.com.imarui.ima.authorization.core.repository;
import br.com.imarui.ima.authorization.core.domain.model.*;
import br.com.imarui.ima.authorization.core.application.command.PolicyChangeReason;
import java.time.Instant;
import java.util.*;
public interface AuthorizationMutationRepository {
  void lock();
  Role saveRole(Role role);
  void deleteRole(Role role,UUID actor,PolicyChangeReason reason,Instant now);
  void replacePermissions(long roleId,Set<String> codes);
  void saveAssignment(RoleAssignment assignment);
  void saveDeny(DenyRule deny);
  void saveDelegation(PermissionDelegation delegation);
  void audit(UUID actor,String operation,String resourceType,String resourceId,UUID scope,
      PolicyChangeReason reason,Object before,Object after,Instant now);
}
