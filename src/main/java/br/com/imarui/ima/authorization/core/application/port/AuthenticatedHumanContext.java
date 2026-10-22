package br.com.imarui.ima.authorization.core.application.port;

import java.util.UUID;

public interface AuthenticatedHumanContext {
  record Human(UUID identityId,UUID sessionId) {}
  Human verify(String accessToken,UUID expectedIdentity);
}
