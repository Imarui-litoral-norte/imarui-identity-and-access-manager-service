package br.com.imarui.ima.identity.api.module;

import br.com.imarui.ima.identity.core.domain.enums.identity.IdentityKind;
import java.util.*;

public interface InitialIdentityProvisioning {
  UUID create(IdentityKind kind, String nickname, String email, Map<String, String> details);
}
