package br.com.imarui.ima.authorization.api.module;

import java.util.*;

public interface IdentityAuthorities {
  List<String> find(UUID identityId);
}
