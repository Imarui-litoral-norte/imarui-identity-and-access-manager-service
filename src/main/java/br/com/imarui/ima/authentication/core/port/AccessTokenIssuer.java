package br.com.imarui.ima.authentication.core.port;

import java.time.Instant;
import java.util.UUID;

public interface AccessTokenIssuer {
  String issue(UUID identityId, UUID sid, Instant now);
}
