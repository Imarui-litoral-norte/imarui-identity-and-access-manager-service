package br.com.imarui.ima.authorization.core.repository;

import br.com.imarui.ima.authorization.core.domain.model.*;
import java.util.*;

public interface AuthorizationCatalog {
  List<Permission> permissions();

  Optional<Role> role(long id);

  List<RolePermission> rolePermissions(long roleId);

  List<RoleAssignment> assignments(UUID id);
}
